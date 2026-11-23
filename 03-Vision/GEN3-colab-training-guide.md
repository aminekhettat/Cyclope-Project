# GEN 3 — Training YOLO on Google Colab (step-by-step)

**Audience:** anyone on the team who needs to train or retrain the Cyclope detection model without a local GPU. Free Colab (T4 GPU) is enough for `n`/`s` models on a few thousand images; Colab Pro helps for `m` and longer runs.

Companion document: `GEN3-model-qualification-protocol.md` (what to measure and how to decide).

---

## 0. Prerequisites

- A Google account.
- The Cyclope dataset: images + YOLO-format labels (`*.txt`, one per image) and the class list. Know where it lives (Google Drive or the repo `03-Vision/`).
- Roughly 2–4 h for a first full run (`n`/`s`, ~150 epochs on a few thousand images).

## 1. Create the notebook

1. Open https://colab.research.google.com → **New notebook**.
2. Menu **Runtime → Change runtime type** → Hardware accelerator: **T4 GPU** (or better if available). Save.
3. Verify the GPU:

```python
!nvidia-smi
```

You should see a Tesla T4 (15 GB). If not, re-select the runtime.

## 2. Install Ultralytics

```python
!pip install ultralytics
```

Record the version (it goes into the training report — reproducibility):

```python
import ultralytics
print(ultralytics.__version__)
```

`ultralytics` pulls PyTorch automatically; Colab images already ship CUDA-compatible PyTorch.

## 3. Get the dataset into the notebook

**Option A — Google Drive (recommended, survives disconnects):**

```python
from google.colab import drive
drive.mount('/content/drive')
```

Put the dataset under e.g. `MyDrive/cyclope/dataset/` with this structure:

```
dataset/
├── images/train/  ├── images/val/
└── labels/train/  └── labels/val/
```

**Option B — clone the repo / download a zip:**

```python
!git clone https://github.com/aminekhettat/Cyclope-Project.git /content/repo
# or, for a zip on Drive:
!unzip -q /content/drive/MyDrive/cyclope/dataset.zip -d /content/dataset
```

**Important for free Colab:** sessions die (idle timeout ~90 min, hard cap ~12 h). Everything in `/content` is then lost — **always write outputs to Drive** (see §6) and keep the dataset on Drive.

## 4. Declare the dataset (`data.yaml`)

Create (or reuse) `data.yaml` — paths must be valid **inside the notebook**:

```yaml
path: /content/drive/MyDrive/cyclope/dataset
train: images/train
val: images/val

names:
  0: pedestrian
  1: obstacle
  2: crossing
  # ... exact class list of the Cyclope dataset
```

(Adjust classes to the real dataset — the list above is illustrative.)

## 5. Train

Baseline example for `yolo26s` (repeat per candidate with the SAME recipe — see protocol Phase 1):

```python
from ultralytics import YOLO

model = YOLO("yolo26s.pt")   # pretrained weights download automatically

results = model.train(
    data="/content/drive/MyDrive/cyclope/dataset/data.yaml",
    imgsz=640,
    epochs=150,
    patience=30,          # early stopping
    batch=16,             # lower to 8 if out-of-memory on T4
    device=0,             # the Colab GPU
    seed=42,
    project="/content/drive/MyDrive/cyclope/runs",   # outputs saved on Drive!
    name="yolo26s-run1",
    exist_ok=True,
)
```

Notes:
- First run downloads the `.pt` weights (a few MB–tens of MB).
- `batch=16` fits a T4 for `n`/`s` at 640px; for `m` start at 8.
- **Resume after a Colab disconnect** (weights are on Drive, that's why §5 writes there):

```python
model = YOLO("/content/drive/MyDrive/cyclope/runs/yolo26s-run1/weights/last.pt")
model.train(resume=True)
```

- Watch the printed per-epoch lines: `box_loss`, `cls_loss`, `mAP50-95`. Validation runs automatically each epoch.

## 6. Evaluate (protocol Phase 1 metrics)

```python
metrics = model.val()          # default conf=0.001, standard protocol
print(metrics.box.map)         # mAP50-95
print(metrics.box.map50)       # mAP50
print(metrics.box.maps)        # per-class AP  <-- fill the comparison table
```

Also record precision/recall at the operating confidence:

```python
metrics = model.val(conf=0.35)
print(metrics.box.mp, metrics.box.mr)   # precision, recall at conf 0.35
```

Save `results.csv` and `results.png` from the run folder into the report.

## 7. Export for the phone (protocol Phase 2)

```python
model.export(format="onnx")                 # reference export
model.export(format="tflite", int8=False)   # float TFLite for Android/LiteRT
model.export(format="tflite", int8=True)    # int8 quantized (needs calib images)
```

For yolo26, also export the NMS-free end-to-end head:

```python
model.export(format="tflite", nms=False)
```

Exported files land next to the weights (on Drive). Download `best.pt`/`*.tflite` for the Android app.

## 8. Reproducibility checklist (append to the run report)

- ultralytics + torch versions, GPU type;
- exact dataset version (folder listing hash or Drive snapshot date), train/val counts per class;
- full `args.yaml` from the run folder (Colab runs save it automatically);
- seed, epochs actually run (early stop?), final metrics table;
- link/folder of the run on Drive.

## 9. Common problems

| Symptom | Fix |
|---|---|
| `CUDA out of memory` | lower `batch` (16→8→4), keep `imgsz=640` |
| Session disconnects overnight | outputs must be on Drive (`project=`), then `resume=True` |
| Very slow epochs | check `!nvidia-smi` shows the GPU and `device=0`; runtime may have fallen back to CPU |
| Dataset path errors | paths in `data.yaml` must be notebook-side (`/content/...`), not your PC's paths |
| mAP much lower than the COCO reference | expected — custom dataset with few classes; compare candidates **between themselves**, not to COCO |

## 10. What "done" looks like

For each candidate: a run folder on Drive (`runs/<name>/`), the Phase 1 metrics table filled in the qualification protocol, exported ONNX/TFLite files, and the reproducibility checklist. The model choice then follows the decision matrix in `GEN3-model-qualification-protocol.md` — measured on the target phone, not on Colab.
