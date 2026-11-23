# GEN 3 — YOLO Model Qualification Protocol

**Purpose:** choose the production object-detection model for Cyclope GEN 3 **based on measurements, not assumptions**. This protocol must be completed before the final model choice is frozen into the Android app.

**Context (GEN 3 architecture):**
- The ESP32-S3 streams ~5 images/second (JPEG) over Wi-Fi to the user's phone; the phone is the Wi-Fi access point and stays close to the device.
- All detection runs **on the phone** (nothing runs YOLO on the ESP32-S3).
- Target input size: 640×640 (standard for YOLO11 and YOLO26); the phone resizes/crops frames before inference.
- An existing custom dataset is already trained with YOLO11 (`03-Vision/runs/train/custom_yolov11_training`) — it is the baseline.

**Candidates:** `yolo11n` (baseline), `yolo26n`, `yolo26s`, `yolo26m`.
**Excluded:** YOLO12 (community release; Ultralytics explicitly recommends YOLO11 or YOLO26 for production: training instability, high memory use, slow CPU inference). YOLO27 is announced but not released.

Reference figures from Ultralytics documentation (COCO val, 640px, verified 2026-11-23):

| Model | mAP50-95 | Params (M) | CPU ONNX (ms) |
|---|---|---|---|
| yolo11n | 39.5 | 2.6 | 56.1 |
| yolo26n | 40.9 | 2.4 | 38.9 (up to 43% faster than 11n) |
| yolo26s | 48.6 | 9.5 | 87.2 |
| yolo26m | 53.1 | 20.4 | 220.0 |

Phone accelerators (NPU/GPU via LiteRT/NNAPI/CoreML) are much faster than these CPU figures; the budget is 5 fps = **200 ms per frame**, so even `m` is feasible. The real constraints are **battery drain and heat**, not raw speed.

---

## Phase 1 — Accuracy qualification (same dataset, same protocol)

1. **Freeze the dataset**: record the exact commit/zip of images + labels, the train/val split and the class list. No change during the comparison.
2. **Check label quality first** (half a day, highest return on effort): consistent bounding boxes, no missing objects in the safety classes (obstacles, pedestrians, crossings), class balance summary. A well-trained nano beats a badly trained medium.
3. **Retrain every candidate with an identical recipe**: `imgsz=640`, same epochs (start 150, early-stop patience 30), same augmentations, same batch size (as GPU allows), same seed. Document the ultralytics version.
4. **Metrics to record per candidate** (from `model.val()` with default conf=0.001):
   - mAP50-95 overall and mAP50;
   - **per-class AP**, especially small/distant classes (obstacles, pedestrians, crossings, signals);
   - **recall at the operating confidence** (e.g. conf=0.35) — for a safety aid, missing an obstacle matters more than a false positive;
   - **false positives per minute** of real footage at the operating confidence (usability: constant wrong warnings destroy trust);
   - training curves (loss, PR curve) archived in `03-Vision/runs/`.
5. **Gate:** a candidate must beat the yolo11n baseline by ≥1.0 mAP50-95 on our dataset, or match it with better on-device performance (Phase 2), to stay in the race.

## Phase 2 — On-device performance (the exact target phone)

Use **one reference phone** (state make/model/OS) — results from other devices are indicative only.

1. **Export** each surviving candidate: `model.export(format="onnx")` then TFLite/LiteRT for Android; test both float16 and int8-quantized variants. For yolo26 also test the NMS-free end-to-end export (`nms=False`) — simpler deployment, lower latency.
2. **Latency**: median and p95 inference time per frame at 640×640, on CPU / GPU / NPU delegates, warm (after 100 frames). **Gate: p95 ≤ 66 ms** (3× margin under the 200 ms budget) on at least one delegate.
3. **Battery**: start at 100%, screen off, Wi-Fi hotspot ON (real usage), run continuous detection at 5 fps for 30 minutes; record % consumed per candidate. Normalize to "%/hour".
4. **Thermal**: run 30 minutes continuous; record device temperature feel/throttle status and whether latency degraded (compare first-100-frames p95 vs last-100-frames p95). Any throttling >20% disqualifies the configuration.
5. **Memory**: peak app RAM with the model loaded.

## Phase 3 — End-to-end system validation

1. Feed the **real camera stream** (ESP32-S3 + the qualified OV3660 module, including IR/night frames once the NoIR module is validated) through the full pipeline; measure detection quality on real captures: motion blur, low light, JPEG artifacts, night/IR.
2. Validate the resize path (camera native resolution → 640×640): compare letterbox vs center-crop on detection quality; pick one and document it.
3. Measure **full-loop latency**: frame captured → voice guidance started. Target to be defined with the PO (suggestion: ≤500 ms).
4. Night/IR imagery is a separate accuracy check: a model trained only on daylight frames may collapse on IR frames — include IR frames in the dataset or accept a documented night-mode accuracy figure.

## Decision matrix (to fill after Phases 1–3)

| Criterion | Weight | yolo11n | yolo26n | yolo26s | yolo26m |
|---|---|---|---|---|---|
| Accuracy (mAP + per-class recall) | 40% | | | | |
| Battery (%/hour on target phone) | 25% | | | | |
| Latency margin (p95 vs 66 ms gate) | 20% | | | | |
| Thermal behavior | 15% | | | | |

The product owner makes the final call from the filled matrix. **Recommended starting point: yolo26s**; nano only if battery/thermal measurements force it; medium if the target phone is recent (2022+) and battery results allow.

## Notes

- **License**: YOLO11/YOLO26 are AGPL-3.0 — fine for this student project; a future commercial product would need an Ultralytics Enterprise license or an alternative. Flag to the PO, not blocking now.
- YOLO26 relevant features for us: NMS-free end-to-end inference (simpler mobile export), STAL small-object label assignment (helps distant obstacles/signals), DFL-free head (lighter export).
- Whatever wins: archive trained weights, data.yaml, training args and the filled matrix in `03-Vision/` so the choice is reproducible.

**Effort estimate:** Phase 1 ≈ 1 day (training runs on Colab — see `GEN3-colab-training-guide.md`), Phase 2 ≈ half a day, Phase 3 ≈ half a day after the camera module is qualified.
