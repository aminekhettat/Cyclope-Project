package org.blindsystems.cyclope.navigation.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

class ParsersTest {

    @Test
    fun osrmRouteIsParsedWithLatLonInTheRightOrder() {
        val route = OsrmParser.parse(fixture("osrm_route_handmade.json"))
        assertEquals(250.0, route.distanceM, 1e-6)
        assertEquals(4, route.geometry.size)
        assertEquals(48.71, route.geometry[0].lat, 1e-6) // GeoJSON is [lon, lat]
        assertEquals(2.17, route.geometry[0].lon, 1e-6)
        assertEquals(listOf("depart", "turn", "arrive"), route.steps.map { it.type })
        assertEquals("right", route.steps[1].modifier)
        assertNull(route.steps[0].modifier)
        assertEquals("Rue B", route.steps[1].name)
        assertEquals(90.0, route.steps[1].bearingAfter, 1e-6)
    }

    @Test
    fun osrmErrorCodeThrows() {
        try {
            OsrmParser.parse(fixture("osrm_noroute_handmade.json"))
            fail("should throw")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("NoRoute"))
        }
    }

    @Test
    fun nominatimResultsAreSortedByDistanceFromUser() {
        val user = GeoPoint(48.7100, 2.1700) // near Orsay / Paris-Saclay
        val places = NominatimParser.parse(fixture("nominatim_search_handmade.json"), near = user)
        assertEquals(3, places.size)
        assertEquals("54 Rue Jean Jaurès, Orsay, 91400", places[0].spokenName)
        assertTrue(places[0].distanceFromUserM!! < 3_000)
        assertEquals("54 Rue Jean Jaurès, Toulouse, 31000", places.last().spokenName)
    }

    @Test
    fun nominatimPoiKeepsItsName() {
        val places = NominatimParser.parse(fixture("nominatim_search_handmade.json"))
        assertEquals("Gare de Lyon, Place Louis-Armand, Paris, 75012", places[2].spokenName)
        assertNull(places[2].distanceFromUserM)
    }

    @Test
    fun nominatimClientSendsUserAgentAndViewbox() {
        var seenUrl = ""
        var seenHeaders = emptyMap<String, String>()
        val fakeHttp = HttpGet { url, headers -> seenUrl = url; seenHeaders = headers; "[]" }
        val client = NominatimClient("CyclopeGEN3-test/0.1", http = fakeHttp, limiter = RateLimiter(0))
        val res = client.search("54 rue Jean Jaurès", near = GeoPoint(48.71, 2.17))
        assertTrue(res.isEmpty())
        assertTrue(seenUrl.startsWith("https://nominatim.openstreetmap.org/search?q=54+rue+Jean+Jaur%C3%A8s"))
        assertTrue(seenUrl.contains("&countrycodes=fr"))
        assertTrue(seenUrl.contains("&viewbox=1.97000,48.91000,2.37000,48.51000&bounded=0"))
        assertEquals("CyclopeGEN3-test/0.1", seenHeaders["User-Agent"])
    }

    @Test
    fun osrmClientBuildsFootUrlWithLonLatOrder() {
        var seenUrl = ""
        val fakeHttp = HttpGet { url, _ -> seenUrl = url; fixture("osrm_route_handmade.json") }
        val client = OsrmClient("ua", http = fakeHttp, limiter = RateLimiter(0))
        client.route(GeoPoint(48.71, 2.17), GeoPoint(48.72, 2.18))
        assertEquals(
            "https://routing.openstreetmap.de/routed-foot/route/v1/foot/2.170000,48.710000;2.180000,48.720000" +
                "?overview=full&geometries=geojson&steps=true",
            seenUrl,
        )
    }
}
