package org.blindsystems.cyclope.navigation.positioning

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import org.blindsystems.cyclope.navigation.core.Geo
import org.blindsystems.cyclope.navigation.core.GeoPoint
import org.blindsystems.cyclope.navigation.core.PdrFusion
import java.io.BufferedWriter
import java.io.File
import java.util.Locale

/**
 * Positioning study: records the 5 position sources of the phone at the same time, in one CSV,
 * so they can be compared on the same walk (see docs/01-positioning-study.md).
 *
 *  1. GNSS        LocationManager.GPS_PROVIDER      satellites only, filtered by the GNSS chip
 *  2. NETWORK     LocationManager.NETWORK_PROVIDER  Wi-Fi / cell towers
 *  3. FUSED       LocationManager.FUSED_PROVIDER    Android's own fusion (Android 12+, open source AOSP)
 *  4. GNSS_STATUS GnssStatus callback               raw quality data: satellites used / visible, C/N0
 *  5. PDR         our PdrFusion                     GNSS + step detector + compass (rotation vector)
 *
 * All callbacks run on the main thread. The caller must hold ACCESS_FINE_LOCATION
 * (and ACTIVITY_RECOGNITION on Android 10+ for the step detector).
 */
class PositionRecorder(context: Context) {

    enum class Source { GNSS, NETWORK, FUSED, GNSS_STATUS, PDR, MARK }

    data class Sample(
        val source: Source,
        val timeMs: Long,
        val lat: Double? = null,
        val lon: Double? = null,
        val accuracyM: Double? = null,
        val altitudeM: Double? = null,
        val speedMps: Double? = null,
        val bearingDeg: Double? = null,
        val satsUsed: Int? = null,
        val satsVisible: Int? = null,
        val meanCn0DbHz: Double? = null,
        val headingDeg: Double? = null,
        val steps: Int? = null,
        val note: String? = null,
    ) {
        val point: GeoPoint? get() = if (lat != null && lon != null) GeoPoint(lat, lon) else null
    }

    fun interface Listener {
        fun onSample(sample: Sample)
    }

    companion object {
        const val CSV_HEADER = "time_ms,source,lat,lon,accuracy_m,altitude_m,speed_mps,bearing_deg," +
            "sats_used,sats_visible,mean_cn0_dbhz,heading_deg,steps,note"
        private const val UPDATE_INTERVAL_MS = 1000L
    }

    private val appContext = context.applicationContext
    private val lm = appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val sm = appContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private val listeners = mutableListOf<Listener>()

    /** Last sample of each source (for the UI). */
    val latest = HashMap<Source, Sample>()

    private var running = false
    private var csv: BufferedWriter? = null
    private var linesSinceFlush = 0

    /** Current (or last) CSV file. */
    var csvFile: File? = null
        private set
    val isLogging: Boolean get() = csv != null

    // Source 5: our own fusion
    private val pdr = PdrFusion()
    private var headingTrueDeg: Double? = null
    private var declinationDeg = 0.0
    private var totalSteps = 0
    private val rotationMatrix = FloatArray(9)
    private val orientation = FloatArray(3)

    val availableProviders: List<String> get() = lm.allProviders

    fun addListener(l: Listener) {
        listeners += l
    }

    // ---------------------------------------------------------------- Android callbacks

    private inner class ProviderListener(private val source: Source) : LocationListener {
        override fun onLocationChanged(location: Location) = onLocation(source, location)
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}

        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    }

    private val gnssListener = ProviderListener(Source.GNSS)
    private val networkListener = ProviderListener(Source.NETWORK)
    private val fusedListener = ProviderListener(Source.FUSED)

    private val gnssStatusCallback = object : GnssStatus.Callback() {
        override fun onSatelliteStatusChanged(status: GnssStatus) {
            var used = 0
            var cn0Sum = 0.0
            for (i in 0 until status.satelliteCount) {
                if (status.usedInFix(i)) {
                    used++
                    cn0Sum += status.getCn0DbHz(i)
                }
            }
            emit(
                Sample(
                    Source.GNSS_STATUS, System.currentTimeMillis(),
                    satsUsed = used, satsVisible = status.satelliteCount,
                    meanCn0DbHz = if (used > 0) cn0Sum / used else null,
                )
            )
        }
    }

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            when (event.sensor.type) {
                Sensor.TYPE_ROTATION_VECTOR -> {
                    SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                    SensorManager.getOrientation(rotationMatrix, orientation)
                    val magneticDeg = Math.toDegrees(orientation[0].toDouble())
                    // Compass gives magnetic north; maps use true north -> add the declination.
                    headingTrueDeg = Geo.normalize360(magneticDeg + declinationDeg)
                }
                Sensor.TYPE_STEP_DETECTOR -> {
                    totalSteps++
                    val heading = headingTrueDeg ?: return
                    pdr.onStep(heading)
                    emitPdr()
                }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    // ---------------------------------------------------------------- start / stop

    @SuppressLint("MissingPermission")
    fun start() {
        if (running) return
        running = true
        val providers = lm.allProviders
        val looper = Looper.getMainLooper()
        if (LocationManager.GPS_PROVIDER in providers) {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, UPDATE_INTERVAL_MS, 0f, gnssListener, looper)
        }
        if (LocationManager.NETWORK_PROVIDER in providers) {
            lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, UPDATE_INTERVAL_MS, 0f, networkListener, looper)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && LocationManager.FUSED_PROVIDER in providers) {
            lm.requestLocationUpdates(LocationManager.FUSED_PROVIDER, UPDATE_INTERVAL_MS, 0f, fusedListener, looper)
        }
        lm.registerGnssStatusCallback(gnssStatusCallback, mainHandler)
        sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)?.let {
            sm.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_UI)
        }
        sm.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)?.let {
            sm.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_FASTEST)
        }
    }

    fun stop() {
        if (!running) return
        running = false
        lm.removeUpdates(gnssListener)
        lm.removeUpdates(networkListener)
        lm.removeUpdates(fusedListener)
        lm.unregisterGnssStatusCallback(gnssStatusCallback)
        sm.unregisterListener(sensorListener)
        stopCsv()
    }

    // ---------------------------------------------------------------- CSV

    fun startCsv(file: File) {
        stopCsv()
        file.parentFile?.mkdirs()
        csv = file.bufferedWriter().also {
            it.write(CSV_HEADER)
            it.newLine()
        }
        csvFile = file
    }

    fun stopCsv() {
        csv?.let {
            it.flush()
            it.close()
        }
        csv = null
    }

    fun flush() {
        csv?.flush()
    }

    /** Writes a MARK line (e.g. "M1") at the last GNSS position: used to note known reference points. */
    fun mark(label: String) {
        val ref = latest[Source.GNSS] ?: latest[Source.FUSED]
        emit(Sample(Source.MARK, System.currentTimeMillis(), ref?.lat, ref?.lon, ref?.accuracyM, note = label))
    }

    // ---------------------------------------------------------------- internals

    private fun onLocation(source: Source, loc: Location) {
        val acc = if (loc.hasAccuracy()) loc.accuracy.toDouble() else null
        if (source == Source.GNSS) {
            declinationDeg = GeomagneticField(
                loc.latitude.toFloat(), loc.longitude.toFloat(), loc.altitude.toFloat(), loc.time
            ).declination.toDouble()
        }
        emit(
            Sample(
                source, System.currentTimeMillis(), loc.latitude, loc.longitude,
                accuracyM = acc,
                altitudeM = if (loc.hasAltitude()) loc.altitude else null,
                speedMps = if (loc.hasSpeed()) loc.speed.toDouble() else null,
                bearingDeg = if (loc.hasBearing()) loc.bearing.toDouble() else null,
            )
        )
        if (source == Source.GNSS && acc != null) {
            pdr.onGnssFix(GeoPoint(loc.latitude, loc.longitude), acc)
            emitPdr()
        }
    }

    private fun emitPdr() {
        val est = pdr.estimate ?: return
        emit(
            Sample(
                Source.PDR, System.currentTimeMillis(), est.lat, est.lon,
                accuracyM = pdr.sigmaM, headingDeg = headingTrueDeg, steps = totalSteps,
            )
        )
    }

    private fun emit(s: Sample) {
        latest[s.source] = s
        writeCsv(s)
        listeners.forEach { it.onSample(s) }
    }

    private fun writeCsv(s: Sample) {
        val w = csv ?: return
        fun f(v: Double?, digits: Int) = if (v == null || v.isNaN()) "" else String.format(Locale.US, "%.${digits}f", v)
        val line = listOf(
            s.timeMs.toString(), s.source.name, f(s.lat, 7), f(s.lon, 7), f(s.accuracyM, 1), f(s.altitudeM, 1),
            f(s.speedMps, 2), f(s.bearingDeg, 1), s.satsUsed?.toString().orEmpty(), s.satsVisible?.toString().orEmpty(),
            f(s.meanCn0DbHz, 1), f(s.headingDeg, 1), s.steps?.toString().orEmpty(), s.note.orEmpty().replace(",", ";"),
        ).joinToString(",")
        w.write(line)
        w.newLine()
        if (++linesSinceFlush >= 20) {
            w.flush()
            linesSinceFlush = 0
        }
    }
}
