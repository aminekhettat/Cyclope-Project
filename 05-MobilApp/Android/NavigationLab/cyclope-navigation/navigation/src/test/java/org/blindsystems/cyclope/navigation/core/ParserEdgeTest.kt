package org.blindsystems.cyclope.navigation.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class ParserEdgeTest {
    @Test
    fun nominatimDropsMissingNullAndInvalidCoordinateRows() {
        val body =
            """
            [
              {}, {"lat":null,"lon":"2"}, {"lat":"bad","lon":"2"}, {"lat":"48"},
              {"lat":"48","lon":null}, {"lat":"48","lon":"bad"}, {"lat":48,"lon":2}
            ]
            """.trimIndent()
        val place = NominatimParser.parse(body).single()
        assertEquals(GeoPoint(48.0, 2.0), place.point)
        assertEquals("", place.displayName)
        assertEquals("", place.spokenName)
        assertEquals(0.0, place.importance, 0.0)
        assertNull(place.distanceFromUserM)
        assertTrue(NominatimParser.parse("[]", GeoPoint(0.0, 0.0)).isEmpty())
    }

    @Test
    fun optionalImportanceAndDisplayNameDefaultsAreExplicit() {
        listOf("null", "\"bad\"").forEach { importance ->
            val p = NominatimParser.parse("""[{"lat":"48","lon":"2","display_name":null,"importance":$importance}]""").single()
            assertEquals(0.0, p.importance, 0.0)
            assertEquals("", p.displayName)
        }
        val withImportance = NominatimParser.parse("""[{"lat":48,"lon":2,"importance":0.4,"display_name":"Fallback"}]""").single()
        assertEquals(0.4, withImportance.importance, 0.0)
        assertEquals("Fallback", withImportance.spokenName)
    }

    @Test
    fun spokenNameTriesEveryStreetAndTownFallbackWithoutRepeatingStreetName() {
        listOf("road", "pedestrian", "footway", "path", "square", "place").forEach { road ->
            val obj =
                Json.parseToJsonElement(
                    """{"name":"Rue A","address":{"$road":"Rue A","house_number":"12","postcode":"75000"}}""",
                ).jsonObject
            assertEquals("12 Rue A, 75000", NominatimParser.spokenName(obj))
        }
        listOf("city", "town", "village", "municipality", "hamlet").forEach { town ->
            val obj = Json.parseToJsonElement("""{"name":"Musée","address":{"road":" ","$town":"Paris"}}""").jsonObject
            assertEquals("Musée, Paris", NominatimParser.spokenName(obj))
        }
        assertEquals(
            "Fallback",
            NominatimParser.spokenName(Json.parseToJsonElement("""{"name":" ","display_name":"Fallback","address":{}}""").jsonObject),
        )
        assertEquals("", NominatimParser.spokenName(Json.parseToJsonElement("""{"name":null,"display_name":null}""").jsonObject))
    }

    @Test
    fun malformedContainersCurrentlyAbortParsingRatherThanDroppingTheRow() {
        // Current robustness limitation: unlike invalid numeric strings, wrong JSON container types throw.
        listOf("{}", "[null]", "[{\"lat\":{},\"lon\":2}]").forEach { body ->
            assertThrows(IllegalArgumentException::class.java) { NominatimParser.parse(body) }
        }
        assertThrows(IllegalArgumentException::class.java) { NominatimParser.parse("[") }
    }

    @Test
    fun nonFiniteCoordinatesCurrentlyPassValidation_bugDocumentedForOwnerDecision() {
        // Known bug candidate: WGS84 coordinates should be finite; preserve current behavior, do not fix production code.
        val p = NominatimParser.parse("""[{"lat":"NaN","lon":"Infinity"}]""").single()
        assertTrue(p.point.lat.isNaN())
        assertEquals(Double.POSITIVE_INFINITY, p.point.lon, 0.0)
    }

    private fun osrm(
        routeFields: String = "",
        stepFields: String = "",
        maneuverFields: String = "",
    ) = """
        {"code":"Ok","routes":[{
          "geometry":{"coordinates":[[2,48],[2.1,48.1]]},
          "legs":[{"steps":[{"maneuver":{"location":[2,48]$maneuverFields}$stepFields}]}]
          $routeFields
        }]}
        """.trimIndent()

    @Test
    fun osrmOptionalFieldsDefaultAndOptionalManeuverFieldsArePreserved() {
        val minimal = OsrmParser.parse(osrm())
        assertEquals(0.0, minimal.distanceM, 0.0)
        assertEquals(0.0, minimal.durationS, 0.0)
        val s = minimal.steps.single()
        assertEquals("continue", s.type)
        assertEquals("", s.name)
        assertNull(s.modifier)
        assertNull(s.exit)
        assertEquals(0.0, s.bearingBefore, 0.0)
        assertEquals(0.0, s.bearingAfter, 0.0)
        val explicit =
            OsrmParser.parse(
                osrm(
                    ",\"distance\":null,\"duration\":\"bad\"",
                    ",\"name\":null,\"distance\":null,\"duration\":\"bad\"",
                    ",\"type\":null,\"modifier\":null,\"bearing_before\":null,\"bearing_after\":\"bad\",\"exit\":\"bad\"",
                ),
            ).steps.single()
        assertEquals(s, explicit)
        val full =
            OsrmParser.parse(
                osrm(
                    ",\"distance\":10,\"duration\":20",
                    ",\"name\":\"Rue A\",\"distance\":10,\"duration\":20",
                    ",\"type\":\"rotary\",\"modifier\":\"right\",\"bearing_before\":90,\"bearing_after\":180,\"exit\":2",
                ),
            ).steps.single()
        assertEquals("rotary", full.type)
        assertEquals(2, full.exit)
        assertEquals(90.0, full.bearingBefore, 0.0)
        assertEquals(180.0, full.bearingAfter, 0.0)
        assertEquals(10.0, full.distanceM, 0.0)
        assertEquals(20.0, full.durationS, 0.0)
        assertEquals("Rue A", full.name)
    }

    @Test
    fun osrmEmptyRoutesAndErrorCodesProduceIoErrors() {
        listOf(
            "{}",
            "{\"code\":null}",
            "{\"code\":\"NoRoute\",\"message\":null}",
            "{\"code\":\"NoRoute\",\"message\":\"No path\"}",
            "{\"code\":\"Ok\"}",
            "{\"code\":\"Ok\",\"routes\":[]}",
        ).forEach {
                body ->
            assertThrows(IOException::class.java) { OsrmParser.parse(body) }
        }
        assertTrue(OsrmParser.parse("""{"code":"Ok","routes":[{"geometry":{"coordinates":[]},"legs":[]}]}""").steps.isEmpty())
    }

    @Test
    fun clientsPropagateOfflineNetworkErrorsAndDoNotLoseHeaders() {
        val error = IOException("offline fixture failure")
        val failing = HttpGet { _, _ -> throw error }
        assertSame(
            error,
            assertThrows(IOException::class.java) { NominatimClient("ua", http = failing, limiter = RateLimiter(0)).search("x") },
        )
        assertSame(
            error,
            assertThrows(IOException::class.java) {
                OsrmClient("ua", http = failing, limiter = RateLimiter(0)).route(GeoPoint(0.0, 0.0), GeoPoint(1.0, 1.0))
            },
        )
        val fake =
            HttpGet { url, headers ->
                assertFalse(url.contains("countrycodes="))
                assertFalse(url.contains("viewbox="))
                assertTrue(url.contains("limit=2"))
                assertEquals(mapOf("User-Agent" to "ua", "Accept-Language" to "fr"), headers)
                "[]"
            }
        assertTrue(
            NominatimClient(
                "ua",
                baseUrl = "https://offline.invalid",
                http = fake,
                limiter = RateLimiter(0),
            ).search("a & b", countryCodes = " ", limit = 2).isEmpty(),
        )
    }
}
