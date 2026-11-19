package org.blindsystems.cyclope.navigation.core

import org.junit.Assert.assertEquals
import org.junit.Test

class InstructionFormatterTest {
    private val fr = InstructionFormatter(Lang.FR)
    private val en = InstructionFormatter(Lang.EN)
    private val p = GeoPoint(48.71, 2.17)

    private fun step(type: String, modifier: String?, name: String = "Rue B", after: Double = 90.0, exit: Int? = null) =
        RouteStep(type, modifier, p, name, 100.0, 70.0, 0.0, after, exit)

    @Test
    fun distanceRounding() {
        assertEquals("5 mètres", fr.distance(2.0))
        assertEquals("45 mètres", fr.distance(47.0))
        assertEquals("130 mètres", fr.distance(127.0))
        assertEquals("650 mètres", fr.distance(640.0))
        assertEquals("1,2 kilomètre", fr.distance(1_230.0))
        assertEquals("2 kilomètres", fr.distance(2_010.0))
        assertEquals("1.5 kilometers", en.distance(1_490.0))
    }

    @Test
    fun turnsInFrench() {
        assertEquals("Dans 50 mètres, tournez à gauche sur Rue B.", fr.prepare(step("turn", "left"), 48.0))
        assertEquals("Maintenant, tournez légèrement à droite sur Rue B.", fr.now(step("turn", "slight right")))
        assertEquals("Maintenant, continuez tout droit.", fr.now(step("new name", "straight", name = "")))
        assertEquals("au bout de la rue, tournez à droite sur Rue B", fr.action(step("end of road", "right")))
        assertEquals("au rond-point, prenez la 2e sortie sur Rue B", fr.action(step("roundabout", "right", exit = 2)))
    }

    @Test
    fun departAndArrive() {
        assertEquals("dirigez-vous vers l'est sur Rue B", fr.action(step("depart", null, after = 90.0)))
        assertEquals("dirigez-vous vers le nord-est sur Rue B", fr.action(step("depart", null, after = 45.0)))
        assertEquals("Vous êtes arrivé à destination, sur votre gauche.", fr.now(step("arrive", "left")))
        assertEquals("Destination dans 30 mètres, sur votre gauche.", fr.prepare(step("arrive", "left"), 31.0))
        assertEquals("In 50 meters, turn left onto Rue B.", en.prepare(step("turn", "left"), 50.0))
    }
}
