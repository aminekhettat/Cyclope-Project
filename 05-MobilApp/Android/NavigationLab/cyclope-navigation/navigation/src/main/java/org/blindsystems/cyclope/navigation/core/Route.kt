package org.blindsystems.cyclope.navigation.core

/**
 * One OSRM step = one manoeuvre ("turn left onto X") followed by a stretch of path.
 * Field names follow the OSRM API (https://project-osrm.org/docs/v5.24.0/api/#routestep-object).
 */
data class RouteStep(
    /** OSRM maneuver.type: depart, turn, new name, continue, end of road, fork, roundabout, arrive... */
    val type: String,
    /** OSRM maneuver.modifier: left, right, slight left, sharp right, straight, uturn... (may be null). */
    val modifier: String?,
    /** Where the manoeuvre happens. */
    val location: GeoPoint,
    /** Street name after the manoeuvre ("" if unnamed, e.g. a footpath). */
    val name: String,
    /** Length of this step in metres (from this manoeuvre to the next one). */
    val distanceM: Double,
    val durationS: Double,
    val bearingBefore: Double,
    val bearingAfter: Double,
    /** Roundabout exit number, if any. */
    val exit: Int? = null,
)

/** A full walking route returned by OSRM. */
data class Route(
    val distanceM: Double,
    val durationS: Double,
    /** Full geometry of the route (overview=full). */
    val geometry: List<GeoPoint>,
    val steps: List<RouteStep>,
)

/**
 * Pre-computed helper to locate a position along the route geometry
 * ("how far along the route am I, and how far am I from it?").
 */
class RouteGeometry(val points: List<GeoPoint>) {
    init {
        require(points.size >= 2) { "A route needs at least 2 points" }
    }

    /** cumulative[i] = distance along the route from points[0] to points[i]. */
    val cumulative: DoubleArray = DoubleArray(points.size).also { c ->
        for (i in 1 until points.size) c[i] = c[i - 1] + Geo.distance(points[i - 1], points[i])
    }

    val totalLengthM: Double get() = cumulative.last()

    data class Position(
        /** Index of the segment [points[i], points[i+1]] the position was snapped to. */
        val segmentIndex: Int,
        /** Distance along the route from the start, in metres. */
        val alongM: Double,
        /** Distance between the real position and the route, in metres. */
        val crossTrackM: Double,
        val snapped: GeoPoint,
    )

    /**
     * Snap [p] to the closest point of the route.
     * Only segments whose start is at least [minAlongM] along the route are considered:
     * this prevents jumping backwards when the route passes twice near the same place.
     */
    fun locate(p: GeoPoint, minAlongM: Double = 0.0): Position {
        var best: Position? = null
        for (i in 0 until points.size - 1) {
            if (cumulative[i + 1] < minAlongM) continue
            val proj = Geo.projectOnSegment(p, points[i], points[i + 1])
            val along = cumulative[i] + proj.fraction * (cumulative[i + 1] - cumulative[i])
            if (best == null || proj.distanceM < best.crossTrackM) {
                best = Position(i, along, proj.distanceM, proj.point)
            }
        }
        // minAlongM beyond the end of the route: snap to the last point.
        return best ?: Position(points.size - 2, totalLengthM, Geo.distance(p, points.last()), points.last())
    }
}
