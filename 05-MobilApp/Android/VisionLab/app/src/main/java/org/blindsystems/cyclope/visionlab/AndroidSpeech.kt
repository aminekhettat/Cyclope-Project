package org.blindsystems.cyclope.visionlab

import android.content.Context
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.accessibility.AccessibilityManager
import org.blindsystems.cyclope.vision.CocoLabels
import org.blindsystems.cyclope.vision.Detection
import org.blindsystems.cyclope.vision.SpeechPolicy
import java.util.Locale

/** All policy and TTS calls happen on the main thread. A screen reader always has priority. */
class AndroidSpeech(private val context: Context, private val report: (String) -> Unit) {
    private val accessibility = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    private val policy = SpeechPolicy()
    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    private var ready = false
    private var busy = false
    private var sequence = 0L
    private var activeUtterance: String? = null
    var muted = false
        set(value) {
            field = value
            if (value) stop()
        }
    val screenReader: Boolean get() = accessibility.isEnabled && accessibility.isTouchExplorationEnabled
    private val listener = AccessibilityManager.TouchExplorationStateChangeListener { enabled -> if (enabled) stop() }
    private var tts: TextToSpeech? = null

    init {
        accessibility.addTouchExplorationStateChangeListener(listener)
        tts =
            TextToSpeech(context) { status ->
                main.post {
                    val engine = tts
                    ready = status == TextToSpeech.SUCCESS && engine != null
                    if (ready) {
                        val language = engine!!.setLanguage(Locale.FRENCH)
                        ready = language != TextToSpeech.LANG_MISSING_DATA && language != TextToSpeech.LANG_NOT_SUPPORTED
                    }
                    report(if (ready) "Speech ready. TalkBack has priority." else "French speech unavailable. Install a French TTS voice; text detections remain available.")
                }
            }
        tts?.setOnUtteranceProgressListener(
            object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit

                override fun onDone(utteranceId: String?) {
                    main.post {
                        if (utteranceId == activeUtterance) {
                            activeUtterance = null
                            busy = false
                            next()
                        }
                    }
                }

                @Deprecated("Android callback")
                override fun onError(utteranceId: String?) {
                    main.post {
                        if (utteranceId == activeUtterance) {
                            activeUtterance = null
                            busy = false
                            policy.clear()
                            report("Speech failed; read the detection summary.")
                        }
                    }
                }
            },
        )
    }

    fun offer(detections: List<Detection>) {
        if (!ready) return
        if (muted || screenReader) {
            stop()
            return
        }
        for (d in detections) policy.offer(d.classId, SystemClock.elapsedRealtime(), muted, screenReader)
        next()
    }

    private fun next() {
        if (busy || !ready || muted || screenReader) return
        val id = policy.poll() ?: return
        busy = true
        val utterance = "speech-${++sequence}"
        activeUtterance = utterance
        if (tts?.speak("${CocoLabels.french[id]} détecté", TextToSpeech.QUEUE_ADD, null, utterance) == TextToSpeech.ERROR) {
            activeUtterance = null
            busy = false
            policy.clear()
            report("Speech failed; read the detection summary.")
        }
    }

    fun stop() {
        activeUtterance = null
        policy.clear()
        tts?.stop()
        busy = false
    }

    fun close() {
        stop()
        accessibility.removeTouchExplorationStateChangeListener(listener)
        tts?.shutdown()
        tts = null
        main.removeCallbacksAndMessages(null)
    }
}
