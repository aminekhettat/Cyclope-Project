package org.blindsystems.cyclope.navigation.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URLEncoder
import java.util.Locale

/** One address candidate returned by Nominatim. */
data class Place(
    /** Full Nominatim text (long: street, district, city, region, country...). */
    val displayName: String,
    /** Short text to read aloud: "54 Rue Jean Jaurès, Paris, 75019". */
    val spokenName: String,
    val point: GeoPoint,
    val importance: Double,
    /** Distance from the user when the search was made (null if the user position was unknown). */
    val distanceFromUserM: Double? = null,
)

/**
 * Geocoding = text address -> GPS coordinates, with OpenStreetMap's Nominatim (open source, free).
 * API doc: https://nominatim.org/release-docs/latest/api/Search/
 * Usage policy: max 1 request/s, an identifying User-Agent is mandatory, no autocomplete.
 */
class NominatimClient(
    private val userAgent: String,
    private val baseUrl: String = "https://nominatim.openstreetmap.org",
    private val http: HttpGet = UrlConnectionHttpGet(),
    private val limiter: RateLimiter = RateLimiter(1100),
) {
    /**
     * @param query the address as dictated by the user (text from the speech-to-text block)
     * @param near the user's position: results are biased towards it and sorted by distance
     */
    fun search(query: String, near: GeoPoint? = null, limit: Int = 5, countryCodes: String = "fr"): List<Place> {
        val q = URLEncoder.encode(query, "UTF-8")
        val sb = StringBuilder("$baseUrl/search?q=$q&format=jsonv2&addressdetails=1&limit=$limit")
        if (countryCodes.isNotBlank()) sb.append("&countrycodes=").append(countryCodes)
        if (near != null) {
            // viewbox = preferred area (~ +/- 0.2 degrees, about 20 km); bounded=0 keeps results outside it too.
            val d = 0.2
            sb.append(String.format(Locale.US, "&viewbox=%.5f,%.5f,%.5f,%.5f&bounded=0",
                near.lon - d, near.lat + d, near.lon + d, near.lat - d))
        }
        limiter.acquire()
        val json = http.get(sb.toString(), mapOf("User-Agent" to userAgent, "Accept-Language" to "fr"))
        return NominatimParser.parse(json, near)
    }
}

object NominatimParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(body: String, near: GeoPoint? = null): List<Place> {
        val arr: JsonArray = json.parseToJsonElement(body).jsonArray
        val places = arr.mapNotNull { el ->
            val o = el.jsonObject
            val lat = o["lat"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull() ?.takeIf { it.isFinite() } ?: return@mapNotNull null
            val lon = o["lon"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull() ?.takeIf { it.isFinite() } ?: return@mapNotNull null
            val point = GeoPoint(lat, lon)
            Place(
                displayName = o["display_name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                spokenName = spokenName(o),
                point = point,
                importance = o["importance"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                distanceFromUserM = near?.let { Geo.distance(it, point) },
            )
        }
        // Closest first when we know where the user is (54 rue Jean Jaurès exists in many towns).
        return if (near != null) places.sortedBy { it.distanceFromUserM } else places
    }

    /** Builds a short, speakable label from the "address" object (addressdetails=1). */
    fun spokenName(o: JsonObject): String {
        val a = o["address"]?.jsonObject
        fun field(vararg keys: String): String? =
            keys.firstNotNullOfOrNull { k -> a?.get(k)?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } }

        val name = o["name"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
        val number = field("house_number")
        val street = field("road", "pedestrian", "footway", "path", "square", "place")
        val town = field("city", "town", "village", "municipality", "hamlet")
        val postcode = field("postcode")

        val streetPart = listOfNotNull(number, street).joinToString(" ").ifBlank { null }
        val parts = listOfNotNull(name?.takeIf { it != street }, streetPart, town, postcode).distinct()
        return if (parts.isEmpty()) o["display_name"]?.jsonPrimitive?.contentOrNull.orEmpty() else parts.joinToString(", ")
    }
}
