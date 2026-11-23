# Shared voicekit and TalkBack integration

## One module, two temporary copies

VisionLab and NavigationLab now use the same `org.blindsystems.cyclope.voicekit` Android library.
All twelve tracked files, including its README, are byte-identical to VisionLab commit
`a517cd1f939b147612f1b4d7460e39f63756866d`.
The canonical repository location will be decided during final application integration.
Keep these copies synchronized until then. Do not fork their packages or add navigation classes inside voicekit.
We keep these host-specific notes outside voicekit to preserve the exact copy.
`docs/voicekit-provenance.json` records source SHA and every file hash.
`python3 tools/verify_shared_voicekit.py` checks them in CI without network access.

## What changed for Taoyu

- `:app` now depends on `:navigation` and `:voicekit`.
- The existing `VoiceOutput.speak(text, priority)` interface is unchanged.
- `AndroidVoiceOutput` delegates Android TTS, TalkBack detection, mute, cooldown and FIFO to voicekit.
- `NavigationVoiceBridge` only converts navigation text/priority into generic announcements.
- `WARNING` still interrupts obsolete custom speech before submitting the warning.
- The bounded startup buffer keeps at most three distinct messages while Android initializes TTS.
- The shared queue also holds at most three messages and suppresses duplicate keys within five seconds.
- A one-second host drain tick lets a pending polite announcement be published even without another navigation event.
- No route algorithm, network request, positioning recorder, grammar or fuzzy logic has changed.
- The module's STT interface remains reserved and unimplemented. No microphone permission is added.

## Testing with TalkBack

1. Open this project in Android Studio and run `app` on a phone.
2. Enable Android TalkBack in Accessibility settings. Do not start walking to test the UI.
3. Swipe from the top. The first control skips positioning and moves input and accessibility focus to the address.
4. Without skipping, the order follows the layout: positioning, destination search, glasses controls, guidance controls, history.
5. Check each control label, including Short (next candidate), Long (choose) and Double (reject all).
6. Search an address. Search/request/error states use a short polite live region.
7. Guidance uses a different polite region. Raw custom TTS stops when touch exploration is enabled.
8. Automatic guidance is paced by shared voicekit (up to ten seconds between batches); warnings interrupt the custom queue.
9. Use Read latest guidance for an explicit accessibility announcement. It bypasses automatic pacing at the user's request.
10. Mute suppresses custom voice output and custom automatic guidance events; TalkBack can still read controls and status.
11. Position coordinates and the full history are readable manually but do not announce every one-second update.
12. Turn TalkBack off. Confirm French Android TTS works when a French voice is installed. Missing voices leave readable text.
13. Check large fonts, landscape, narrow screens, focus visibility and every target's usability.

`contentDescription`, native widget roles, text labels and minimum 48dp targets are explicit.
Dark text on a white surface and a fixed dark-blue accent avoid color-only meaning.
Native headings are exposed through shared helpers, retaining AccessibilityNodeInfo roles/actions.
Only an explicit read/skip action moves or requests accessibility focus; background updates do not.
The native polite region emits the accessibility event; do not also call announceForAccessibility for the same automatic message.

## Checks and their limits

```sh
python3 tools/verify_shared_voicekit.py
python3 tools/verify_accessibility_contract.py
./gradlew --no-daemon verifyVersionSingleSource :voicekit:verifySharedBoundary
./gradlew --no-daemon :navigation:testDebugUnitTest :navigation:jacocoTestReport :navigation:jacocoTestCoverageVerification :voicekit:testDebugUnitTest :voicekit:jacocoTestReport :voicekit:jacocoTestCoverageVerification :app:testDebugUnitTest
./gradlew --no-daemon :navigation:ktlintCheck :voicekit:ktlintCheck :app:ktlintCheck :app:lintDebug :voicekit:lintDebug :navigation:dokkaHtml :voicekit:dokkaHtml :app:assembleDebug
```

The existing whole-navigation 80% gates (instruction/line/branch/method) are unchanged.
Voicekit has the same four 80% gates over all pure core classes; Android adapters are not claimed as JVM-covered.
The copied voicekit tests and host bridge tests exercise routing/queues deterministically without a phone.
Navigation ktlint retains its existing report-only policy; new host adapters and voicekit use blocking ktlint.
Legacy MainActivity and the original smoke test are not reformatted for this integration.
The existing non-blocking emulator job also checks native skip-link input focus and polite region attributes.
That is not proof of TalkBack speech, real GPS, a real phone or physical accessibility usability.
Full Google Accessibility Test Framework integration remains future work, separate from Android Lint checks.
On ARM64 Raspberry Pi, SDK aapt2 is x86-only; actual APK packaging is checked by the Ubuntu CI job.

Official Android guidance consulted/referenced:
- https://developer.android.com/guide/topics/ui/accessibility/apps
- https://developer.android.com/guide/topics/ui/accessibility/testing
- https://developer.android.com/reference/android/view/View#announceForAccessibility(java.lang.CharSequence)
- https://developer.android.com/reference/android/view/accessibility/AccessibilityManager#isTouchExplorationEnabled()
