package io.github.roby222.rush

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

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
)
@Serializable data class Route(val summary: Summary, val sections: List<Section> = emptyList())
@Serializable data class RouteResponse(val routes: List<Route> = emptyList())

@Serializable data class Position(val lat: Double, val lon: Double)
@Serializable data class Address(val freeformAddress: String = "")
@Serializable data class Poi(val name: String = "")
@Serializable data class SearchResult(val position: Position, val address: Address = Address(), val poi: Poi? = null) {
    val label get() = listOfNotNull(poi?.name, address.freeformAddress.ifBlank { null }).joinToString(", ")
}
@Serializable data class SearchResponse(val results: List<SearchResult> = emptyList())

const val NEAR_RADIUS_M = 50_000

object TomTom {
    private val http = OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS).build()
    private val json = Json { ignoreUnknownKeys = true }

    private fun base() = HttpUrl.Builder().scheme("https").host("api.tomtom.com")
        .addQueryParameter("key", BuildConfig.TOMTOM_API_KEY)

    private fun get(url: HttpUrl): String {
        check(BuildConfig.TOMTOM_API_KEY.isNotBlank()) { "TOMTOM_API_KEY mancante in local.properties" }
        http.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            val body = resp.body.string()
            check(resp.isSuccessful) { "HTTP ${resp.code}: ${body.take(200)}" }
            return body
        }
    }

    /** Search URL; with [near] results are limited to [NEAR_RADIUS_M] around that point. */
    fun searchUrl(query: String, near: Place?): HttpUrl = base()
        .addPathSegments("search/2/search").addPathSegment("$query.json")
        .addQueryParameter("limit", "1")
        .apply {
            if (near != null) {
                addQueryParameter("lat", near.lat.toString())
                addQueryParameter("lon", near.lon.toString())
                addQueryParameter("radius", NEAR_RADIUS_M.toString())
            }
        }
        .build()

    fun search(query: String, near: Place?): SearchResult? =
        json.decodeFromString<SearchResponse>(get(searchUrl(query, near))).results.firstOrNull()

    fun route(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double): Route {
        val url = base().addPathSegments("routing/1/calculateRoute")
            .addPathSegment("$fromLat,$fromLon:$toLat,$toLon").addPathSegment("json")
            .addQueryParameter("traffic", "true")
            .addQueryParameter("travelMode", "car")
            .addQueryParameter("departAt", "now")
            .addQueryParameter("computeTravelTimeFor", "all")
            .addQueryParameter("sectionType", "traffic")
            .build()
        return parseRoute(get(url))
    }

    fun parseRoute(body: String): Route =
        json.decodeFromString<RouteResponse>(body).routes.firstOrNull() ?: error("Nessun percorso trovato")
}

private fun min(seconds: Int) = (seconds + 30) / 60

/** Notification title and text for a route. */
fun describe(destination: String, r: Route): Pair<String, String> {
    val s = r.summary
    val title = "$destination: ${min(s.travelTimeInSeconds)} min (+${min(s.trafficDelayInSeconds)} traffico)"
    val lines = mutableListOf("%.1f km · senza traffico %d min".format(s.lengthInMeters / 1000.0, min(s.noTrafficTravelTimeInSeconds ?: s.travelTimeInSeconds)))
    r.sections.filter { it.sectionType == "TRAFFIC" }.forEach { sec ->
        val what = when (sec.simpleCategory) {
            "JAM" -> "Coda"
            "ROAD_WORK" -> "Lavori"
            "ROAD_CLOSURE" -> "Strada chiusa"
            null -> "Traffico"
            else -> sec.simpleCategory.lowercase().replace('_', ' ')
        }
        val speed = sec.effectiveSpeedInKmh?.let { " ($it km/h)" } ?: ""
        lines += "$what: +${min(sec.delayInSeconds ?: 0)} min$speed"
    }
    return title to lines.joinToString("\n")
}
