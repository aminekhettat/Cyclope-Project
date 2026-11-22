package org.blindsystems.cyclope.navigation.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectionGeometryEdgeTest {
    private val origin = GeoPoint(48.0, 2.0)

    private fun place(distance: Double? = null) = Place("Full", "Home", origin, 0.0, distance)

    @Test
    fun singleCandidateDoesNotAnnounceWrapAndEnglishSelectionIsComplete() {
        val single = AddressSelector(listOf(place()), Lang.EN)
        assertEquals(
            "I found 1 address. Short press: next. Long press: choose. Double press: none is correct. Address 1: Home.",
            (single.start() as AddressSelector.Result.Speak).message,
        )
        assertEquals("Address 1: Home.", (single.onButton(ButtonEvent.SHORT_PRESS) as AddressSelector.Result.Speak).message)
        val chosen = single.onButton(ButtonEvent.LONG_PRESS) as AddressSelector.Result.Selected
        assertEquals(place(), chosen.place)
        assertEquals("Destination selected: Home. Computing the route.", chosen.message)
        assertTrue(single.finished)
        assertThrows(IllegalStateException::class.java) { single.onButton(ButtonEvent.SHORT_PRESS) }
        val fr = AddressSelector(listOf(place()))
        assertTrue((fr.start() as AddressSelector.Result.Speak).message.startsWith("J'ai trouvé 1 adresse."))
    }

    @Test
    fun englishMultipleCandidatesHaveDistanceWrapAndNoneMatchesMessages() {
        val selector = AddressSelector(listOf(place(100.0), place(200.0)), Lang.EN)
        assertTrue((selector.start() as AddressSelector.Result.Speak).message.contains("2 addresses"))
        assertEquals(
            "Address 2: Home, 200 meters away.",
            (selector.onButton(ButtonEvent.SHORT_PRESS) as AddressSelector.Result.Speak).message,
        )
        assertEquals(
            "Back to the first one. Address 1: Home, 100 meters away.",
            (selector.onButton(ButtonEvent.SHORT_PRESS) as AddressSelector.Result.Speak).message,
        )
        assertEquals(
            "OK, no address selected. Please dictate the address again.",
            (selector.onButton(ButtonEvent.DOUBLE_PRESS) as AddressSelector.Result.NoneMatches).message,
        )
        assertThrows(IllegalStateException::class.java) { selector.onButton(ButtonEvent.LONG_PRESS) }
        val empty = AddressSelector(emptyList(), Lang.EN)
        assertEquals("No address found. Please dictate the address again.", (empty.start() as AddressSelector.Result.NoneMatches).message)
        assertTrue(empty.finished)
    }

    @Test
    fun simulatorRejectsNonPositiveStepAndHandlesEmptySingletonAndRepeatedPoints() {
        assertThrows(IllegalArgumentException::class.java) { RouteSimulator.walk(emptyList(), 0.0) }
        assertThrows(IllegalArgumentException::class.java) { RouteSimulator.walk(listOf(origin), -1.0) }
        assertTrue(RouteSimulator.walk(emptyList(), 1.0).isEmpty())
        assertEquals(listOf(origin), RouteSimulator.walk(listOf(origin), 1.0))
        assertEquals(listOf(origin, origin), RouteSimulator.walk(listOf(origin, origin), 1.0))
        val end = Geo.destination(origin, 90.0, 10.0)
        assertEquals(listOf(origin, end), RouteSimulator.walk(listOf(origin, end), 20.0))
        assertEquals(end, RouteSimulator.walk(listOf(origin, end), 3.0).last())
    }

    @Test
    fun geometryHandlesDegenerateSegmentsPastTheEndAndSegmentFiltering() {
        assertThrows(IllegalArgumentException::class.java) { RouteGeometry(listOf(origin)) }
        val end = Geo.destination(origin, 0.0, 100.0)
        val route = RouteGeometry(listOf(origin, origin, end))
        assertEquals(0.0, Geo.projectOnSegment(end, origin, origin).fraction, 0.0)
        val beyond = route.locate(end, minAlongM = 200.0)
        assertEquals(end, beyond.snapped)
        assertEquals(route.totalLengthM, beyond.alongM, 0.0)
        assertEquals(1, beyond.segmentIndex)
        val middle = route.locate(Geo.destination(origin, 0.0, 50.0), minAlongM = 1.0)
        assertEquals(1, middle.segmentIndex)
        assertEquals(50.0, middle.alongM, 0.01)
        assertEquals("south-west", Geo.cardinal(225.0, Lang.EN))
        assertEquals(-90.0, Geo.normalize180(270.0), 0.0)
    }

    @Test
    fun followerWithNoStepsProducesOnlySummaryAndStableState() {
        val end = Geo.destination(origin, 0.0, 100.0)
        val route = Route(100.0, 60.0, listOf(origin, end), emptyList())
        val follower = RouteFollower(route)
        assertTrue(follower.start().single() is GuidanceEvent.RouteStarted)
        assertTrue(follower.update(origin, 5.0).isEmpty())
        assertEquals(100.0, follower.state.distanceToNextStepM, 0.01)
        assertFalse(follower.state.arrived)
    }

    @Test
    fun limiterUsesInjectedClockForBothWaitBranches() {
        // Injected clock makes wait exactly one millisecond, with no timing assertion.
        val times = ArrayDeque(listOf(100L, 100L, 99L, 101L))
        val limiter = RateLimiter(0) { times.removeFirst() }
        limiter.acquire()
        limiter.acquire()
        assertTrue(times.isEmpty())
    }
}
