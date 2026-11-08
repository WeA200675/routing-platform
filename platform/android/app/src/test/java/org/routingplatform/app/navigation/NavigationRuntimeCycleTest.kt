package org.routingplatform.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class NavigationRuntimeCycleTest {
    @Test
    fun producesLiveContextDecisionAndNextRefresh() {
        val provider = object : NavigationLiveEventProvider {
            override val providerId: String = "test"
            override fun fetch(nowEpochMs: Long) = emptyList<NavigationLiveEvent>()
        }
        val cycle = NavigationRuntimeCycle(
            liveRuntime = NavigationLiveRuntimeCoordinator(
                ResilientLiveEventProvider(listOf(provider)),
            ),
            intelligence = NavigationRuntimeIntelligence(),
        )
        val result = cycle.run(
            nowEpochMs = 1_000,
            nowElapsedMs = 1_000,
            lastPositionElapsedMs = 900,
            lastProgressElapsedMs = 900,
            navigationActive = true,
            speedMetersPerSecond = 10.0,
            networkAvailable = true,
            batteryPercent = 80,
            nearestLiveEventDistanceMeters = null,
        )
        assertNotNull(result.liveContext)
        assertNotNull(result.decision.lastDecision)
        assertEquals(60_000L, result.nextRefreshInMs)
    }
}
