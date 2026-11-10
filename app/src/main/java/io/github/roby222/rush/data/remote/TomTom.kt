package io.github.roby222.rush.data.remote

import io.github.roby222.rush.BuildConfig
import io.github.roby222.rush.model.Check
import io.github.roby222.rush.model.Place
import io.github.roby222.rush.model.TrafficEvent
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

@Serializable data class Summary(
    val lengthInMeters: Int = 0,
    val travelTimeInSeconds: Int = 0,
    val trafficDelayInSeconds: Int = 0,
    val noTrafficTravelTimeInSeconds: Int? = null,
)
@Serializable data class Section(
    val sectionType: String = "",
    val simpleCategory: String? = null,
    val delayInSeconds: Int? = null,
    val effectiveSpeedInKmh: Int? = null,
    val startPointIndex: Int = 0,
)
@Serializable data class Instruction(val pointIndex: Int = 0, val street: String? = null, val roadNumbers: List<String> = emptyList())
@Serializable data class Guidance(val instructions: List<Instruction> = emptyList())
@Serializable data class Route(val summary: Summary, val sections: List<Section> = emptyList(), val guidance: Guidance? = null)
@Serializable data class RouteResponse(val routes: List<Route> = emptyList())

@Serializable data class Position(val lat: Double, val lon: Double)
@Serializable data class Address(val freeformAddress: String = "")
@Serializable data class Poi(val name: String = "")
/** One search result; [dist] is the distance in meters from the point the search was near, when there was one. */
@Serializable data class SearchResult(
    val position: Position, val address: Address = Address(), val poi: Poi? = null, val dist: Double? = null,
) {
    /** "Name, address", without repeating a name the address already starts with (a square, a street). */
    val label get() = listOfNotNull(poi?.name?.takeUnless { address.freeformAddress.startsWith(it) }, address.freeformAddress.ifBlank { null })
        .joinToString(", ")
}
@Serializable data class SearchResponse(val results: List<SearchResult> = emptyList())

const val NEAR_RADIUS_M = 50_000

/** How many places the address field suggests. */
const val SUGGESTIONS = 5

class NoRouteException : Exception("No route found")

object TomTom {
    private val http = OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS).build()
    private val json = Json { ignoreUnknownKeys = true }

    private fun base() = HttpUrl.Builder().scheme("https").host("api.tomtom.com")
        .addQueryParameter("key", BuildConfig.TOMTOM_API_KEY)

    private fun get(url: HttpUrl): String {
        check(BuildConfig.TOMTOM_API_KEY.isNotBlank()) { "TOMTOM_API_KEY missing in local.properties" }
        http.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            val body = resp.body.string()
            check(resp.isSuccessful) { "HTTP ${resp.code}: ${body.take(200)}" }
            return body
        }
    }

    /**
     * Suggestions URL for a query still being typed (`typeahead`); with [near] results are limited to
     * [NEAR_RADIUS_M] around that point and carry their distance from it.
     */
    fun searchUrl(query: String, near: Place?): HttpUrl = base()
        .addPathSegments("search/2/search").addPathSegment("$query.json")
        .addQueryParameter("typeahead", "true")
        .addQueryParameter("limit", SUGGESTIONS.toString())
        .apply {
            if (near != null) {
                addQueryParameter("lat", near.lat.toString())
                addQueryParameter("lon", near.lon.toString())
                addQueryParameter("radius", NEAR_RADIUS_M.toString())
            }
        }
        .build()

    fun suggest(query: String, near: Place?): List<SearchResult> =
        json.decodeFromString<SearchResponse>(get(searchUrl(query, near))).results

    fun route(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double): Route {
        val url = base().addPathSegments("routing/1/calculateRoute")
            .addPathSegment("$fromLat,$fromLon:$toLat,$toLon").addPathSegment("json")
            .addQueryParameter("traffic", "true")
            .addQueryParameter("travelMode", "car")
            .addQueryParameter("departAt", "now")
            .addQueryParameter("computeTravelTimeFor", "all")
            .addQueryParameter("sectionType", "traffic")
            .addQueryParameter("instructionsType", "text") // street names, to say where each slowdown is
            .addQueryParameter("language", Locale.getDefault().toLanguageTag())
            .build()
        return parseRoute(get(url))
    }

    fun parseRoute(body: String): Route =
        json.decodeFromString<RouteResponse>(body).routes.firstOrNull() ?: throw NoRouteException()
}

private fun min(seconds: Int) = (seconds + 30) / 60

fun Route.toCheck(destination: String): Check {
    val instructions = guidance?.instructions.orEmpty().sortedBy { it.pointIndex }
    val events = sections.filter { it.sectionType == "TRAFFIC" }.sortedBy { it.startPointIndex }.map { sec ->
        // The street of a slowdown is the one named by the last instruction before it starts.
        val at = instructions.lastOrNull { it.pointIndex <= sec.startPointIndex }
        TrafficEvent(
            kind = sec.simpleCategory ?: "TRAFFIC",
            street = at?.street?.ifBlank { null } ?: at?.roadNumbers?.firstOrNull(),
            delayMin = min(sec.delayInSeconds ?: 0),
            speedKmh = sec.effectiveSpeedInKmh,
        )
    }
    // TomTom splits one jam into back-to-back sections; the user sees one slowdown per kind and street.
    val merged = events.fold(listOf<TrafficEvent>()) { acc, e ->
        val last = acc.lastOrNull()
        if (last != null && last.kind == e.kind && last.street == e.street) {
            acc.dropLast(1) + last.copy(delayMin = last.delayMin + e.delayMin,
                speedKmh = listOfNotNull(last.speedKmh, e.speedKmh).minOrNull())
        } else acc + e
    }
    val s = summary
    return Check(destination, min(s.travelTimeInSeconds), min(s.trafficDelayInSeconds),
        min(s.noTrafficTravelTimeInSeconds ?: s.travelTimeInSeconds), s.lengthInMeters / 1000.0, merged)
}
