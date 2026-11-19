package org.blindsystems.cyclope.navigation.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import java.util.Locale

/**
 * Routing = start + destination -> path + turn-by-turn steps, with OSRM (open source).
 *
 * We use the FOSSGIS community server, which has a real pedestrian ("foot") profile:
 *   https://routing.openstreetmap.de/routed-foot/route/v1/foot/{lon},{lat};{lon},{lat}
 * (the router.project-osrm.org demo server only has a car profile).
 * Usage policy: max 1 request/s, identifying User-Agent, OSM attribution.
 * For the final product we could self-host OSRM with the foot profile (Docker image osrm/osrm-backend).
 */
class OsrmClient(
    private val userAgent: String,
    private val baseUrl: String = "https://routing.openstreetmap.de/routed-foot",
    private val http: HttpGet = UrlConnectionHttpGet(),
    private val limiter: RateLimiter = RateLimiter(1100),
) {
    fun route(from: GeoPoint, to: GeoPoint): Route {
        val url = String.format(
            Locale.US,
            "%s/route/v1/foot/%.6f,%.6f;%.6f,%.6f?overview=full&geometries=geojson&steps=true",
            baseUrl, from.lon, from.lat, to.lon, to.lat,
        )
        limiter.acquire()
        return OsrmParser.parse(http.get(url, mapOf("User-Agent" to userAgent)))
    }
}

object OsrmParser {
    private val json = Json { ignoreUnknownKeys = true }

    /** GeoJSON coordinates are [lon, lat] (careful: not lat, lon!). */
    private fun lonLat(arr: JsonArray) = GeoPoint(arr[1].jsonPrimitive.double, arr[0].jsonPrimitive.double)

    fun parse(body: String): Route {
        val root = json.parseToJsonElement(body).jsonObject
        val code = root["code"]?.jsonPrimitive?.contentOrNull
        if (code != "Ok") {
            throw IOException("OSRM error: $code ${root["message"]?.jsonPrimitive?.contentOrNull.orEmpty()}")
        }
        val r = root["routes"]?.jsonArray?.firstOrNull()?.jsonObject ?: throw IOException("OSRM: no route found")

        val geometry = r["geometry"]!!.jsonObject["coordinates"]!!.jsonArray.map { lonLat(it.jsonArray) }
        val steps = r["legs"]!!.jsonArray.flatMap { leg ->
            leg.jsonObject["steps"]!!.jsonArray.map { parseStep(it.jsonObject) }
        }
        return Route(
            distanceM = r["distance"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
            durationS = r["duration"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
            geometry = geometry,
            steps = steps,
        )
    }

    private fun parseStep(s: JsonObject): RouteStep {
        val m = s["maneuver"]!!.jsonObject
        return RouteStep(
            type = m["type"]?.jsonPrimitive?.contentOrNull ?: "continue",
            modifier = m["modifier"]?.jsonPrimitive?.contentOrNull,
            location = lonLat(m["location"]!!.jsonArray),
            name = s["name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            distanceM = s["distance"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
            durationS = s["duration"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
            bearingBefore = m["bearing_before"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
            bearingAfter = m["bearing_after"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
            exit = m["exit"]?.jsonPrimitive?.intOrNull,
        )
    }
}
