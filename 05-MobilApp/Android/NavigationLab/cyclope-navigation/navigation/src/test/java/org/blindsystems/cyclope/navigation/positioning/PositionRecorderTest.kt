package org.blindsystems.cyclope.navigation.positioning

import android.Manifest
import android.app.Application
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import android.location.GnssStatus
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import org.blindsystems.cyclope.navigation.core.Geo
import org.blindsystems.cyclope.navigation.positioning.PositionRecorder.Source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.shadows.GnssStatusBuilder
import org.robolectric.shadows.SensorEventBuilder
import org.robolectric.shadows.ShadowSensor

/** Framework shadows only: no emulator, physical sensor, GPS fix or Android resources. */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
// No TLS in these service tests; avoid Conscrypt JNI, unavailable on Linux ARM64.
@ConscryptMode(ConscryptMode.Mode.OFF)
class PositionRecorderTest {
    @get:Rule val files = TemporaryFolder()
    private lateinit var app: Application
    private lateinit var lm: LocationManager
    private lateinit var sm: SensorManager
    private lateinit var recorder: PositionRecorder

    @Before
    fun setUp() {
        app = RuntimeEnvironment.getApplication()
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        lm = app.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        sm = app.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        shadowOf(lm).setLocationEnabled(true)
        shadowOf(lm).setProviderEnabled(LocationManager.GPS_PROVIDER, true)
        shadowOf(lm).setProviderEnabled(LocationManager.NETWORK_PROVIDER, true)
        recorder = PositionRecorder(app)
    }

    private fun location(
        provider: String,
        rich: Boolean = true,
    ) = Location(provider).apply {
        latitude = 48.0
        longitude = 2.0
        time = 1_700_000_000_000L
        elapsedRealtimeNanos = 1_000_000_000L
        if (rich) {
            accuracy = 5f
            altitude = 123.0
            speed = 1.25f
            bearing = 90f
        }
    }

    private fun deliver(loc: Location) {
        shadowOf(lm).simulateLocation(loc)
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun startAndStopAreIdempotentAndReleaseAllRegistrationsAndCsv() {
        val rotation = ShadowSensor.newInstance(Sensor.TYPE_ROTATION_VECTOR)
        val step = ShadowSensor.newInstance(Sensor.TYPE_STEP_DETECTOR)
        shadowOf(sm).addSensor(rotation)
        shadowOf(sm).addSensor(step)
        recorder.stop()
        recorder.start()
        recorder.start()
        assertEquals(1, shadowOf(lm).getLocationUpdateListeners(LocationManager.GPS_PROVIDER).size)
        assertEquals(1, shadowOf(sm).listeners.size)
        assertTrue(recorder.availableProviders.contains(LocationManager.GPS_PROVIDER))
        recorder.startCsv(files.newFile("lifecycle.csv"))
        assertTrue(recorder.isLogging)
        recorder.stop()
        recorder.stop()
        assertFalse(recorder.isLogging)
        assertTrue(shadowOf(lm).locationUpdateListeners.isEmpty())
        assertTrue(shadowOf(sm).listeners.isEmpty())
    }

    @Test
    fun missingProvidersAndSensorsDoNotPreventStartAndStop() {
        shadowOf(lm).removeProvider(LocationManager.GPS_PROVIDER)
        shadowOf(lm).removeProvider(LocationManager.NETWORK_PROVIDER)
        recorder.start()
        assertTrue(shadowOf(lm).locationUpdateListeners.isEmpty())
        recorder.stop()
    }

    @Test
    fun richGnssLocationEmitsGnssAndPdrAndNotifiesObservers() {
        val samples = mutableListOf<PositionRecorder.Sample>()
        recorder.addListener { samples += it }
        recorder.start()
        deliver(location(LocationManager.GPS_PROVIDER))
        val sample = recorder.latest.getValue(Source.GNSS)
        assertEquals(48.0, sample.lat!!, 0.0)
        assertEquals(2.0, sample.lon!!, 0.0)
        assertEquals(5.0, sample.accuracyM!!, 0.0)
        assertEquals(123.0, sample.altitudeM!!, 0.0)
        assertEquals(1.25, sample.speedMps!!, 0.0)
        assertEquals(90.0, sample.bearingDeg!!, 0.0)
        assertEquals(listOf(Source.GNSS, Source.PDR), samples.map { it.source })
        assertEquals(sample.point, recorder.latest.getValue(Source.PDR).point)
        assertEquals(0, recorder.latest.getValue(Source.PDR).steps)
        recorder.stop()
    }

    @Test
    fun locationsWithoutOptionalMeasurementsDoNotInventAccuracyOrPdrFixes() {
        recorder.start()
        deliver(location(LocationManager.GPS_PROVIDER, false))
        val gnss = recorder.latest.getValue(Source.GNSS)
        assertNull(gnss.accuracyM)
        assertNull(gnss.altitudeM)
        assertNull(gnss.speedMps)
        assertNull(gnss.bearingDeg)
        assertFalse(recorder.latest.containsKey(Source.PDR))
        deliver(location(LocationManager.NETWORK_PROVIDER, false))
        assertEquals(gnss.point, recorder.latest.getValue(Source.NETWORK).point)
        recorder.stop()
    }

    @Test
    @Config(sdk = [31])
    fun fusedProviderOnAndroid12IsSubscribedAndDelivered() {
        shadowOf(lm).setProviderEnabled(LocationManager.FUSED_PROVIDER, true)
        recorder.start()
        deliver(location(LocationManager.FUSED_PROVIDER))
        assertEquals(48.0, recorder.latest.getValue(Source.FUSED).lat!!, 0.0)
        assertFalse(recorder.latest.containsKey(Source.PDR))
        recorder.stop()
    }

    @Test
    @Config(sdk = [31])
    fun android12WithoutFusedProviderStillStarts() {
        shadowOf(lm).removeProvider(LocationManager.FUSED_PROVIDER)
        recorder.start()
        assertFalse(recorder.availableProviders.contains(LocationManager.FUSED_PROVIDER))
        recorder.stop()
    }

    private fun satellite(
        id: Int,
        used: Boolean,
        cn0: Float,
    ) = GnssStatusBuilder.GnssSatelliteInfo.builder()
        .setConstellation(GnssStatus.CONSTELLATION_GPS).setSvid(id).setCn0DbHz(cn0)
        .setElevation(30f).setAzimuth(0f).setHasEphemeris(true).setHasAlmanac(true)
        .setUsedInFix(used).setCarrierFrequencyHz(null).build()

    @Test
    fun gnssStatusCountsOnlyUsedSatellitesAndHandlesNoFix() {
        recorder.start()
        shadowOf(
            lm,
        ).simulateGnssStatus(GnssStatusBuilder.buildFrom(satellite(1, true, 30f), satellite(2, false, 50f), satellite(3, true, 40f)))
        shadowOf(Looper.getMainLooper()).idle()
        val used = recorder.latest.getValue(Source.GNSS_STATUS)
        assertEquals(2, used.satsUsed)
        assertEquals(3, used.satsVisible)
        assertEquals(35.0, used.meanCn0DbHz!!, 0.0)
        shadowOf(lm).simulateGnssStatus(GnssStatusBuilder.buildFrom(satellite(1, false, 20f)))
        shadowOf(Looper.getMainLooper()).idle()
        assertNull(recorder.latest.getValue(Source.GNSS_STATUS).meanCn0DbHz)
        shadowOf(lm).simulateGnssStatus(GnssStatusBuilder.create().build())
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(0, recorder.latest.getValue(Source.GNSS_STATUS).satsVisible)
        recorder.stop()
    }

    @Test
    fun stepsNeedHeadingAndFixThenMoveThePdrEstimate() {
        val rotation = ShadowSensor.newInstance(Sensor.TYPE_ROTATION_VECTOR)
        val step = ShadowSensor.newInstance(Sensor.TYPE_STEP_DETECTOR)
        shadowOf(sm).addSensor(rotation)
        shadowOf(sm).addSensor(step)
        recorder.start()

        fun event(
            sensor: Sensor,
            vararg values: Float,
        ) = SensorEventBuilder.newBuilder().setSensor(sensor).setValues(values).build()
        shadowOf(sm).sendSensorEventToListeners(event(step, 1f))
        assertFalse(recorder.latest.containsKey(Source.PDR))
        shadowOf(sm).sendSensorEventToListeners(event(rotation, 0f, 0f, 0f, 1f))
        shadowOf(sm).sendSensorEventToListeners(event(step, 1f))
        assertFalse(recorder.latest.containsKey(Source.PDR))
        deliver(location(LocationManager.GPS_PROVIDER))
        val before = recorder.latest.getValue(Source.PDR).point!!
        shadowOf(sm).sendSensorEventToListeners(event(step, 1f))
        val after = recorder.latest.getValue(Source.PDR)
        assertEquals(0.7, Geo.distance(before, after.point!!), 0.01)
        assertEquals(3, after.steps)
        assertNotNull(after.headingDeg)
        // Other sensor types and accuracy-only notifications must not emit fake positions.
        val previous = recorder.latest.toMap()
        shadowOf(sm).sendSensorEventToListeners(event(ShadowSensor.newInstance(Sensor.TYPE_ACCELEROMETER), 0f, 0f, 0f))
        shadowOf(sm).listeners.forEach { it.onAccuracyChanged(null, 0) }
        assertEquals(previous, recorder.latest)
        recorder.stop()
    }

    @Test
    fun csvFlushMarkFallbackAndRestartKeepStableColumnOrder() {
        val first = files.newFile("first.csv")
        recorder.startCsv(first)
        assertSame(first, recorder.csvFile)
        recorder.mark("without,position")
        recorder.latest[Source.FUSED] = PositionRecorder.Sample(Source.FUSED, 1L, 48.0, 2.0, 8.0)
        recorder.mark("fused")
        recorder.latest[Source.GNSS] = PositionRecorder.Sample(Source.GNSS, 2L, 49.0, 3.0, 4.0)
        repeat(20) { recorder.mark("M$it") }
        recorder.flush()
        val lines = first.readLines()
        assertEquals(PositionRecorder.CSV_HEADER, lines.first())
        assertEquals(23, lines.size)
        val mark = lines[1].split(',')
        assertEquals(14, mark.size)
        assertEquals("", mark[2])
        assertEquals("without;position", mark.last())
        assertEquals("48.0000000", lines[2].split(',')[2])
        assertEquals("49.0000000", lines[3].split(',')[2])
        val second = files.newFile("second.csv")
        recorder.startCsv(second)
        recorder.mark("new")
        recorder.stopCsv()
        recorder.stopCsv()
        recorder.flush()
        assertFalse(recorder.isLogging)
        assertEquals(2, second.readLines().size)
        assertEquals(23, first.readLines().size)
    }

    @Test
    fun samplePointRequiresBothCoordinatesAndCsvFormatsAllFieldsAndNan() {
        assertNull(PositionRecorder.Sample(Source.MARK, 1L).point)
        assertNull(PositionRecorder.Sample(Source.MARK, 1L, lat = 1.0).point)
        assertNull(PositionRecorder.Sample(Source.MARK, 1L, lon = 1.0).point)
        val file = files.newFile("fields.csv")
        recorder.startCsv(file)
        // Exercise the CSV serializer with a synthetic complete sample, not production service internals.
        val emit = PositionRecorder::class.java.getDeclaredMethod("emit", PositionRecorder.Sample::class.java).apply { isAccessible = true }
        emit.invoke(
            recorder,
            PositionRecorder.Sample(Source.GNSS_STATUS, 123L, 48.0, 2.0, 4.25, 9.0, 1.25, 90.0, 2, 3, 35.0, 180.0, 4, "a,b"),
        )
        emit.invoke(recorder, PositionRecorder.Sample(Source.MARK, 124L, Double.NaN, Double.NaN))
        recorder.stopCsv()
        val lines = file.readLines()
        assertEquals("123,GNSS_STATUS,48.0000000,2.0000000,4.3,9.0,1.25,90.0,2,3,35.0,180.0,4,a;b", lines[1])
        assertEquals("", lines[2].split(',')[2])
        assertEquals("", lines[2].split(',')[3])
    }
}
