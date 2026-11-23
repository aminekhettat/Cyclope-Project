package org.blindsystems.cyclope.voicekit.core

/** Stable domain-owned key plus ready-to-speak text. Keys may represent objects OR navigation steps. */
data class VoiceAnnouncement(val key: String, val text: String)
