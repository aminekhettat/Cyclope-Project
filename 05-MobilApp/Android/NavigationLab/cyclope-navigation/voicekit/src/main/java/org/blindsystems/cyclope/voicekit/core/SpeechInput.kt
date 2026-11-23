package org.blindsystems.cyclope.voicekit.core

/** Reserved for the shared navigation integration. NO implementation or microphone use in V1. */
interface SpeechInput {
    /**
     * Start only after explicit user action. A future implementation asks the host for permission
     * if needed; denied permission must report PERMISSION_DENIED without opening the microphone.
     * Deliver partial/final text and errors to [listener] on the host UI thread.
     */
    fun startListening(
        permission: MicrophonePermissionHost,
        listener: SpeechInputListener,
    )

    /** Cancel recognition and release the microphone. Safe to call repeatedly. */
    fun stopListening()
}

/** The host Activity owns any future permission dialog. This contract requests nothing by itself. */
interface MicrophonePermissionHost {
    val isGranted: Boolean

    fun requestPermission(result: (Boolean) -> Unit)
}

interface SpeechInputListener {
    fun onText(
        text: String,
        isFinal: Boolean,
    )

    fun onError(error: SpeechInputError)
}

enum class SpeechInputError { PERMISSION_DENIED, UNAVAILABLE, CANCELLED, RECOGNITION_FAILED }
