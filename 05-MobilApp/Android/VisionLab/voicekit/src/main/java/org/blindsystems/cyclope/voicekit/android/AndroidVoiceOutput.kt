package org.blindsystems.cyclope.voicekit.android

import android.content.Context
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.accessibility.AccessibilityManager
import org.blindsystems.cyclope.voicekit.core.AnnouncementQueue
import org.blindsystems.cyclope.voicekit.core.AnnouncementRoute
import org.blindsystems.cyclope.voicekit.core.UpdateGate
import org.blindsystems.cyclope.voicekit.core.VoiceAnnouncement
import org.blindsystems.cyclope.voicekit.core.announcementRoute
import java.util.Locale

/** All policy and TTS calls happen on the main thread. A screen reader always has priority. */
class AndroidVoiceOutput(private val context: Context, private val announceAccessibility: (String) -> Unit, private val report: (String) -> Unit, language: Locale = Locale.FRENCH) {
    private val accessibility = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    private val policy = AnnouncementQueue()
    private val accessibilityUpdates = UpdateGate(10000)
    private var lastRoute = AnnouncementRoute.MUTED
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
                        val languageStatus = engine!!.setLanguage(language)
                        ready = languageStatus != TextToSpeech.LANG_MISSING_DATA && languageStatus != TextToSpeech.LANG_NOT_SUPPORTED
                    }
                    report(if (ready) "Speech ready. TalkBack receives polite accessibility events instead of raw TTS." else "Speech voice unavailable for ${language.displayName}. Install that voice; text announcements remain available.")
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

    fun offer(announcements: List<VoiceAnnouncement>) {
        val route = announcementRoute(muted, screenReader)
        if (route != lastRoute) {
            stop() // Also cancels raw TTS before switching to the accessibility service.
            lastRoute = route
        }
        if (route == AnnouncementRoute.MUTED) return
        if (route == AnnouncementRoute.TTS && !ready) return
        val now = SystemClock.elapsedRealtime()
        for (announcement in announcements) policy.offer(announcement, now, muted)
        if (route == AnnouncementRoute.ACCESSIBILITY) {
            // This path works even when no requested Android TTS voice is installed.
            if (policy.peek() != null && accessibilityUpdates.mayPublish(now)) {
                val messages = mutableListOf<String>()
                while (policy.peek() != null) {
                    val announcement = policy.poll()!!
                    messages += announcement.text
                }
                announceAccessibility(messages.joinToString(". "))
            }
        } else {
            next()
        }
    }

    private fun next() {
        if (busy || !ready || muted || screenReader) return
        val announcement = policy.poll() ?: return
        busy = true
        val utterance = "speech-${++sequence}"
        activeUtterance = utterance
        if (tts?.speak(announcement.text, TextToSpeech.QUEUE_ADD, null, utterance) == TextToSpeech.ERROR) {
            activeUtterance = null
            busy = false
            policy.clear()
            report("Speech failed; read the detection summary.")
        }
    }

    fun stop() {
        activeUtterance = null
        accessibilityUpdates.reset()
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
