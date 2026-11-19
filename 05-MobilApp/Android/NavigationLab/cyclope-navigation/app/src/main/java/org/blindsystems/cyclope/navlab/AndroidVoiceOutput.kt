package org.blindsystems.cyclope.navlab

import android.content.Context
import android.speech.tts.TextToSpeech
import org.blindsystems.cyclope.navigation.VoiceOutput
import org.blindsystems.cyclope.navigation.core.Priority
import java.util.Locale

/**
 * STUB of the voice-feedback module (Shana's block), so the navigation block can be tested alone.
 * Uses Android's built-in TextToSpeech. WARNING messages interrupt the current sentence.
 * Every message is also passed to [onSpoken] so it appears in the on-screen log.
 */
class AndroidVoiceOutput(
    context: Context,
    private val onSpoken: (String) -> Unit,
) : VoiceOutput, TextToSpeech.OnInitListener {

    // Declared before `tts` because onInit() uses them.
    private var ready = false
    private val pending = mutableListOf<Pair<String, Priority>>()
    private var counter = 0

    private val tts = TextToSpeech(context.applicationContext, this)

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts.setLanguage(Locale.FRANCE)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                // Some phones (e.g. without Google services) have no French voice: speak with the default one.
                tts.setLanguage(Locale.getDefault())
                onSpoken("! No French voice on this phone (engine: ${tts.defaultEngine}). Using ${Locale.getDefault()}. " +
                    "Install a French TTS voice (e.g. eSpeak NG) for French speech.")
            } else {
                onSpoken("(Voice ready: French, engine ${tts.defaultEngine})")
            }
            ready = true
            pending.forEach { speakNow(it.first, it.second) }
            pending.clear()
        } else {
            onSpoken("! TextToSpeech init failed (status $status)")
        }
    }

    override fun speak(text: String, priority: Priority) {
        onSpoken("[${priority.name}] $text")
        if (ready) speakNow(text, priority) else pending += text to priority
    }

    private fun speakNow(text: String, priority: Priority) {
        val mode = if (priority == Priority.WARNING) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        tts.speak(text, mode, null, "nav-${counter++}")
    }

    fun stop() {
        tts.stop()
    }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
    }
}
