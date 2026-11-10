package io.github.roby222.rush.model

import kotlinx.serialization.Serializable

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

    /**
     * The usual duration at this time of day: TomTom's delay is measured against the typical traffic, not
     * an empty road, so this is longer than [freeFlowMin] (e.g. 32 min against 25 on a city route).
     */
    val usualMin get() = totalMin - delayMin

    /** 0 = regolare, 1 = rallentamento, 2 = intenso o strada chiusa. */
    val level get() = if (events.any { it.kind == CLOSURE }) 2 else trafficLevel(totalMin, delayMin)
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
