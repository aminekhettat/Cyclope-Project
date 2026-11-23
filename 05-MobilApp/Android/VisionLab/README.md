# Cyclope VisionLab

A small Android learning app for Shanna. You can explore object detection without learning navigation at the same time. The app uses **the phone back camera**, not the ESP32 camera. All inference is local. It does not record or upload images. There is no Internet permission.

**This is a learning tool. It is not an obstacle-avoidance or navigation safety aid.** Confidence is a model score, not a guarantee that an object exists.

## Start here

1. Install Android Studio Ladybug or newer. Use JDK 17 for Gradle.
2. Switch the repository to `CYCLOPE_GEN3_AndroidVision`.
3. In Android Studio, choose **Open** and select this `VisionLab` folder. Open the folder containing `settings.gradle.kts`, not the whole repository.
4. Let Gradle sync. Install Android SDK platform 35 when asked.
5. Connect an Android 8.0/API 26 or newer phone with USB debugging enabled. Choose that device, then **Run app**.
6. Open **Device info** first. Read what Android reports, and what it cannot promise.
7. Choose **small**, **CPU**, and the object classes you want. The initial classes are person, bicycle, car, traffic light, and stop sign.
8. Press **Start camera**, then allow Camera permission. No microphone permission is needed for speech.
9. Press **Stop camera** when finished. Leaving the activity also stops the camera and speech.

**YOLO26 nano and small float16 models are bundled.** Small is the default; nano is also available immediately after installation, with no model download. Allow the camera permission and press Start camera to use detection. Medium remains an optional export exercise: selecting it explains its missing filename and keeps the camera preview available. No detector output is simulated. Real-phone accuracy, acceleration and TalkBack still need the device walkthrough below.

## What to try

- **Model size:** small is the default. Medium may find more objects but needs more memory and compute. Nano lets you compare responsiveness. Selections persist on this phone.
- **Delegate:** CPU is the baseline. GPU and NNAPI are optional experiments. A supported API is not proof of an accelerator. Initialization failure returns to CPU with a visible explanation. An inference-time failure stops detection once and asks you to select CPU or check the model; it does not keep retrying.
- **Classes:** the accessible multi-choice dialog uses checkboxes. Apply saves the selection. Select none clears it. The exact same filtered list drives boxes and speech. Changing classes cancels queued speech.
- **Speech:** French object names use Android TextToSpeech. Each class has a five-second minimum interval. There are at most three pending class announcements, plus the one currently speaking. Mute clears the queue. Install a French TTS voice if Android reports it missing.
- **TalkBack:** touch exploration routes object announcements to Android accessibility events from a **POLITE live region**, instead of raw TextToSpeech. The service owns its speech queue. Object messages are batched (up to three) no more often than every ten seconds. FPS/latency has a separate POLITE region, updated at most every fifteen seconds while TalkBack is active. Without TalkBack, text measurements update about every 1.5 seconds. Read current detections requests the latest full summary immediately. Mute suppresses automatic object messages on both routes; measurements remain accessible.
- **Measurements:** processed FPS counts analyzed frames over a one-second window; it is not preview FPS. Inference latency measures `Interpreter.run`. Pipeline latency includes resize, tensor conversion, inference and postprocessing, but excludes CameraX image conversion and screen drawing. Without touch exploration, text updates about every 1.5 seconds; TalkBack updates are deliberately paced as described above. No performance number is invented when models are absent.

Do not choose the production model from a single FPS reading. Follow the [GEN 3 model qualification protocol](../../../03-Vision/GEN3-model-qualification-protocol.md). Compare precision, recall, battery, heat and latency on the same phone. Small is an educational starting point, not a completed qualification decision.

## Model setup

### Bundled models and verified contract

The APK includes the official pretrained **COCO 80-class YOLO26 nano and small** weights, exported to TFLite float16 on GitHub Actions **Ubuntu x86 / Python 3.11**, not on the ARM development Pi. The exact sizes, source-weight SHA-256, export recipe and output SHA-256 are recorded in `app/src/main/assets/model-manifest.json`.

| Bundled asset | Bytes | Size (decimal MB) |
| --- | ---: | ---: |
| `yolo26n_float16.tflite` | 5,448,366 | 5.45 |
| `yolo26s_float16.tflite` | 19,629,058 | 19.63 |

Export provenance: [manual run 45138993009](https://github.com/aminekhettat/Cyclope-Project/actions/runs/45138993009), commit `abef139`. The complete resolved toolchain is retained in `tools/export-environment-45138993009.txt`.

Both exports use **NHWC float32 `[1,640,640,3]` input in [0,1]**, float16 weights and **float32 `[1,300,6]` output**: normalized `x1,y1,x2,y2,score,classId`, end-to-end (no extra NMS). This is the existing `YoloEngine` / `YoloParser` contract; no model-name or parser workaround is needed. Normal CI verifies hashes, float16 tensors, the contract, built-in CPU operators and real inference on the checked-in official bus/person example. It also checks that the assembled APK contains byte-identical **uncompressed** assets, required by the memory-mapped loader. This is not a real-phone camera, speed or accuracy qualification.

Medium is **not bundled**. Selecting it uses the existing missing-model explanation. Export it on Colab as Shanna's exercise and rebuild if needed. Never rename depth, segmentation, INT8 or custom-class weights to these filenames.

### Regenerate manually on GitHub Actions

The dedicated `.github/workflows/visionlab-export-models.yml` has **only `workflow_dispatch`**, never push/schedule triggers. The shared composite action pins the CPU toolchain, exports nano/small, smoke-tests them and publishes `visionlab-yolo26-float16`, including `model-manifest.json` and `export-environment.txt`. This does not commit or publish new weights automatically.

GitHub cannot dispatch a newly added workflow that is absent from the default branch. On this teaching branch, use the already registered VisionLab CI workflow's **manual export-only input** (same composite action):

```sh
gh workflow run 427496876 --ref CYCLOPE_GEN3_AndroidVision -f export_models=true
# Find the workflow_dispatch run ID, then download its artifact:
gh run list --branch CYCLOPE_GEN3_AndroidVision --event workflow_dispatch
gh run download RUN_ID --name visionlab-yolo26-float16 --dir /tmp/visionlab-export
```

Copy the two `.tflite` files and `model-manifest.json` from the artifact's `exported-models/` directory to `app/src/main/assets/`, review checksums and provenance, and rebuild. Export-only runs deliberately skip APK/quality jobs; they are **not** APK acceptance evidence. Normal branch pushes retain all quality gates and the real APK build. If the standalone workflow is later registered on the default branch, it can be dispatched directly by filename; changing that branch is outside this delivery.

### Reproduce on Colab or Linux x86

Use fresh **Python 3.11**, not the Pi Python 3.13 system environment. The previously attempted local ARM export did not complete. Current Ultralytics changed its exporter, so keep this tested legacy conversion stack pinned (complete transitive versions are in the export artifact):

```sh
python3.11 -m venv .venv
. .venv/bin/activate
python -m pip install torch==2.8.0 torchvision==0.23.0 --index-url https://download.pytorch.org/whl/cpu
python -m pip install 'ultralytics==8.4.82' 'tensorflow==2.19.0' 'tf-keras==2.19.0' 'onnx2tf==1.28.8' 'onnx==1.17.0' 'onnxruntime==1.23.2' 'onnxslim==0.1.82' 'sng4onnx==1.0.4' 'onnx-graphsurgeon==0.5.8' 'ai-edge-litert==1.4.0' 'protobuf>=5,<6'
python tools/export_models.py --sizes n s --destination app/src/main/assets
python tools/verify_models.py --smoke-image tools/testdata/bus.jpg
# Optional medium exercise (not part of the bundled pair):
python tools/export_models.py --sizes m --destination /tmp/visionlab-medium
```

The helper uses `format="tflite", quantize=16, end2end=True, nms=False, imgsz=640, batch=1, device="cpu"`. `quantize=16` is the float16-weight option in this version; image/output tensors remain float32. Official source URLs are `https://github.com/ultralytics/assets/releases/download/v8.4.0/yolo26n.pt` and `yolo26s.pt`. The helper checks the COCO labels and runs a finite-output smoke inference before copying. Keep medium's separate manifest for provenance; do not replace the bundled pair's manifest with a medium-only one. Models at or above **50,000,000 bytes** must stay team artifacts, not Git blobs.

Expected assets are `yolo26n_float16.tflite`, `yolo26s_float16.tflite` and optionally `yolo26m_float16.tflite`. The app never downloads weights. See the [GEN3 Colab training guide](../../../03-Vision/GEN3-colab-training-guide.md#7-export-for-the-phone-protocol-phase-2) for training context. Before upgrading to the [current LiteRT exporter](https://docs.ultralytics.com/integrations/litert/), revalidate operators, runtime and tensors: newer NCHW exports use pixel coordinates, unlike these normalized NHWC exports.

### License

Ultralytics code and YOLO weights are offered under AGPL-3.0 or an Enterprise license. AGPL can be used for this student project, with its obligations respected. Keep license notices and review distribution/source requirements when distributing the app. Future proprietary use needs a separate license decision. See [Ultralytics licensing](https://www.ultralytics.com/license). The bundled nano/small weights are AGPL-3.0 licensed; their upstream license is retained alongside them in the assets folder.

## Architecture: follow one frame

```text
Phone back camera (CameraX)
  -> RGBA image, viewport crop and rotation
  -> letterbox resize, RGB float values in [0,1]
  -> local LiteRT Interpreter (CPU / GPU / NNAPI)
  -> output contract validation and YOLO parsing
  -> inverse letterbox -> class filter
  -> custom overlay AND accessible text summary
  -> bounded speech queue (unless muted or TalkBack active)
```

There are three modules:

- `vision` is a plain Kotlin/JVM library for detection, labels, parsing and geometry. It knows nothing about Android or voice services. Its tests run without a phone, network services, or an emulator.
- `voicekit` is a real Android library shared by future vision/navigation hosts. It owns accessibility/TTS integration and a pure, generic queue/router/pacer. It accepts only `VoiceAnnouncement(key, text)`, never a detection or YOLO class. Read its [ten-line introduction](voicekit/README.md).
- `app` connects CameraX and model inference to `vision`, and maps filtered detections to generic `voicekit` messages. Navigation business code is not copied here.

Dependency direction: `app -> vision` and `app -> voicekit`. There is **no** `voicekit -> vision/app` dependency. CI enforces this with `:voicekit:verifySharedBoundary`, checking imports, Gradle project dependencies and the absence of microphone permission in the library manifest. NavigationLab may later include the unchanged module, provide the same Gradle catalog/plugin aliases (Android library, Kotlin Android, ktlint, Dokka, AndroidX core and JUnit), and send keys such as `route-step-42`; no change is made to Taoyu's branch in this task.

In the future combined application, create **one AndroidVoiceOutput owner** for vision and navigation. Submit namespaced keys to that shared queue. Do not start two competing TTS engines just because two features use the module.

STT is a **contract only**: `SpeechInput.startListening/stopListening`, text/error callbacks and `MicrophonePermissionHost` describe the future permission handoff. It is reserved for the shared navigation integration. No speech recognizer is instantiated, no microphone permission is declared or requested, and no audio dependency is added in V1.

### Suggested reading order

| File | What you learn |
| --- | --- |
| `vision/.../Detection.kt` | A Kotlin data class, normalized box coordinates, and a simple list filter. |
| `vision/.../CocoLabels.kt` | A class ID is an index in a fixed label list. English text and French speech share the same IDs. |
| `vision/.../DelegatePolicy.kt` | Enums, model filenames, and honest CPU fallback decisions. |
| `voicekit/.../core/AnnouncementQueue.kt` | A small state machine: cooldowns, a bounded queue, and why a clock matters. |
| `voicekit/.../core/AnnouncementRouting.kt` | Routing to native TTS or the accessibility service, plus monotonic live-region pacing. |
| `vision/.../Geometry.kt` | Letterbox padding, inverse mapping and preview FIT_CENTER coordinates. |
| `vision/.../PixelCoordinates.kt` | Why tensor layouts and coordinate units must be explicit. |
| `vision/.../YoloParser.kt` | Float arrays become detections; invalid values are rejected; legacy exports use class-aware NMS. |
| `vision/src/test/...` | Small examples with expected answers. Start with `SelectionTest`. |
| `app/.../DeviceInfo.kt` | Reading Android capabilities without promising an NPU. |
| `app/.../YoloEngine.kt` | Resize, tensor packing, one-thread delegate lifetime, measured inference and inverse letterbox. |
| `app/.../DetectionOverlay.kt` | Drawing the filtered boxes and text with Canvas. |
| `voicekit/.../android/AndroidVoiceOutput.kt` | Connecting generic announcements to Android TTS or TalkBack, without any vision import. |
| `voicekit/.../android/VoiceAccessibility.kt` | Shared native labels, polite regions, explicit announcements and accessible dialog helpers. |
| `voicekit/.../core/SpeechInput.kt` | The reserved STT contract. There is no recognizer or microphone use in V1. |
| `app/.../MainActivity.kt` | Controls, permissions, settings, CameraX lifecycle and text equivalents. Read this last. |
| `tools/export_models.py` | The offline preparation step that makes a phone-ready asset. |

The `...` package paths are `org/blindsystems/cyclope/vision`, `org/blindsystems/cyclope/visionlab` and `org/blindsystems/cyclope/voicekit`. Comments explain the reason for important steps. Try changing one class selection and running its tests before changing the camera code.

## Testing with TalkBack

This checklist requires a real phone. It is not claimed as completed by CI.

1. Enable TalkBack in Android **Settings > Accessibility**.
2. Navigate from the title through **Device info -> Choose object classes -> Start camera -> Model size -> Inference delegate -> Mute speech**, then status, measurements, detections and Read current detections. This is the view order, not an artificial focus jump. The preview/overlay do not capture focus.
3. Open Device info. Read its text, then activate Close.
4. Open Choose object classes. Check and uncheck items. Verify their announced checked state. Apply. Try Select none.
5. Start the camera. Deny permission once. Check that the explanation is readable and other controls still work. Allow permission in Settings, then retry.
6. Select small and nano in turn: each bundled model should detect without an additional download. Verify that only selected classes appear in the detection summary. Select medium to verify the missing filename and setup explanation.
7. With TalkBack active, verify that **raw app TTS is silent**, but object messages reach TalkBack through the polite detection region. Updates must not occur on every camera frame: object batches are at least ten seconds apart, metrics at least fifteen seconds apart. Use Read current detections for an immediate, user-requested announcement. Disable TalkBack and verify normal French TTS, per-class cooldowns and mute. Enable TalkBack while TTS is already speaking: raw speech must stop before accessibility routing starts. Test accessibility routing even if no French TTS voice is installed.
8. Change model/delegate while running. Confirm the status or explicit failure/fallback. Press Stop, background the app, and rotate the phone. Verify there is no continuing camera or speech.
9. Increase system font size and display size. All controls must remain reachable by scrolling. Interactive controls have at least 48 dp height. Boxes have labels and percentages, not color-only meaning. Text summaries do not require seeing the preview.
10. On the target device, check portrait/landscape box alignment, tall/wide preview letterboxing, thermal behavior and memory. Real camera/delegate execution is not covered by JVM tests.

Automatic TalkBack detection uses Android's accessibility + touch-exploration signals, not service names. Other screen readers that do not enable touch exploration should use Mute speech. Standard Button, Spinner and CheckedTextView widgets keep their platform AccessibilityNodeInfo roles and checked states. Explicit descriptions name interactive controls and dialog rows; text is not hidden by replacing the native node tree. Static information remains readable. Decorative preview/overlay views use importantForAccessibility=NO/NO_HIDE_DESCENDANTS.

The detection callback changes the POLITE region's text. Android emits the content-change AccessibilityEvent; we do **not** also call announceForAccessibility for that same update. The manual Read current detections button uses announceForAccessibility only for its explicit request. This avoids duplicate announcements. Android API 36 deprecates generic announceForAccessibility in favor of semantic/live-region updates; the automatic path already follows that guidance.

Official references consulted:

- [Make apps more accessible](https://developer.android.com/guide/topics/ui/accessibility/apps): meaningful labels, native semantics, at least 48 dp targets.
- [Test your app's accessibility](https://developer.android.com/guide/topics/ui/accessibility/testing): manual tests and Accessibility Test Framework integration.
- [View live regions and announcement APIs](https://developer.android.com/reference/android/view/View): POLITE updates, importantForAccessibility and accessibility events.

**CI scope:** Android Lint (`:app:lintDebug`) adds static Android/accessibility checks. The pure JVM tests cover route selection, mute precedence, queue behavior and update pacing. They are not the Google Accessibility Test Framework (ATF). Full ATF hierarchy checks are a documented next step, using Espresso AccessibilityChecks on a controlled UI fixture/device or Robolectric; simply adding an unused dependency named checks would not prove accessibility. No emulator/camera-dependent ATF test is claimed in this delivery. Add that harness after the phone/TalkBack walkthrough, without creating CI tests that require unavailable camera or GPU hardware. Manual accessibility and runtime checks remain a release gate for real use.

## Quality and build commands

```sh
./gradlew --no-daemon verifyVersionSingleSource ktlintCheck
./gradlew --no-daemon :vision:test :vision:jacocoTestReport :vision:jacocoTestCoverageVerification
./gradlew --no-daemon :voicekit:verifySharedBoundary :voicekit:testDebugUnitTest :voicekit:jacocoTestReport :voicekit:jacocoTestCoverageVerification
./gradlew --no-daemon :vision:dokkaHtml :voicekit:dokkaHtml
./gradlew --no-daemon :app:lintDebug :voicekit:lintDebug :app:assembleDebug
```

Gradle 8.9, AGP 8.7.3 and JDK 17 follow NavigationLab conventions. Dependencies use `gradle/libs.versions.toml`. The library is intentionally JVM-only rather than Android-dependent: no resource compiler or framework is needed to test pure logic. `cyclopeVersionName` and `cyclopeVersionCode` in `gradle.properties` are the only version source. Use `./gradlew bumpVersion -Pbump=patch` to increment them. CI checks for drift.

For local style fixes, run `./gradlew ktlintFormat` first. Then run the check in a separate command. Formatting and verification must not modify/read the same files concurrently.

ktlint is **blocking**, not report-only. We use ktlint alone for this teaching-sized project: another configurable static analyzer would add noise before Shanna knows the code. JaCoCo produces actual coverage reports and enforces at least **80% instruction, line, branch and method coverage**, matching the NavigationLab convention. The `vision` gate covers its whole JVM module. The `voicekit` gate covers **all pure core classes**; Android framework adapters/helpers are explicitly outside that JVM-only coverage gate and still require device/ATF validation. Their exclusion is not a claim of runtime verification. Dokka builds both libraries' API documentation.

Tests cover class selection, malformed/end-to-end/legacy output parsing, NMS, letterbox/screen geometry, speech queue/cooldown/accessibility suppression and delegate policy. They are deterministic and offline after Gradle dependencies are cached. No instrumented camera/GPU tests are fabricated for CI.

GitHub Actions workflow: `.github/workflows/android-visionlab-ci.yml`. It runs blocking style, Android Lint, JVM tests for both libraries, their four-metric 80% pure-logic coverage gates, shared-module boundary checks, Dokka, the version gate and **a real debug APK assembly on ubuntu-latest x86**. APK and reports are downloadable as artifacts. The Pi host cannot package an APK because Android's aapt2 binary is x86-64. Do not confuse that host limit with a failed CI build. Do not run `assembleDebug` repeatedly on ARM.

The Android app uses LiteRT's Interpreter-compatible 1.4.2 APIs. This deliberately keeps the CPU/GPU/NNAPI experiment readable. NNAPI is deprecated in Android 15; modern CompiledModel/NPU APIs are a future migration, not a promise that this teaching app controls a phone's NPU.

## Depth evaluation — documented only

The official `yolo26-depth.yaml`, `yolo26n-depth.pt` family and [Ultralytics depth task](https://docs.ultralytics.com/tasks/depth/) were confirmed upstream on 2026-11-23. It is a real task, not a made-up distance formula. Official task/export support does not establish that the provided depth model is calibrated for this phone or produces reliable metric object distances.

Depth output is a dense depth map, not the COCO `[1,N,6]` detection output. Integration needs a separate model/contract, aligned preprocessing, robust object-region depth aggregation, and calibration/validation against known distances. It also doubles part of the compute/memory work. Detection model export on x86 does not validate the separate depth model; no depth export or device evaluation is claimed.

**Decision for V1: no depth implementation and no distance numbers.** In particular, bounding-box size is not used to invent meters. A future experiment may show relative near/far estimates or broad approximate ranges only after calibration and validation. Its screen must visibly say that monocular estimates are approximate and not a precise measurement. There is no centimeter claim and no hidden experimental switch in this app.
