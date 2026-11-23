#!/usr/bin/env python3
"""Verify bundled asset integrity, optional CPU inference, and optional APK packaging."""
import argparse
import hashlib
import json
import zipfile
from pathlib import Path

REQUIRED = {"yolo26n_float16.tflite", "yolo26s_float16.tflite", "yolo26m_float16.tflite"}
BUS_SHA256 = "c02019c4979c191eb739ddd944445ef408dad5679acab6fd520ef9d434bfbc63"


def digest(data):
    return hashlib.sha256(data).hexdigest()


def smoke(path, image_path):
    import numpy as np
    from ai_edge_litert.interpreter import Interpreter
    from PIL import Image

    if digest(image_path.read_bytes()) != BUS_SHA256:
        raise ValueError("Unexpected smoke image; use the checked-in upstream fixture")
    runtime = Interpreter(model_path=str(path), num_threads=2)
    runtime.allocate_tensors()
    ins, outs = runtime.get_input_details(), runtime.get_output_details()
    if len(ins) != 1 or len(outs) != 1:
        raise ValueError("Expected one image input and one detection output")
    if list(ins[0]["shape"]) != [1, 640, 640, 3] or list(outs[0]["shape"]) != [1, 300, 6]:
        raise ValueError("Expected static NHWC [1,640,640,3] -> normalized e2e [1,300,6]")
    if ins[0]["dtype"] != np.float32 or outs[0]["dtype"] != np.float32:
        raise ValueError("Image and output tensors must remain float32")
    if not any(t["dtype"] == np.float16 for t in runtime.get_tensor_details()):
        raise ValueError("Expected float16 weight tensors")
    ops = sorted({o["op_name"] for o in runtime._get_ops_details()})
    if any(o.startswith("Flex") or o == "CUSTOM" for o in ops):
        raise ValueError("Android Interpreter must not need Select TF/custom operators")
    image = Image.open(image_path).convert("RGB")
    scale = min(640 / image.width, 640 / image.height)
    width, height = round(image.width * scale), round(image.height * scale)
    letterbox = Image.new("RGB", (640, 640), (114, 114, 114))
    letterbox.paste(image.resize((width, height), Image.Resampling.BILINEAR), ((640 - width) // 2, (640 - height) // 2))
    runtime.set_tensor(ins[0]["index"], np.asarray(letterbox, dtype=np.float32)[None] / 255)
    runtime.invoke()
    rows = runtime.get_tensor(outs[0]["index"])[0]
    if not np.isfinite(rows).all():
        raise ValueError("Non-finite inference output")
    rows = rows[rows[:, 4] >= 0.35]
    classes = set(rows[:, 5].astype(int))
    if not {0, 5}.issubset(classes):
        raise ValueError(f"Expected person (0) and bus (5), got {classes}")
    if (rows[:, :4] < -0.1).any() or (rows[:, :4] > 1.1).any():
        raise ValueError("Expected normalized xyxy, not pixel coordinates")
    if (rows[:, 2] <= rows[:, 0]).any() or (rows[:, 3] <= rows[:, 1]).any():
        raise ValueError("Expected positive-area xyxy boxes")
    if (rows[:, 4] > 1).any() or not np.allclose(rows[:, 5], np.round(rows[:, 5])):
        raise ValueError("Invalid confidence/class IDs")
    return {"input": ins[0]["shape"].tolist(), "output": outs[0]["shape"].tolist(), "operators": ops,
            "fixture_sha256": BUS_SHA256, "detections_above_0_35": rows.tolist()}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--assets", type=Path, default=Path("app/src/main/assets"))
    parser.add_argument("--smoke-image", type=Path)
    parser.add_argument("--apk", type=Path)
    parser.add_argument("--report", type=Path)
    args = parser.parse_args()
    manifest = json.loads((args.assets / "model-manifest.json").read_text())
    if {m["file"] for m in manifest} != REQUIRED:
        raise ValueError("Manifest must contain exactly the bundled nano, small and medium models")
    results = []
    for model in manifest:
        path = args.assets / model["file"]
        data = path.read_bytes()
        if len(data) != model["bytes"] or digest(data) != model["sha256"]:
            raise ValueError(f"Asset integrity mismatch: {path}")
        if len(data) >= 50_000_000 or data[4:8] != b"TFL3":
            raise ValueError(f"Not a supported <50 MB TFLite model: {path}")
        result = {"file": path.name, "bytes": len(data), "sha256": digest(data)}
        if args.smoke_image:
            result["smoke"] = smoke(path, args.smoke_image)
        if args.apk:
            with zipfile.ZipFile(args.apk) as apk:
                entry = apk.getinfo(f"assets/{path.name}")
                if entry.compress_type != zipfile.ZIP_STORED or apk.read(entry) != data:
                    raise ValueError("APK must contain byte-identical uncompressed mmap-ready models")
                result["apk_entry"] = entry.filename
                result["apk_uncompressed"] = True
        results.append(result)
    encoded = json.dumps(results, indent=2) + "\n"
    if args.report:
        args.report.write_text(encoded)
    print(encoded)


if __name__ == "__main__":
    main()
