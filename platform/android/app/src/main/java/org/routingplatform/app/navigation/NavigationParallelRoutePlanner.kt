package org.routingplatform.app.navigation

interface NavigationRouteCandidateSource {
    fun calculate(candidateId: String): RouteCandidateScore?
}

class NavigationParallelRoutePlanner(
    private val sources: List<NavigationRouteCandidateSource>,
    private val selector: (List<RouteCandidateScore>) -> RouteCandidateScore? =
        { candidates -> NavigationRouteCandidateSelector.select(candidates) },
) {
    fun calculate(candidateIds: List<String>): RouteCandidateScore? {
        val candidates = candidateIds.flatMap { id ->
            sources.mapNotNull { source -> source.calculate(id) }
        }
        return selector(candidates)
    }
}
