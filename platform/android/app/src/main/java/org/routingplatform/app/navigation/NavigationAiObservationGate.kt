package org.routingplatform.app.navigation

import kotlin.math.abs

enum class NavigationAiVerdict {
    Plausible,
    Suspicious,
    Reject,
}

data class NavigationAiObservation(
    val timestampNanos: Long,
    val latitude: Double,
    val longitude: Double,
    val speedMps: Double?,
    val bearingDegrees: Double?,
    val integrityIssues: Set<PositionIntegrityIssue>,
)

data class NavigationAiAssessment(
    val verdict: NavigationAiVerdict,
    val confidence: Double,
    val reasons: Set<String>,
) {
    init {
        require(confidence in 0.0..1.0)
    }
}

/**
 * Optional local model boundary. The model can enrich the assessment, but it
 * never gets authority to move native navigation progress.
 */
fun interface LocalNavigationAnomalyModel {
    fun assess(
        window: List<NavigationAiObservation>,
    ): NavigationAiAssessment
}

class NavigationAiObservationGate(
    private val model: LocalNavigationAnomalyModel? = null,
    private val maxWindowSize: Int = 32,
) {
    init {
        require(maxWindowSize >= 2)
    }

    private val window = ArrayDeque<NavigationAiObservation>()

    fun observe(
        observation: NavigationAiObservation,
    ): NavigationAiAssessment {
        if (observation.integrityIssues.isNotEmpty()) {
            return NavigationAiAssessment(
                NavigationAiVerdict.Reject,
                1.0,
                setOf("integrity_issue"),
            )
        }

        window.addLast(observation)
        while (window.size > maxWindowSize) {
            window.removeFirst()
        }

        val local = model?.assess(window.toList())
        if (local != null) {
            return local
        }

        val previous = window.dropLast(1).lastOrNull()
        val impliedSpeed = previous?.let {
            val dt = (observation.timestampNanos - it.timestampNanos) / 1_000_000_000.0
            if (dt > 0.0) distanceMeters(it, observation) / dt else null
        }

        return when {
            impliedSpeed != null && impliedSpeed > 80.0 ->
                NavigationAiAssessment(
                    NavigationAiVerdict.Reject,
                    0.98,
                    setOf("implausible_speed"),
                )
            observation.speedMps != null &&
                impliedSpeed != null &&
                abs(observation.speedMps - impliedSpeed) > 20.0 ->
                NavigationAiAssessment(
                    NavigationAiVerdict.Suspicious,
                    0.85,
                    setOf("speed_disagreement"),
                )
            else ->
                NavigationAiAssessment(
                    NavigationAiVerdict.Plausible,
                    0.60,
                    emptySet(),
                )
        }
    }

    fun clear() {
        window.clear()
    }

    private fun distanceMeters(
        first: NavigationAiObservation,
        second: NavigationAiObservation,
    ): Double {
        val lat = (first.latitude + second.latitude) * 0.5 * 0.017453292519943295
        val dx = (second.longitude - first.longitude) * 111_320.0 * kotlin.math.cos(lat)
        val dy = (second.latitude - first.latitude) * 110_540.0
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }
}
