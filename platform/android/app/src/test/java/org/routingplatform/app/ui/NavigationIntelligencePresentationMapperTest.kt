package org.routingplatform.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import org.routingplatform.app.navigation.NavigationLiveRuntimeSnapshot
import org.routingplatform.app.navigation.NavigationRecoveryAction
import org.routingplatform.app.navigation.NavigationRuntimeCycleResult
import org.routingplatform.app.navigation.NavigationRuntimeIntelligenceState
import org.routingplatform.app.navigation.NavigationDecisionTelemetry
import org.routingplatform.app.navigation.LiveRouteAction
import org.routingplatform.app.navigation.NavigationWatchdogStatus

class NavigationIntelligencePresentationMapperTest {
    @Test
    fun mapsOfflineCycleToOfflineFallback() {
        val telemetry = NavigationDecisionTelemetry(
            1L,
            NavigationWatchdogStatus.HEALTHY,
            LiveRouteAction.KEEP_ROUTE,
            NavigationRecoveryAction.NONE,
            false,
            null,
        )
        val result = NavigationRuntimeCycleResult(
            NavigationLiveRuntimeSnapshot(1L, emptyList(), 0, 0),
            NavigationRuntimeIntelligenceState(telemetry, null),
            Long.MAX_VALUE,
        )
        assertEquals(
            NavigationIntelligenceUiState.OFFLINE,
            NavigationIntelligencePresentationMapper.map(result).state,
        )
    }
}
