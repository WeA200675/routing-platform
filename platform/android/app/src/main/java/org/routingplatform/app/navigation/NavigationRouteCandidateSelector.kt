package org.routingplatform.app.navigation

data class RouteCandidateScore(
    val routeId: String,
    val safetyScore: Double,
    val timeSeconds: Double,
    val costScore: Double,
    val confidence: Double,
)

object NavigationRouteCandidateSelector {
    fun select(
        candidates: List<RouteCandidateScore>,
        safetyThreshold: Double = 0.70,
    ): RouteCandidateScore? =
        candidates.asSequence()
            .filter { it.safetyScore >= safetyThreshold && it.confidence >= 0.50 }
            .minWithOrNull(
                compareBy<RouteCandidateScore> { it.safetyScore * -100.0 }
                    .thenBy { it.timeSeconds }
                    .thenBy { it.costScore },
            )
}
