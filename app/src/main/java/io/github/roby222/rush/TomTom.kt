package io.github.roby222.rush

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Locale
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
    val startPointIndex: Int = 0,
)
@Serializable data class Instruction(val pointIndex: Int = 0, val street: String? = null, val roadNumbers: List<String> = emptyList())
@Serializable data class Guidance(val instructions: List<Instruction> = emptyList())
@Serializable data class Route(val summary: Summary, val sections: List<Section> = emptyList(), val guidance: Guidance? = null)
@Serializable data class RouteResponse(val routes: List<Route> = emptyList())

@Serializable data class Position(val lat: Double, val lon: Double)
@Serializable data class Address(val freeformAddress: String = "")
@Serializable data class Poi(val name: String = "")
@Serializable data class SearchResult(val position: Position, val address: Address = Address(), val poi: Poi? = null) {
    val label get() = listOfNotNull(poi?.name, address.freeformAddress.ifBlank { null }).joinToString(", ")
}
@Serializable data class SearchResponse(val results: List<SearchResult> = emptyList())

const val NEAR_RADIUS_M = 50_000

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
            .addQueryParameter("instructionsType", "text") // street names, to say where each slowdown is
            .addQueryParameter("language", Locale.getDefault().toLanguageTag())
            .build()
        return parseRoute(get(url))
    }

    fun parseRoute(body: String): Route =
        json.decodeFromString<RouteResponse>(body).routes.firstOrNull() ?: throw NoRouteException()
}

private fun min(seconds: Int) = (seconds + 30) / 60

const val CLOSURE = "ROAD_CLOSURE"

/**
 * One slowdown on the route, in driving order. [kind] is TomTom's simpleCategory (JAM, ROAD_WORK, ROAD_CLOSURE, …)
 * or TRAFFIC; the UI turns it into words. [street] is null when TomTom gives no name for that stretch.
 */
@Serializable data class TrafficEvent(val kind: String, val street: String? = null, val delayMin: Int, val speedKmh: Int? = null) {
    val severe get() = kind == CLOSURE || delayMin >= 10 // same threshold as a red trafficLevel
}

/** The outcome of one successful check, saved for the Home screen. */
@Serializable data class Check(
    val destination: String,
    val totalMin: Int,
    val delayMin: Int,
    val freeFlowMin: Int,
    val km: Double,
    val events: List<TrafficEvent> = emptyList(),
) {
    /** Slowdowns worth telling the user about: any closure, otherwise only those that cost time. */
    val notable get() = events.filter { it.kind == CLOSURE || it.delayMin > 0 }

    /** 0 = regolare, 1 = rallentamento, 2 = intenso o strada chiusa. */
    val level get() = if (events.any { it.kind == CLOSURE }) 2 else trafficLevel(totalMin, delayMin)
}

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

/**
 * 0 = regolare, 1 = rallentamento, 2 = intenso. Minutes first: the user leaves at a fixed time, so
 * +5 min matters on any trip; the ratio only makes a short trip intense sooner.
 */
fun trafficLevel(totalMin: Int, delayMin: Int): Int {
    val ratio = delayMin.toDouble() / maxOf(1, totalMin - delayMin)
    return when {
        delayMin <= 2 -> 0
        delayMin >= 10 || ratio >= 0.4 -> 2
        else -> 1
    }
}
