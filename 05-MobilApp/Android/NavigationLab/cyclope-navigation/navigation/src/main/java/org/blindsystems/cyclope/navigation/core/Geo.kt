package org.blindsystems.cyclope.navigation.core

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** A WGS84 position in decimal degrees. */
data class GeoPoint(val lat: Double, val lon: Double)

/**
 * Small geodesy toolbox. Pure Kotlin (no Android dependency) so it can be unit-tested on a PC.
 *
 * For the short distances a pedestrian walks (< a few km) we use:
 *  - haversine for distances,
 *  - a local flat "east/north" projection (equirectangular) for point-to-segment projection.
 * The error of the flat projection is far below GNSS noise at these scales.
 */
object Geo {
    const val EARTH_RADIUS_M = 6_371_008.8

    private fun Double.rad() = this * PI / 180.0
    private fun Double.deg() = this * 180.0 / PI

    /** Great-circle distance in metres (haversine). */
    fun distance(a: GeoPoint, b: GeoPoint): Double {
        val dLat = (b.lat - a.lat).rad()
        val dLon = (b.lon - a.lon).rad()
        val h = sin(dLat / 2) * sin(dLat / 2) +
            cos(a.lat.rad()) * cos(b.lat.rad()) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * EARTH_RADIUS_M * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }

    /** Initial bearing from a to b, in degrees [0, 360). 0 = north, 90 = east. */
    fun bearing(a: GeoPoint, b: GeoPoint): Double {
        val y = sin((b.lon - a.lon).rad()) * cos(b.lat.rad())
        val x = cos(a.lat.rad()) * sin(b.lat.rad()) -
            sin(a.lat.rad()) * cos(b.lat.rad()) * cos((b.lon - a.lon).rad())
        return normalize360(atan2(y, x).deg())
    }

    /** Point reached when walking [distanceM] metres from [start] towards [bearingDeg]. */
    fun destination(start: GeoPoint, bearingDeg: Double, distanceM: Double): GeoPoint {
        val d = distanceM / EARTH_RADIUS_M
        val brg = bearingDeg.rad()
        val lat1 = start.lat.rad()
        val lon1 = start.lon.rad()
        val lat2 = asin(sin(lat1) * cos(d) + cos(lat1) * sin(d) * cos(brg))
        val lon2 = lon1 + atan2(sin(brg) * sin(d) * cos(lat1), cos(d) - sin(lat1) * sin(lat2))
        return GeoPoint(lat2.deg(), normalize180(lon2.deg()))
    }

    fun normalize360(deg: Double): Double {
        val r = deg % 360.0
        return if (r < 0) r + 360.0 else r
    }

    fun normalize180(deg: Double): Double {
        val r = normalize360(deg)
        return if (r > 180.0) r - 360.0 else r
    }

    /** Signed smallest difference b - a in degrees, in (-180, 180]. Positive = b is clockwise from a. */
    fun angleDiff(a: Double, b: Double): Double = normalize180(b - a)

    /** Metres east / north of [origin] (local flat approximation). */
    fun toLocal(origin: GeoPoint, p: GeoPoint): DoubleArray {
        val east = (p.lon - origin.lon).rad() * EARTH_RADIUS_M * cos(origin.lat.rad())
        val north = (p.lat - origin.lat).rad() * EARTH_RADIUS_M
        return doubleArrayOf(east, north)
    }

    fun fromLocal(origin: GeoPoint, east: Double, north: Double): GeoPoint {
        val lat = origin.lat + (north / EARTH_RADIUS_M).deg()
        val lon = origin.lon + (east / (EARTH_RADIUS_M * cos(origin.lat.rad()))).deg()
        return GeoPoint(lat, lon)
    }

    /** Result of projecting a point on a segment [a, b]. */
    data class SegmentProjection(
        /** Closest point on the segment. */
        val point: GeoPoint,
        /** 0 = at a, 1 = at b. */
        val fraction: Double,
        /** Distance in metres between the input point and the segment ("cross-track"). */
        val distanceM: Double,
    )

    fun projectOnSegment(p: GeoPoint, a: GeoPoint, b: GeoPoint): SegmentProjection {
        val pb = toLocal(a, b)
        val pp = toLocal(a, p)
        val len2 = pb[0] * pb[0] + pb[1] * pb[1]
        val t = if (len2 < 1e-9) 0.0 else ((pp[0] * pb[0] + pp[1] * pb[1]) / len2).coerceIn(0.0, 1.0)
        val proj = fromLocal(a, pb[0] * t, pb[1] * t)
        return SegmentProjection(proj, t, distance(p, proj))
    }

    /** "nord", "nord-est"... or "north", "north-east"... for a bearing. */
    fun cardinal(bearingDeg: Double, lang: Lang): String {
        val namesFr = listOf("nord", "nord-est", "est", "sud-est", "sud", "sud-ouest", "ouest", "nord-ouest")
        val namesEn = listOf("north", "north-east", "east", "south-east", "south", "south-west", "west", "north-west")
        val idx = (((normalize360(bearingDeg) + 22.5) / 45.0).toInt()) % 8
        return if (lang == Lang.FR) namesFr[idx] else namesEn[idx]
    }
}

/** Language of the spoken messages. The final product targets French users. */
enum class Lang { FR, EN }
