package org.routingplatform.app.navigation

/**
 * Selects a via candidate only from complete routes produced by the routing
 * engine. Geographic distance and AI output are deliberately not scoring
 * inputs.
 */
data class NavigationViaCandidateRoute(
    val candidateId: String,
    val request: NavigationRouteRequest,
    val route: NavigationRouteContract,
) {
    init {
        require(candidateId.isNotBlank())
        require(request.viaPoints.isNotEmpty())
    }
}

data class NavigationViaCandidateRanked(
    val candidate: NavigationViaCandidateRoute,
    val addedDurationS: Double,
    val addedDistanceM: Double,
)

data class NavigationViaCandidateEvaluation(
    val baseline: NavigationRouteContract,
    val rankedCandidates: List<NavigationViaCandidateRanked>,
)

sealed interface NavigationViaCandidateSelection {
    data class Selected(
        val candidate: NavigationViaCandidateRoute,
        val addedDurationS: Double,
        val addedDistanceM: Double,
    ) : NavigationViaCandidateSelection

    data class ClarificationRequired(
        val candidates: List<NavigationViaCandidateRoute>,
    ) : NavigationViaCandidateSelection

    data object NoRoutableCandidate : NavigationViaCandidateSelection
}

class NavigationViaCandidateSelector(
    private val maximumAutomaticAddedDurationS: Double = 600.0,
    private val ambiguityDurationS: Double = 60.0,
) {
    init {
        require(maximumAutomaticAddedDurationS >= 0.0)
        require(ambiguityDurationS >= 0.0)
    }

    fun select(
        baseline: NavigationRouteContract,
        candidates: List<NavigationViaCandidateRoute>,
    ): NavigationViaCandidateSelection {
        val ranked = rank(baseline, candidates)
        if (ranked.isEmpty()) {
            return NavigationViaCandidateSelection.NoRoutableCandidate
        }

        val best = ranked.first()
        if (best.addedDurationS > maximumAutomaticAddedDurationS) {
            return NavigationViaCandidateSelection.ClarificationRequired(
                ranked.map { it.candidate }
            )
        }

        val second = ranked.getOrNull(1)
        if (
            second != null &&
            second.addedDurationS - best.addedDurationS <= ambiguityDurationS
        ) {
            return NavigationViaCandidateSelection.ClarificationRequired(
                ranked.map { it.candidate }
            )
        }

        return NavigationViaCandidateSelection.Selected(
            candidate = best.candidate,
            addedDurationS = best.addedDurationS,
            addedDistanceM = best.addedDistanceM,
        )
    }

    fun rank(
        baseline: NavigationRouteContract,
        candidates: List<NavigationViaCandidateRoute>,
    ): List<NavigationViaCandidateRanked> =
        candidates
            .map { candidate ->
                NavigationViaCandidateRanked(
                    candidate = candidate,
                    addedDurationS =
                        (candidate.route.durationS - baseline.durationS)
                            .coerceAtLeast(0.0),
                    addedDistanceM =
                        (candidate.route.distanceM - baseline.distanceM)
                            .coerceAtLeast(0.0),
                )
            }
            .sortedWith(
                compareBy<NavigationViaCandidateRanked> { it.addedDurationS }
                    .thenBy { it.addedDistanceM }
                    .thenBy { it.candidate.candidateId }
            )
}
