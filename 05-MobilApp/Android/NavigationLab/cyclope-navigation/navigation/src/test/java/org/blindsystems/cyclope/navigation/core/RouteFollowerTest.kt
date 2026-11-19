package org.blindsystems.cyclope.navigation.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Simulated walks on the hand-made route: A --100 m north--> B (turn right) --150 m east--> C.
 */
class RouteFollowerTest {
    private val route = OsrmParser.parse(fixture("osrm_route_handmade.json"))

    private fun walkAll(follower: RouteFollower, points: List<GeoPoint>, accuracy: Double = 5.0): List<GuidanceEvent> =
        points.flatMap { follower.update(it, accuracy) }

    @Test
    fun maneuverPositionsAlongTheRoute() {
        val f = RouteFollower(route)
        assertEquals(0.0, f.stepAlongM[0], 0.5)
        assertEquals(100.0, f.stepAlongM[1], 0.5)
        assertEquals(250.0, f.stepAlongM[2], 0.5)
    }

    @Test
    fun startAnnouncesSummaryAndDeparture() {
        val events = RouteFollower(route).start()
        assertTrue(events[0] is GuidanceEvent.RouteStarted)
        assertEquals("Itinéraire calculé : 250 mètres, environ 3 minutes à pied.", events[0].message)
        assertEquals("Dirigez-vous vers le nord sur Rue A. Puis continuez sur 100 mètres.", events[1].message)
    }

    @Test
    fun perfectWalkGivesAnnouncementsInTheRightOrder() {
        val f = RouteFollower(route)
        f.start()
        val events = walkAll(f, walk(route.geometry, 1.0))
        val kinds = events.map { it::class.simpleName }
        assertEquals(listOf("Prepare", "Now", "Prepare", "Arrived"), kinds)

        val prepare = events[0] as GuidanceEvent.Prepare
        assertEquals(1, prepare.stepIndex)
        assertTrue(prepare.distanceM in 45.0..50.0)
        assertEquals("Dans 50 mètres, tournez à droite sur Rue B.", prepare.message)
        assertEquals("Maintenant, tournez à droite sur Rue B. Puis continuez sur 150 mètres.", events[1].message)
        assertTrue(events[2].message.startsWith("Destination dans"))
        assertEquals("Vous êtes arrivé à destination, sur votre gauche.", events[3].message)
        assertTrue(f.state.arrived)
        // No more messages after arrival
        assertTrue(f.update(route.geometry.last(), 5.0).isEmpty())
    }

    @Test
    fun noisyWalkStillGivesEachAnnouncementOnce() {
        val f = RouteFollower(route)
        val rnd = java.util.Random(42)
        val noisy = walk(route.geometry, 1.5).map {
            Geo.destination(it, rnd.nextDouble() * 360.0, rnd.nextDouble() * 6.0) // up to 6 m of noise
        }
        val events = walkAll(f, noisy, accuracy = 8.0)
        assertEquals(1, events.count { it is GuidanceEvent.Now && it.stepIndex == 1 })
        assertEquals(1, events.count { it is GuidanceEvent.Arrived })
        assertEquals(0, events.count { it is GuidanceEvent.OffRoute })
    }

    @Test
    fun offRouteNeedsThreeConsecutiveFixesThenBackOnRoute() {
        val f = RouteFollower(route)
        val onRoute = Geo.destination(route.geometry[0], 0.0, 30.0)
        val farAway = Geo.destination(onRoute, 270.0, 40.0) // 40 m west of the first street

        assertTrue(f.update(onRoute, 5.0).isEmpty())
        assertTrue(f.update(farAway, 5.0).isEmpty())
        assertTrue(f.update(farAway, 5.0).isEmpty())
        val third = f.update(farAway, 5.0)
        assertEquals(1, third.size)
        assertTrue(third[0] is GuidanceEvent.OffRoute)
        assertEquals(Priority.WARNING, third[0].priority)
        assertTrue(f.update(farAway, 5.0).isEmpty()) // said only once
        assertTrue(f.state.offRoute)

        val back = f.update(onRoute, 5.0)
        assertTrue(back.any { it is GuidanceEvent.BackOnRoute })
    }

    @Test
    fun bigAccuracyRadiusDoesNotTriggerOffRoute() {
        // 30 m from the route but the phone itself says "accuracy 34 m": not a reliable off-route
        val f = RouteFollower(route)
        val p = Geo.destination(Geo.destination(route.geometry[0], 0.0, 30.0), 270.0, 30.0)
        repeat(5) { assertTrue(f.update(p, 34.0).none { it is GuidanceEvent.OffRoute }) }
    }

    @Test
    fun weakGpsIsAnnouncedOnceThenRecovered() {
        val f = RouteFollower(route)
        val p = Geo.destination(route.geometry[0], 0.0, 10.0)
        val e1 = f.update(p, 80.0)
        assertTrue(e1.single() is GuidanceEvent.GpsWeak)
        assertTrue(f.update(p, 60.0).isEmpty())
        assertTrue(f.update(p, 5.0).first() is GuidanceEvent.GpsRecovered)
    }
}
