package org.blindsystems.cyclope.vision

/** Availability is a candidate capability, never proof of NPU execution. */
enum class DelegateChoice { CPU, GPU, NNAPI }

data class DelegateSelection(val actual: DelegateChoice, val explanation: String)

fun selectDelegate(
    requested: DelegateChoice,
    gpuCompatible: Boolean,
    nnapiAvailable: Boolean,
): DelegateSelection =
    when {
        requested == DelegateChoice.GPU && !gpuCompatible -> DelegateSelection(DelegateChoice.CPU, "GPU unavailable; using CPU")
        requested == DelegateChoice.NNAPI && !nnapiAvailable -> DelegateSelection(DelegateChoice.CPU, "NNAPI unavailable; using CPU")
        requested == DelegateChoice.NNAPI -> DelegateSelection(requested, "NNAPI requested; driver may use CPU, GPU, DSP or NPU")
        else -> DelegateSelection(requested, "${requested.name} requested")
    }

enum class ModelSize(val assetName: String) {
    SMALL("yolo26s_float16.tflite"),
    MEDIUM("yolo26m_float16.tflite"),
    NANO("yolo26n_float16.tflite"),
}
