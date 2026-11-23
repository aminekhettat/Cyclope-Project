# Shared voicekit
1. Vision and navigation both need one consistent, accessible voice experience.
2. This real Android library has no dependency on the app or on any detection/model class.
3. Hosts submit VoiceAnnouncement(key, text); only the host decides what an object or route step means.
4. AnnouncementQueue owns bounded FIFO, mute and per-key cooldowns; AnnouncementRoute and UpdateGate pace delivery.
5. AndroidVoiceOutput stops raw TTS for touch exploration and routes messages to the host accessibility region.
6. VoiceAccessibility provides polite live regions, explicit announcements, labels, focus exclusions and native dialog helpers.
7. NavigationLab can include this unchanged module, provide the same Gradle catalog/plugin aliases, and depend on project(":voicekit") with navigation keys/text.
8. SpeechInput, SpeechInputListener and MicrophonePermissionHost are reserved for the shared navigation integration: no STT implementation or microphone permission in V1; the manifest provides only the TTS service query.
9. verifySharedBoundary rejects vision/app coupling; JVM tests cover all pure core logic, while Android/TalkBack behavior still needs device/ATF validation.
