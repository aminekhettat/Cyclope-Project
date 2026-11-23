#!/usr/bin/env python3
"""Run on Linux x86/Colab in the README's isolated Python environment, not in Android."""
import argparse
import json
import shutil
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--sizes", nargs="+", choices=["n", "s", "m"], default=["s"])
    parser.add_argument("--destination", type=Path, default=Path("app/src/main/assets"))
    args = parser.parse_args()
    import numpy as np
    import tensorflow as tf
    import ultralytics
    from ultralytics import YOLO

    if ultralytics.__version__ != "8.4.82":
        raise SystemExit("Use ultralytics==8.4.82 for this legacy float16 recipe. See README before upgrading.")
    args.destination.mkdir(parents=True, exist_ok=True)
    manifest = []
    for size in args.sizes:
        model = YOLO(f"yolo26{size}.pt")
        if len(model.names) != 80 or model.names[0] != "person" or model.names[79] != "toothbrush":
            raise RuntimeError("Expected the official 80-class COCO detection weights")
        path = Path(model.export(format="tflite", quantize=16, nms=False, end2end=True, imgsz=640, batch=1, device="cpu"))
        runtime = tf.lite.Interpreter(model_path=str(path), num_threads=2)
        runtime.allocate_tensors()
        ins, outs = runtime.get_input_details(), runtime.get_output_details()
        if len(ins) != 1 or len(outs) != 1:
            raise RuntimeError("Expected one image input and one detection output")
        ishape, oshape = list(map(int, ins[0]["shape"])), list(map(int, outs[0]["shape"]))
        if ishape != [1, 640, 640, 3] or ins[0]["dtype"] != np.float32 or outs[0]["dtype"] != np.float32:
            raise RuntimeError(f"Unexpected legacy float input/output contract: {ishape}, {oshape}")
        if len(oshape) != 3 or oshape[0] != 1 or oshape[2] != 6:
            raise RuntimeError(f"Expected end-to-end [1,N,6] detection output: {oshape}")
        runtime.set_tensor(ins[0]["index"], np.zeros(ishape, dtype=np.float32))
        runtime.invoke()
        output = runtime.get_tensor(outs[0]["index"])
        if not np.isfinite(output).all():
            raise RuntimeError("Non-finite values in smoke inference")
        dest = args.destination / f"yolo26{size}_float16.tflite"
        shutil.copy2(path, dest)
        import hashlib
        info = {"file": dest.name, "bytes": dest.stat().st_size, "sha256": hashlib.sha256(dest.read_bytes()).hexdigest(), "input": ishape, "output": oshape, "ultralytics": ultralytics.__version__, "tensorflow": tf.__version__}
        manifest.append(info)
        print(json.dumps(info))
        if dest.stat().st_size >= 50_000_000:
            print("Over 50 MB: keep as local asset/team artifact; do not commit this model.")
    (args.destination / "model-manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")


if __name__ == "__main__":
    main()
