package org.blindsystems.cyclope.navigation.core

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI

/**
 * Minimal HTTP GET abstraction.
 * The real implementation uses HttpURLConnection (available on Android and on the JVM);
 * unit tests replace it with a fake that returns a saved JSON file, so tests run offline.
 */
fun interface HttpGet {
    @Throws(IOException::class)
    fun get(url: String, headers: Map<String, String>): String
}

class UrlConnectionHttpGet(
    private val connectTimeoutMs: Int = 10_000,
    private val readTimeoutMs: Int = 15_000,
) : HttpGet {
    override fun get(url: String, headers: Map<String, String>): String {
        val conn = URI(url).toURL().openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "GET"
            conn.connectTimeout = connectTimeoutMs
            conn.readTimeout = readTimeoutMs
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            val code = conn.responseCode
            if (code != 200) {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw IOException("HTTP $code for $url ${err.take(200)}")
            }
            return conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}

/**
 * Both public servers we use (nominatim.openstreetmap.org and routing.openstreetmap.de)
 * allow at most 1 request per second. This blocks the calling thread if needed.
 * Never call it from the Android UI thread.
 */
class RateLimiter(private val minIntervalMs: Long, private val clock: () -> Long = System::currentTimeMillis) {
    private var lastCall = 0L

    @Synchronized
    fun acquire() {
        val wait = lastCall + minIntervalMs - clock()
        if (wait > 0) Thread.sleep(wait)
        lastCall = clock()
    }
}
