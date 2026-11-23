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

**Models are not bundled in this initial delivery.** You can explore device info, accessible controls, filters and camera preview immediately. Detection and spoken object names require a model. The status explains the missing filename. This is an expected state, not a download button or a simulated detector. See Model setup below.

## What to try

- **Model size:** small is the default. Medium may find more objects but needs more memory and compute. Nano lets you compare responsiveness. Selections persist on this phone.
- **Delegate:** CPU is the baseline. GPU and NNAPI are optional experiments. A supported API is not proof of an accelerator. Initialization failure returns to CPU with a visible explanation. An inference-time failure stops detection once and asks you to select CPU or check the model; it does not keep retrying.
- **Classes:** the accessible multi-choice dialog uses checkboxes. Apply saves the selection. Select none clears it. The exact same filtered list drives boxes and speech. Changing classes cancels queued speech.
- **Speech:** French object names use Android TextToSpeech. Each class has a five-second minimum interval. There are at most three pending class announcements, plus the one currently speaking. Mute clears the queue. Install a French TTS voice if Android reports it missing.
- **TalkBack:** automatic detection speech stops when touch exploration is enabled. Read the text summary or use **Read current detections** instead. This avoids competing with the screen reader. The status and the metrics are separate: rapidly changing FPS is not a live announcement.
- **Measurements:** processed FPS counts analyzed frames over a one-second window; it is not preview FPS. Inference latency measures `Interpreter.run`. Pipeline latency includes resize, tensor conversion, inference and postprocessing, but excludes CameraX image conversion and screen drawing. Text updates about every 1.5 seconds. No performance number is invented when models are absent.

Do not choose the production model from a single FPS reading. Follow the [GEN 3 model qualification protocol](../../../03-Vision/GEN3-model-qualification-protocol.md). Compare precision, recall, battery, heat and latency on the same phone. Small is an educational starting point, not a completed qualification decision.

## Model setup

### Why this delivery has no weights

The development host is an ARM64 Raspberry Pi running Python 3.13. The first isolated export-toolchain attempt found no compatible `tensorflow-cpu` distribution. A second approach using the ARM `tensorflow` package reached large dependencies and timed out after three minutes. We did not install a multi-gigabyte stack on the shared host. Current Ultralytics also explicitly restricts its new LiteRT exporter to Linux x86 or macOS. No export completed and no model-runtime accuracy or device acceleration has been verified.

There is a format change to understand. Current Ultralytics redirects `format="tflite"` to a newer `format="litert"` exporter. Legacy TensorFlow exports use NHWC input and normalized coordinates. Current litert-torch exports use NCHW input; end-to-end detection boxes use pixel coordinates. VisionLab supports both contracts explicitly. It does not guess units from box values.

### Reproducible starting procedure on Colab or Linux x86

Use a fresh **Python 3.11** environment, not the Pi system environment. This recipe pins a legacy exporter whose source still implements float16 TensorFlow export. The recipe is provided for execution on that environment; it was **not executed successfully on the Pi**.

```sh
python3.11 -m venv .venv
. .venv/bin/activate
python -m pip install 'ultralytics==8.4.82' 'tensorflow==2.19.0'
python tools/export_models.py --sizes s n m --destination app/src/main/assets
```

`quantize=16` selects float16 weights in this Ultralytics version. Image and detection tensors stay float32. First export only `--sizes s` if downloads or memory are limited. The exporter may install additional ONNX/TensorFlow conversion dependencies. Archive their actual versions (`python -m pip freeze`) with your measurements. If Colab already has conflicting dependencies, use a fresh runtime.

The helper downloads official pretrained COCO YOLO26 weights. It exports batch one, static 640 pixels, end-to-end detection, without additional NMS. It checks the 80-class list, tensor shapes, float I/O, and runs one smoke inference before copying each model. A successful smoke test verifies the tensor contract, not accuracy. Read the helper before running it. Network access is needed on the export computer only.

Expected Android assets:

```text
app/src/main/assets/yolo26s_float16.tflite
app/src/main/assets/yolo26m_float16.tflite
app/src/main/assets/yolo26n_float16.tflite
```

Copy the files and rebuild the app. Do not rename an INT8, segmentation, custom-class, or depth model to these names. Medium will probably exceed 25 MB; record the measured size instead of committing a large binary. Models below 25 MB can be committed within the project scope after export validation. Keep larger models as local assets or download them from your team's agreed artifact storage, then copy them to the same directory. This app never downloads models automatically.

For training and export context, see [GEN3 Colab training guide, section 7](../../../03-Vision/GEN3-colab-training-guide.md#7-export-for-the-phone-protocol-phase-2). That document describes the general workflow. The version-pinned helper here accounts for exporter API changes. For current exports on x86, consult the [official LiteRT integration](https://docs.ultralytics.com/integrations/litert/) and revalidate tensor/operator compatibility before adopting a new exporter/runtime pair.

### License

Ultralytics code and YOLO weights are offered under AGPL-3.0 or an Enterprise license. AGPL can be used for this student project, with its obligations respected. Keep license notices and review distribution/source requirements when distributing the app. Future proprietary use needs a separate license decision. See [Ultralytics licensing](https://www.ultralytics.com/license). No weights are included here.

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

There are only two modules:

- `vision` is a plain Kotlin/JVM library. It knows nothing about Android. Its tests run without a phone, network services, or an emulator.
- `app` connects Android services to that library. This boundary makes the vision logic easier to learn and later integrate with navigation. Navigation business code is not copied here.

### Suggested reading order

| File | What you learn |
| --- | --- |
| `vision/.../Detection.kt` | A Kotlin data class, normalized box coordinates, and a simple list filter. |
| `vision/.../CocoLabels.kt` | A class ID is an index in a fixed label list. English text and French speech share the same IDs. |
| `vision/.../DelegatePolicy.kt` | Enums, model filenames, and honest CPU fallback decisions. |
| `vision/.../SpeechPolicy.kt` | A small state machine: cooldowns, a bounded queue, and why a clock matters. |
| `vision/.../Geometry.kt` | Letterbox padding, inverse mapping and preview FIT_CENTER coordinates. |
| `vision/.../PixelCoordinates.kt` | Why tensor layouts and coordinate units must be explicit. |
| `vision/.../YoloParser.kt` | Float arrays become detections; invalid values are rejected; legacy exports use class-aware NMS. |
| `vision/src/test/...` | Small examples with expected answers. Start with `SelectionTest`. |
| `app/.../DeviceInfo.kt` | Reading Android capabilities without promising an NPU. |
| `app/.../YoloEngine.kt` | Resize, tensor packing, one-thread delegate lifetime, measured inference and inverse letterbox. |
| `app/.../DetectionOverlay.kt` | Drawing the filtered boxes and text with Canvas. |
| `app/.../AndroidSpeech.kt` | Connecting the tested queue to Android TTS and yielding to TalkBack. |
| `app/.../MainActivity.kt` | Controls, permissions, settings, CameraX lifecycle and text equivalents. Read this last. |
| `tools/export_models.py` | The offline preparation step that makes a phone-ready asset. |

The `...` package paths are `org/blindsystems/cyclope/vision` and `org/blindsystems/cyclope/visionlab`. Comments explain the reason for important steps. Try changing one class selection and running its tests before changing the camera code.

## Test with TalkBack

This checklist requires a real phone. It is not claimed as completed by CI.

1. Enable TalkBack in Android **Settings > Accessibility**.
2. Navigate from the title through Device info, Model size, Inference delegate, Choose object classes, Mute speech, Start camera, status, measurements, detections and Read current detections.
3. Open Device info. Read its text, then activate Close.
4. Open Choose object classes. Check and uncheck items. Verify their announced checked state. Apply. Try Select none.
5. Start the camera. Deny permission once. Check that the explanation is readable and other controls still work. Allow permission in Settings, then retry.
6. Without models, verify the missing filename and setup explanation. With a model, verify that only selected classes appear in the detection summary.
7. With TalkBack active, verify that automatic object TTS is silent. Use Read current detections to request an announcement through accessibility. Disable TalkBack and verify that French TTS uses cooldowns and mute.
8. Change model/delegate while running. Confirm the status or explicit failure/fallback. Press Stop, background the app, and rotate the phone. Verify there is no continuing camera or speech.
9. Increase system font size and display size. All controls must remain reachable by scrolling. Interactive controls have at least 48 dp height. Boxes have labels and percentages, not color-only meaning. Text summaries do not require seeing the preview.
10. On the target device, check portrait/landscape box alignment, tall/wide preview letterboxing, thermal behavior and memory. Real camera/delegate execution is not covered by JVM tests.

Automatic TalkBack detection uses Android's accessibility + touch-exploration signals, not service names. Other screen readers that do not enable touch exploration should use Mute speech. Manual accessibility and runtime checks remain a release gate for real use.

## Quality and build commands

```sh
./gradlew --no-daemon verifyVersionSingleSource ktlintCheck
./gradlew --no-daemon :vision:test :vision:jacocoTestReport
./gradlew --no-daemon :vision:dokkaHtml
./gradlew --no-daemon :app:assembleDebug
```

Gradle 8.9, AGP 8.7.3 and JDK 17 follow NavigationLab conventions. Dependencies use `gradle/libs.versions.toml`. The library is intentionally JVM-only rather than Android-dependent: no resource compiler or framework is needed to test pure logic. `cyclopeVersionName` and `cyclopeVersionCode` in `gradle.properties` are the only version source. Use `./gradlew bumpVersion -Pbump=patch` to increment them. CI checks for drift.

For local style fixes, run `./gradlew ktlintFormat` first. Then run the check in a separate command. Formatting and verification must not modify/read the same files concurrently.

ktlint is **blocking**, not report-only. We use ktlint alone for this teaching-sized project: another configurable static analyzer would add noise before Shanna knows the code. JaCoCo produces actual coverage reports; no unmeasured percentage is advertised. Dokka builds the library API documentation.

Tests cover class selection, malformed/end-to-end/legacy output parsing, NMS, letterbox/screen geometry, speech queue/cooldown/accessibility suppression and delegate policy. They are deterministic and offline after Gradle dependencies are cached. No instrumented camera/GPU tests are fabricated for CI.

GitHub Actions workflow: `.github/workflows/android-visionlab-ci.yml`. It runs blocking style, JVM tests, JaCoCo, Dokka, the version gate and **a real debug APK assembly on ubuntu-latest x86**. APK and reports are downloadable as artifacts. The Pi host cannot package an APK because Android's aapt2 binary is x86-64. Do not confuse that host limit with a failed CI build. Do not run `assembleDebug` repeatedly on ARM.

The Android app uses LiteRT's Interpreter-compatible 1.4.2 APIs. This deliberately keeps the CPU/GPU/NNAPI experiment readable. NNAPI is deprecated in Android 15; modern CompiledModel/NPU APIs are a future migration, not a promise that this teaching app controls a phone's NPU.

## Depth evaluation — documented only

The official `yolo26-depth.yaml`, `yolo26n-depth.pt` family and [Ultralytics depth task](https://docs.ultralytics.com/tasks/depth/) were confirmed upstream on 2026-11-23. It is a real task, not a made-up distance formula. Official task/export support does not establish that the provided depth model is calibrated for this phone or produces reliable metric object distances.

Depth output is a dense depth map, not the COCO `[1,N,6]` detection output. Integration needs a separate model/contract, aligned preprocessing, robust object-region depth aggregation, and calibration/validation against known distances. It also doubles part of the compute/memory work. The shared Pi export limits prevented an actual export or device evaluation in this delivery.

**Decision for V1: no depth implementation and no distance numbers.** In particular, bounding-box size is not used to invent meters. A future experiment may show relative near/far estimates or broad approximate ranges only after calibration and validation. Its screen must visibly say that monocular estimates are approximate and not a precise measurement. There is no centimeter claim and no hidden experimental switch in this app.
