package org.blindsystems.cyclope.navigation.core

import org.junit.Assert.assertEquals
import org.junit.Test

class GeoTest {
    private val paris = GeoPoint(48.8566, 2.3522)
    private val london = GeoPoint(51.5074, -0.1278)

    @Test
    fun distanceParisLondon() {
        // Reference value ~343.5 km (great circle)
        assertEquals(343_500.0, Geo.distance(paris, london), 1_500.0)
    }

    @Test
    fun bearingCardinalDirections() {
        val o = GeoPoint(48.71, 2.17)
        assertEquals(0.0, Geo.bearing(o, GeoPoint(48.72, 2.17)), 0.01)
        assertEquals(90.0, Geo.bearing(o, Geo.destination(o, 90.0, 100.0)), 0.01)
        assertEquals(180.0, Geo.bearing(o, GeoPoint(48.70, 2.17)), 0.01)
        assertEquals("nord-est", Geo.cardinal(44.0, Lang.FR))
        assertEquals("north", Geo.cardinal(350.0, Lang.EN))
    }

    @Test
    fun destinationThenDistanceIsConsistent() {
        val o = GeoPoint(48.71, 2.17)
        val p = Geo.destination(o, 37.0, 250.0)
        assertEquals(250.0, Geo.distance(o, p), 0.01)
        assertEquals(37.0, Geo.bearing(o, p), 0.01)
    }

    @Test
    fun projectionOnSegment() {
        val a = GeoPoint(48.71, 2.17)
        val b = Geo.destination(a, 0.0, 100.0) // 100 m north
        val p = Geo.destination(Geo.destination(a, 0.0, 30.0), 90.0, 8.0) // 30 m along, 8 m east
        val proj = Geo.projectOnSegment(p, a, b)
        assertEquals(0.30, proj.fraction, 0.01)
        assertEquals(8.0, proj.distanceM, 0.1)
    }

    @Test
    fun angleDiff() {
        assertEquals(20.0, Geo.angleDiff(350.0, 10.0), 1e-9)
        assertEquals(-20.0, Geo.angleDiff(10.0, 350.0), 1e-9)
    }
}
