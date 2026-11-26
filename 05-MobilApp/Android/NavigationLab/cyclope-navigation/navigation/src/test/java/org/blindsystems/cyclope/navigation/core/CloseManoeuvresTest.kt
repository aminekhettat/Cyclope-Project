package org.blindsystems.cyclope.navigation.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Manoeuvres only a few metres apart (typical of OSRM foot routes: sidewalk -> crossing -> sidewalk)
 * must be said in one sentence, not as "left!" immediately followed by "right!".
 */
class CloseManoeuvresTest {
    private val origin = GeoPoint(48.7100, 2.1700)

    /** origin --100 m north (Rue A)--> P1, turn left --[gapM] west--> P2, turn right --100 m north (Rue B)--> P3. */
    private fun zigzag(gapM: Double): Route {
        val p1 = Geo.destination(origin, 0.0, 100.0)
        val p2 = Geo.destination(p1, 270.0, gapM)
        val p3 = Geo.destination(p2, 0.0, 100.0)
        val steps = listOf(
            RouteStep("depart", null, origin, "Rue A", 100.0, 72.0, 0.0, 0.0),
            RouteStep("turn", "left", p1, "", gapM, 5.0, 0.0, 270.0),
            RouteStep("turn", "right", p2, "Rue B", 100.0, 72.0, 270.0, 0.0),
            RouteStep("arrive", null, p3, "Rue B", 0.0, 0.0, 0.0, 0.0),
        )
        return Route(200.0 + gapM, 150.0, listOf(origin, p1, p2, p3), steps)
    }

    /** origin --[firstM] north (Rue A)--> P1, turn right --100 m east (Rue B)--> P2. */
    private fun lShape(firstM: Double): Route {
        val p1 = Geo.destination(origin, 0.0, firstM)
        val p2 = Geo.destination(p1, 90.0, 100.0)
        val steps = listOf(
            RouteStep("depart", null, origin, "Rue A", firstM, 4.0, 0.0, 0.0),
            RouteStep("turn", "right", p1, "Rue B", 100.0, 72.0, 0.0, 90.0),
            RouteStep("arrive", null, p2, "Rue B", 0.0, 0.0, 90.0, 90.0),
        )
        return Route(firstM + 100.0, 76.0, listOf(origin, p1, p2), steps)
    }

    private fun walkWhole(f: RouteFollower): List<GuidanceEvent> =
        walk(f.route.geometry, 1.0).flatMap { f.update(it, 5.0) }

    @Test
    fun veryCloseTurnsAreSaidTogetherAndTheSecondIsNotRepeated() {
        val f = RouteFollower(zigzag(6.0))
        f.start()
        val events = walkWhole(f)
        assertEquals(listOf("Prepare", "Now", "Prepare", "Arrived"), events.map { it::class.simpleName })
        assertEquals(
            "Dans 50 mètres, tournez à gauche, puis 5 mètres plus loin, tournez à droite sur Rue B.",
            events[0].message,
        )
        assertEquals(
            "Maintenant, tournez à gauche, puis 5 mètres plus loin, tournez à droite sur Rue B. " +
                "Puis continuez sur 100 mètres.",
            events[1].message,
        )
        assertEquals(0, events.count { it is GuidanceEvent.Now && it.stepIndex == 2 })
    }

    @Test
    fun closeButNotImmediateTurnKeepsItsOwnNowButNoSecondPrepare() {
        val f = RouteFollower(zigzag(16.0))
        f.start()
        val events = walkWhole(f)
        assertEquals(listOf("Prepare", "Now", "Now", "Prepare", "Arrived"), events.map { it::class.simpleName })
        assertTrue(events[0].message.contains("puis 15 mètres plus loin, tournez à droite sur Rue B"))
        assertEquals(2, (events[2] as GuidanceEvent.Now).stepIndex)
        assertEquals("Maintenant, tournez à droite sur Rue B. Puis continuez sur 100 mètres.", events[2].message)
        assertEquals(0, events.count { it is GuidanceEvent.Prepare && it.stepIndex == 2 })
    }

    @Test
    fun turnRightAfterTheStartIsPartOfTheDepartureSentence() {
        val f = RouteFollower(lShape(5.0))
        val start = f.start()
        assertEquals(
            "Dirigez-vous vers le nord sur Rue A, puis 5 mètres plus loin, tournez à droite sur Rue B. " +
                "Puis continuez sur 100 mètres.",
            start[1].message,
        )
        val events = walkWhole(f)
        assertEquals(0, events.count { it is GuidanceEvent.Now && it.stepIndex == 1 })
        assertEquals(listOf("Prepare", "Arrived"), events.map { it::class.simpleName })
    }

    @Test
    fun farApartTurnsAreUnchanged() {
        val f = RouteFollower(zigzag(30.0))
        f.start()
        val events = walkWhole(f)
        assertEquals(
            listOf("Prepare", "Now", "Prepare", "Now", "Prepare", "Arrived"),
            events.map { it::class.simpleName },
        )
        assertEquals("Dans 50 mètres, tournez à gauche.", events[0].message)
    }

    @Test
    fun englishCombinedSentences() {
        val route = zigzag(6.0)
        val en = InstructionFormatter(Lang.EN)
        assertEquals(
            "In 50 meters, turn left, then 5 meters later, turn right onto Rue B.",
            en.prepare(route.steps[1], 50.0, route.steps[2], 6.0),
        )
        assertEquals(
            "Now, turn left, then 5 meters later, turn right onto Rue B.",
            en.now(route.steps[1], route.steps[2], 6.0),
        )
    }

    @Test
    fun gpsRecoveredNeedsClearlyBetterAccuracy() {
        val f = RouteFollower(zigzag(30.0))
        val p = Geo.destination(origin, 0.0, 10.0)
        assertTrue(f.update(p, 40.0).single() is GuidanceEvent.GpsWeak)
        assertTrue(f.update(p, 30.0).isEmpty()) // usable fix, but not "recovered" yet
        assertTrue(f.update(p, 40.0).isEmpty()) // and no second "weak" warning
        assertTrue(f.update(p, 20.0).single() is GuidanceEvent.GpsRecovered)
    }
}
