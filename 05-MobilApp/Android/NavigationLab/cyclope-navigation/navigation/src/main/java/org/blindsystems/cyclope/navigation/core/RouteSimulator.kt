package org.blindsystems.cyclope.navigation.core

/**
 * Generates fake positions every [stepM] metres along a route geometry.
 * Used to demo / test the guidance indoors without walking (no GNSS needed).
 */
object RouteSimulator {
    fun walk(points: List<GeoPoint>, stepM: Double): List<GeoPoint> {
        require(stepM > 0) { "stepM must be > 0" }
        if (points.isEmpty()) return emptyList()
        val out = mutableListOf(points.first())
        for (i in 0 until points.size - 1) {
            val a = points[i]
            val b = points[i + 1]
            val len = Geo.distance(a, b)
            val brg = Geo.bearing(a, b)
            var d = stepM
            while (d < len) {
                out += Geo.destination(a, brg, d)
                d += stepM
            }
            out += b
        }
        return out
    }
}
