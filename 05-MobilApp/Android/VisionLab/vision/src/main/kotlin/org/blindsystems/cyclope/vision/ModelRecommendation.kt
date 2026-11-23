package org.blindsystems.cyclope.vision

/** Public capability snapshot, not a benchmark or proof of accelerator execution. */
data class PhoneCapabilities(
    val totalRamBytes: Long,
    val availableRamBytes: Long,
    val gpuCompatible: Boolean,
    val nnapiApiAvailable: Boolean,
    val cpuCores: Int,
    val soc: String,
    val androidApi: Int,
)

/** All three models remain selectable regardless of the advisory labels. */
data class ModelOption(val size: ModelSize, val label: String)

/** A fresh recommendation and the effective selection; explicit saved choices always win. */
data class ModelRecommendation(
    val recommended: ModelSize,
    val selected: ModelSize,
    val hasUserChoice: Boolean,
    val mediumPrecisionOption: Boolean,
    val explanation: String,
)

/**
 * One deterministic policy, using binary GiB thresholds. Below 6 GiB or without a
 * compatible LiteRT GPU, prefer nano; at least 6 GiB plus GPU compatibility prefers
 * small. At least 8 GiB also labels medium as a better-precision option, never an
 * automatic upgrade. Below 4 GiB the medium selection gets a nonblocking memory note.
 *
 * NNAPI API presence does NOT prove hardware acceleration, so it cannot raise the
 * recommendation. Available RAM, CPU cores, SoC and API are reported, not scored:
 * transient free RAM and vendor names are not reliable performance benchmarks.
 * Reevaluate by calling [evaluate] with a fresh snapshot and the same saved choice.
 * No camera, delegate, network or preference side effects occur here.
 */
object ModelAdvisor {
    const val BYTES_PER_GIB = 1_073_741_824L
    const val LOW_RAM_GIB = 4L
    const val SMALL_RAM_GIB = 6L
    const val MEDIUM_OPTION_RAM_GIB = 8L

    fun evaluate(
        phone: PhoneCapabilities,
        savedChoice: String?,
    ): ModelRecommendation {
        val recommended =
            if (phone.totalRamBytes >= SMALL_RAM_GIB * BYTES_PER_GIB && phone.gpuCompatible) ModelSize.SMALL else ModelSize.NANO
        val saved = ModelSize.entries.find { it.name == savedChoice }
        val selected = saved ?: recommended
        val mediumOption = phone.totalRamBytes >= MEDIUM_OPTION_RAM_GIB * BYTES_PER_GIB
        val ram = (phone.totalRamBytes.coerceAtLeast(0) * 10 / BYTES_PER_GIB) / 10.0
        val available = (phone.availableRamBytes.coerceAtLeast(0) * 10 / BYTES_PER_GIB) / 10.0
        val gpu = if (phone.gpuCompatible) "a LiteRT-compatible GPU reported" else "no confirmed LiteRT-compatible GPU"
        val nnapi = if (phone.nnapiApiAvailable) "NNAPI API available, not proof of hardware acceleration" else "NNAPI API unavailable"
        val precision = if (mediumOption) " Medium is also offered as a better-precision option; compare live latency and FPS." else ""
        val retained = if (saved != null) " Your saved choice, ${selected.name.lowercase()}, is retained." else " ${recommended.name.lowercase()} is preselected; you decide."
        val memory = if (selected == ModelSize.MEDIUM && phone.totalRamBytes < LOW_RAM_GIB * BYTES_PER_GIB) " Medium may use more memory on this phone; it remains selectable." else ""
        val explanation =
            "Your phone reports $ram GiB total RAM ($available GiB available now), $gpu; $nnapi. " +
                "${recommended.name.lowercase()} is recommended for your phone under the RAM/GPU rule. " +
                "You can choose any model for greater precision or speed.$precision$retained$memory"
        return ModelRecommendation(recommended, selected, saved != null, mediumOption, explanation)
    }

    fun options(recommendation: ModelRecommendation): List<ModelOption> =
        ModelSize.entries.map { size ->
            val recommended = if (size == recommendation.recommended) " — Recommended for your phone" else ""
            val precision = if (size == ModelSize.MEDIUM && recommendation.mediumPrecisionOption) " — Better-precision option" else ""
            ModelOption(size, "${size.name.lowercase()}$recommended$precision")
        }
}
