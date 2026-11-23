package org.blindsystems.cyclope.visionlab

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import java.io.File

/** These are capabilities, not benchmarks. A delegate can silently use CPU kernels. */
object DeviceInfo {
    fun describe(context: Context): String {
        val memory = ActivityManager.MemoryInfo()
        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(memory)
        val cpu = Runtime.getRuntime().availableProcessors()
        val soc = if (Build.VERSION.SDK_INT >= 31) "${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}" else Build.HARDWARE
        val frequencies =
            (0 until cpu).joinToString("; ") { core ->
                val khz = runCatching { File("/sys/devices/system/cpu/cpu$core/cpufreq/cpuinfo_max_freq").readText().trim().toLong() }.getOrNull()
                "core $core: ${khz?.let { "${it / 1000} MHz max" } ?: "frequency not exposed by Android"}"
            }
        val gl = (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).deviceConfigurationInfo.glEsVersion

        val nnapi = if (Build.VERSION.SDK_INT >= 27) "API available; hardware execution is driver-dependent." else "Unavailable before Android 8.1/API 27."

        fun mb(bytes: Long) = bytes / (1024 * 1024)
        return """
            Phone: ${Build.MANUFACTURER} ${Build.MODEL}
            SoC (system on a chip): $soc
            Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})
            CPU cores available to this app: $cpu
            $frequencies
            ABI (native instruction sets): ${Build.SUPPORTED_ABIS.joinToString()}
            RAM: ${mb(memory.totalMem)} MB total; ${mb(memory.availMem)} MB available now
            CPU: always available. More threads do not always mean faster inference.
            GPU: OpenGL ES $gl reported. Actual LiteRT GPU compatibility is checked when loading.
            NNAPI: $nnapi NNAPI is deprecated on Android 15.
            NPU/DSP: not reliably enumerable through the public Java API. NNAPI does NOT prove an NPU is present or used.
            The live status shows the selected delegate and any CPU fallback, not per-operation hardware placement.
            """.trimIndent()
    }
}
