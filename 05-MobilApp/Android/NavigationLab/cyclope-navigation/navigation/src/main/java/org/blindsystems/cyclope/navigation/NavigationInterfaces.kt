package org.blindsystems.cyclope.navigation

import org.blindsystems.cyclope.navigation.core.ButtonEvent
import org.blindsystems.cyclope.navigation.core.GeoPoint
import org.blindsystems.cyclope.navigation.core.Priority

/*
 * Interface contract between the navigation block and the other blocks of the Android app.
 * Goal (from the project slides): "interfaces frozen before starting, and a substitute for each
 * dependency: nobody waits for anybody". Each interface below has a stub so this block can be
 * developed and tested alone. To be validated with the team.
 */

/**
 * OUTPUT -> voice-feedback module (Shana).
 * Navigation only produces text + priority; the voice module decides when/how to speak it
 * (e.g. a "zebra crossing" alert from vision must interrupt a navigation sentence).
 * Stub in the test app: Android TextToSpeech.
 */
interface VoiceOutput {
    fun speak(text: String, priority: Priority)
}

/**
 * INPUT <- speech-to-text (microphone block): the address dictated by the user, as text.
 * Stub in the test app: an EditText where the address is typed.
 */
interface AddressInputListener {
    fun onAddressDictated(text: String)
}

/**
 * INPUT <- glasses buttons (firmware -> TCP link -> app).
 * Stub in the test app: three on-screen buttons (short / long / double).
 */
interface ButtonListener {
    fun onButton(event: ButtonEvent)
}

/**
 * INPUT <- localisation (this block): one position fix.
 * @param accuracyM horizontal accuracy (68 % radius) as given by Android
 */
interface PositionListener {
    fun onPosition(source: String, point: GeoPoint, accuracyM: Double, timeMs: Long)
}
