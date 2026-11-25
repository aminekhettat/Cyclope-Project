package org.blindsystems.cyclope.navigation.core

import org.junit.Assert.assertEquals
import org.junit.Test

class InstructionFormatterEdgeTest {
    private fun step(
        type: String = "turn",
        modifier: String? = null,
        name: String = "",
        bearing: Double = 0.0,
        exit: Int? = null,
    ) = RouteStep(type, modifier, GeoPoint(0.0, 0.0), name, 100.0, 60.0, 0.0, bearing, exit)

    @Test
    fun directionsIncludeEveryModifierAndUnknownFallbackInBothLanguages() {
        val rows =
            listOf(
                Triple("left", "tournez à gauche", "turn left"),
                Triple("right", "tournez à droite", "turn right"),
                Triple("slight left", "tournez légèrement à gauche", "bear left"),
                Triple("slight right", "tournez légèrement à droite", "bear right"),
                Triple("sharp left", "tournez franchement à gauche", "turn sharp left"),
                Triple("sharp right", "tournez franchement à droite", "turn sharp right"),
                Triple("uturn", "faites demi-tour", "make a U-turn"),
                Triple("straight", "continuez tout droit", "continue straight"),
                Triple("unknown", "continuez tout droit", "continue straight"),
            )
        rows.forEach { (modifier, fr, en) ->
            assertEquals(fr, InstructionFormatter(Lang.FR).action(step(modifier = modifier)))
            assertEquals(en, InstructionFormatter(Lang.EN).action(step(modifier = modifier)))
        }
        assertEquals("continuez tout droit", InstructionFormatter().action(step()))
    }

    @Test
    fun maneuverTypesAndOptionalStreetNamesHaveSpeakableMessages() {
        val fr = InstructionFormatter()
        val en = InstructionFormatter(Lang.EN)
        assertEquals("dirigez-vous vers l'ouest", fr.action(step("depart", bearing = 270.0)))
        assertEquals("head north onto Main", en.action(step("depart", name = "Main")))
        assertEquals("à l'embranchement, tournez à gauche sur Rue A", fr.action(step("fork", "left", "Rue A")))
        assertEquals("at the fork, turn right", en.action(step("fork", "right")))
        assertEquals("at the end of the road, turn left", en.action(step("end of road", "left")))
        listOf("continue", "new name").forEach { type ->
            assertEquals("continuez tout droit", fr.action(step(type)))
            assertEquals("continue straight", en.action(step(type, "straight")))
            assertEquals("tournez à droite", fr.action(step(type, "right")))
            assertEquals("turn left onto Main", en.action(step(type, "left", "Main")))
        }
        listOf("roundabout", "rotary").forEach { type ->
            assertEquals("au rond-point, prenez", fr.action(step(type)))
            assertEquals("au rond-point, prenez la première sortie", fr.action(step(type, exit = 1)))
            assertEquals("au rond-point, prenez la 3e sortie", fr.action(step(type, exit = 3)))
            assertEquals("at the roundabout, take", en.action(step(type)))
            assertEquals("at the roundabout, take exit 2 onto Main", en.action(step(type, name = "Main", exit = 2)))
        }
    }

    @Test
    fun arrivalSideAndPreparationMessagesInBothLanguages() {
        listOf(null, "left", "right", "straight").forEach { modifier ->
            val frSide =
                when (modifier) {
                    "left" -> ", sur votre gauche"
                    "right" -> ", sur votre droite"
                    else -> ""
                }
            val enSide =
                when (modifier) {
                    "left" -> ", on your left"
                    "right" -> ", on your right"
                    else -> ""
                }
            val arrival = step("arrive", modifier)
            assertEquals("vous êtes arrivé à destination$frSide", InstructionFormatter().action(arrival))
            assertEquals("Destination dans 10 mètres$frSide.", InstructionFormatter().prepare(arrival, 12.0))
            assertEquals("you have arrived at your destination$enSide", InstructionFormatter(Lang.EN).action(arrival))
            assertEquals("Destination in 50 meters$enSide.", InstructionFormatter(Lang.EN).prepare(arrival, 50.0))
            assertEquals("You have arrived at your destination$enSide.", InstructionFormatter(Lang.EN).now(arrival))
        }
        assertEquals("Maintenant, tournez à gauche.", InstructionFormatter().now(step(modifier = "left")))
        assertEquals("Now, turn right.", InstructionFormatter(Lang.EN).now(step(modifier = "right")))
        assertEquals("Dans 10 mètres, tournez à gauche.", InstructionFormatter().prepare(step(modifier = "left"), 12.0))
    }

    @Test
    fun roundingThresholdsWarningsAndSummaryAreCoveredInBothLanguages() {
        val fr = InstructionFormatter()
        val en = InstructionFormatter(Lang.EN)
        mapOf(
            -5.0 to "5 mètres",
            0.0 to "5 mètres",
            99.0 to "100 mètres",
            100.0 to "100 mètres",
            499.0 to "500 mètres",
            500.0 to "500 mètres",
            1000.0 to "1 kilomètre",
            2000.0 to "2 kilomètres",
        ).forEach {
                (m, expected) ->
            assertEquals(expected, fr.distance(m))
        }
        assertEquals("1 kilometer", en.distance(1000.0))
        assertEquals("2 kilometers", en.distance(2000.0))
        assertEquals("50 meters", en.distance(50.0))
        assertEquals("Puis continuez sur 50 mètres.", fr.thenContinue(50.0))
        assertEquals("Then continue for 50 meters.", en.thenContinue(50.0))
        val route = Route(50.0, 0.0, emptyList(), emptyList())
        // Durations that round to one minute use the singular form.
        assertEquals("Itinéraire calculé : 50 mètres, environ 1 minute à pied.", fr.routeSummary(route))
        assertEquals("Route found: 50 meters, about 1 minute on foot.", en.routeSummary(route))
        val oneMinute = Route(50.0, 60.0, emptyList(), emptyList())
        assertEquals("Route found: 50 meters, about 1 minute on foot.", en.routeSummary(oneMinute))
        val twoMinutes = Route(50.0, 120.0, emptyList(), emptyList())
        assertEquals("Itinéraire calculé : 50 mètres, environ 2 minutes à pied.", fr.routeSummary(twoMinutes))
        assertEquals("Route found: 50 meters, about 2 minutes on foot.", en.routeSummary(twoMinutes))
        assertEquals("Attention, vous vous éloignez de l'itinéraire. Recalcul en cours.", fr.offRoute())
        assertEquals("Warning, you are leaving the route. Recalculating.", en.offRoute())
        assertEquals("Vous êtes de nouveau sur l'itinéraire.", fr.backOnRoute())
        assertEquals("You are back on the route.", en.backOnRoute())
        assertEquals("Signal GPS faible, guidage moins précis.", fr.gpsWeak())
        assertEquals("Weak GPS signal, guidance is less precise.", en.gpsWeak())
        assertEquals("Signal GPS retrouvé.", fr.gpsRecovered())
        assertEquals("GPS signal recovered.", en.gpsRecovered())
    }
}
