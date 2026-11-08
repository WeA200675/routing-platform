package org.routingplatform.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class NavigationRuntimeIntelligenceTest {
    @Test
    fun exposesRecoveryRequestAndTelemetryTogether() {
        val state = NavigationRuntimeIntelligence().evaluate(
            nowElapsedMs = 20_000,
            lastPositionElapsedMs = 1_000,
            lastProgressElapsedMs = 1_000,
            navigationActive = true,
            liveEvents = emptyList(),
            nearestLiveEventDistanceMeters = null,
        )
        assertNotNull(state.lastDecision)
        assertEquals(NavigationRecoveryAction.REQUEST_RECALCULATION,
            state.lastDecision?.recoveryAction)
        assertNotNull(state.pendingRecoveryRequestId)
    }
}
