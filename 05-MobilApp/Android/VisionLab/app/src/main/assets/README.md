# Bundled model assets

- `yolo26s_float16.tflite`: official COCO YOLO26 small.
- `yolo26n_float16.tflite`: official COCO YOLO26 nano.
- `yolo26m_float16.tflite`: official COCO YOLO26 medium.
- `model-manifest.json`: export provenance, exact bytes and SHA-256 hashes.
- `ULTRALYTICS-LICENSE.txt`: upstream AGPL-3.0 license for the weights.

All three models have float16 weights and float32 NHWC image / normalized end-to-end detection tensors.
The manual x86 CI export and regeneration recipe are documented in the project README.
The app preselects a capability-based recommendation; the user can always choose any of the three.
Do not substitute depth, segmentation, custom-class or INT8 models under these names.
