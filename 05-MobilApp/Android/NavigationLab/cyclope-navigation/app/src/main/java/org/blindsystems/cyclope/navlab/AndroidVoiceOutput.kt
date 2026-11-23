package org.blindsystems.cyclope.navlab

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.blindsystems.cyclope.navigation.VoiceOutput
import org.blindsystems.cyclope.navigation.core.Priority
import org.blindsystems.cyclope.voicekit.android.AndroidVoiceOutput as SharedVoiceOutput

/** Thin navigation host for the exact shared VisionLab voicekit; no duplicate TTS/TalkBack policy. */
class AndroidVoiceOutput(
    context: Context,
    announceAccessibility: (String) -> Unit,
    private val onSpoken: (String) -> Unit,
    onStatus: (String) -> Unit,
) : VoiceOutput {
    private val main = Handler(Looper.getMainLooper())
    private lateinit var bridge: NavigationVoiceBridge
    private val shared =
        SharedVoiceOutput(
            context,
            announceAccessibility,
            { status ->
                onSpoken(status)
                onStatus(status)
                bridge.onInitialized()
            },
        )
    private val drain =
        object : Runnable {
            override fun run() {
                bridge.tick()
                main.postDelayed(this, 1000)
            }
        }

    init {
        // SharedVoiceOutput posts initialization callbacks onto the main looper.
        bridge = NavigationVoiceBridge(shared::offer, shared::stop, onSpoken)
        main.post(drain)
    }

    var muted: Boolean
        get() = shared.muted
        set(value) {
            shared.muted = value
            if (value) bridge.stop()
        }

    override fun speak(
        text: String,
        priority: Priority,
    ) = bridge.speak(text, priority)

    fun stop() = bridge.stop()

    fun shutdown() {
        main.removeCallbacksAndMessages(null)
        shared.close()
    }
}
