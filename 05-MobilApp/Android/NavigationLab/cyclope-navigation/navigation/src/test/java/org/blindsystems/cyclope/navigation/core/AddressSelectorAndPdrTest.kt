package org.blindsystems.cyclope.navigation.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AddressSelectorAndPdrTest {
    private val user = GeoPoint(48.71, 2.17)
    private val places = NominatimParser.parse(fixture("nominatim_search_handmade.json"), near = user)

    @Test
    fun shortPressCyclesLongPressSelects() {
        val sel = AddressSelector(places)
        val first = sel.start() as AddressSelector.Result.Speak
        assertTrue(first.message.startsWith("J'ai trouvé 3 adresses."))
        assertTrue(first.message.contains("Adresse 1 : 54 Rue Jean Jaurès, Orsay, 91400, à "))

        val second = sel.onButton(ButtonEvent.SHORT_PRESS) as AddressSelector.Result.Speak
        assertTrue(second.message.startsWith("Adresse 2 : Gare de Lyon"))
        sel.onButton(ButtonEvent.SHORT_PRESS)
        val wrapped = sel.onButton(ButtonEvent.SHORT_PRESS) as AddressSelector.Result.Speak
        assertTrue(wrapped.message.startsWith("Retour au début. Adresse 1"))

        val chosen = sel.onButton(ButtonEvent.LONG_PRESS) as AddressSelector.Result.Selected
        assertEquals("54 Rue Jean Jaurès, Orsay, 91400", chosen.place.spokenName)
        assertTrue(sel.finished)
    }

    @Test
    fun doublePressMeansNoneIsCorrect() {
        val sel = AddressSelector(places)
        sel.start()
        assertTrue(sel.onButton(ButtonEvent.DOUBLE_PRESS) is AddressSelector.Result.NoneMatches)
    }

    @Test
    fun emptySearchAsksToDictateAgain() {
        assertTrue(AddressSelector(emptyList()).start() is AddressSelector.Result.NoneMatches)
    }

    @Test
    fun pdrDoesNotBecomeOverConfidentWhenStandingStill() {
        // Field observation 19/11: without a floor the uncertainty fell to 0.4 m indoors.
        val pdr = PdrFusion()
        repeat(60) { pdr.onGnssFix(user, accuracyM = 5.0) }
        assertTrue(pdr.sigmaM >= 3.0)
    }

    @Test
    fun pdrMovesWithStepsAndGnssCorrects() {
        val pdr = PdrFusion(stepLengthM = 0.7)
        pdr.onStep(0.0) // no start point yet: ignored
        assertEquals(null, pdr.estimate)

        pdr.onGnssFix(user, accuracyM = 5.0)
        repeat(10) { pdr.onStep(0.0) } // 10 steps north
        assertEquals(7.0, Geo.distance(user, pdr.estimate!!), 0.01)
        assertEquals(10, pdr.stepsSinceLastFix)

        // A very precise fix pulls the estimate almost onto it; a very bad one barely moves it.
        val before = pdr.estimate!!
        val fix = Geo.destination(before, 90.0, 10.0)
        val bad = PdrFusion().apply { onGnssFix(before, 1.0) }
        bad.onGnssFix(fix, accuracyM = 100.0)
        assertTrue(Geo.distance(before, bad.estimate!!) < 0.1)

        pdr.onGnssFix(fix, accuracyM = 0.5)
        assertTrue(Geo.distance(fix, pdr.estimate!!) < 1.0)
    }
}
