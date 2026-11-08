package org.routingplatform.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationParallelRoutePlannerTest {
    @Test
    fun evaluatesCandidatesAndSelectsSafestRoute() {
        val source = object : NavigationRouteCandidateSource {
            override fun calculate(candidateId: String) = when (candidateId) {
                "fast" -> RouteCandidateScore("fast", 0.8, 100.0, 1.0, 0.9)
                "safe" -> RouteCandidateScore("safe", 0.95, 120.0, 2.0, 0.9)
                else -> null
            }
        }
        val result = NavigationParallelRoutePlanner(listOf(source))
            .calculate(listOf("fast", "safe"))
        assertEquals("safe", result?.routeId)
    }
}
