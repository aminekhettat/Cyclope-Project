# Bundled model assets

- `yolo26s_float16.tflite`: official COCO YOLO26 small, default.
- `yolo26n_float16.tflite`: official COCO YOLO26 nano.
- `model-manifest.json`: export provenance, exact bytes and SHA-256 hashes.
- `ULTRALYTICS-LICENSE.txt`: upstream AGPL-3.0 license for the weights.

Both models have float16 weights and float32 NHWC image / normalized end-to-end detection tensors.
The manual x86 CI export and regeneration recipe are documented in the project README.
Medium (`yolo26m_float16.tflite`) is intentionally absent and remains a Colab exercise.
Do not substitute depth, segmentation, custom-class or INT8 models under these names.
