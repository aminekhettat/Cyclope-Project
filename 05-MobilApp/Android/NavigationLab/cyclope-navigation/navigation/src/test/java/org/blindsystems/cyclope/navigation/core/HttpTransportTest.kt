package org.blindsystems.cyclope.navigation.core

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException
import java.net.InetAddress
import java.util.concurrent.TimeUnit

/** Loopback-only fake server; no internet, production endpoint or fixed port. */
class HttpTransportTest {
    @Test
    fun getSendsHeadersAndDecodesUtf8ThenSurfacesHttpErrors() {
        val server = MockWebServer()
        server.start(InetAddress.getByName("127.0.0.1"), 0)
        try {
            server.enqueue(MockResponse().setResponseCode(200).setBody("école à côté"))
            server.enqueue(MockResponse().setResponseCode(503).setBody("x".repeat(250)))
            server.enqueue(MockResponse().setResponseCode(404).setBody(""))
            val client = UrlConnectionHttpGet(2000, 2000)
            assertEquals("école à côté", client.get(server.url("/ok").toString(), mapOf("User-Agent" to "Cyclope-test")))
            val request = requireNotNull(server.takeRequest(1, TimeUnit.SECONDS))
            assertEquals("GET", request.method)
            assertEquals("Cyclope-test", request.getHeader("User-Agent"))
            assertEquals("/ok", request.path)
            val errorUrl = server.url("/error").toString()
            val error = assertThrows(IOException::class.java) { client.get(errorUrl, emptyMap()) }
            assertEquals("HTTP 503 for $errorUrl ${"x".repeat(200)}", error.message)
            val emptyUrl = server.url("/empty").toString()
            val emptyError = assertThrows(IOException::class.java) { client.get(emptyUrl, emptyMap()) }
            assertEquals("HTTP 404 for $emptyUrl ", emptyError.message)
        } finally {
            server.shutdown()
        }
    }
}
