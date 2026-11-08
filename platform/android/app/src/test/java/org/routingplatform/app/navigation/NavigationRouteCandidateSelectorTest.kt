package org.routingplatform.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NavigationRouteCandidateSelectorTest {
    @Test
    fun rejectsUnsafeCandidates() {
        val result = NavigationRouteCandidateSelector.select(
            listOf(RouteCandidateScore("unsafe", 0.69, 10.0, 1.0, 0.99)),
        )
        assertNull(result)
    }

    @Test
    fun choosesSafestEligibleCandidateBeforeTime() {
        val result = NavigationRouteCandidateSelector.select(
            listOf(
                RouteCandidateScore("fast", 0.72, 100.0, 1.0, 0.90),
                RouteCandidateScore("safe", 0.95, 140.0, 3.0, 0.90),
            ),
        )
        assertEquals("safe", result?.routeId)
    }

    @Test
    fun choosesFasterRouteWhenSafetyMatches() {
        val result = NavigationRouteCandidateSelector.select(
            listOf(
                RouteCandidateScore("slow", 0.90, 140.0, 1.0, 0.90),
                RouteCandidateScore("fast", 0.90, 100.0, 2.0, 0.90),
            ),
        )
        assertEquals("fast", result?.routeId)
    }
}
