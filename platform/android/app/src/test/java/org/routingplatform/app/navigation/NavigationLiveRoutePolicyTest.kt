package org.routingplatform.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationLiveRoutePolicyTest {
    @Test
    fun nearSevereClosureRequestsImmediateRecalculation() {
        val event = NavigationLiveEvent(
            id = "closure",
            kind = LiveEventKind.ROAD_CLOSURE,
            latitude = 48.0,
            longitude = 11.0,
            severity = 5,
            confidence = 0.95,
            capturedAtEpochMs = 0,
            validUntilEpochMs = 10_000,
            source = "test",
        )
        val decision = NavigationLiveRoutePolicy.decide(
            NavigationLiveEventPrioritizer.prioritize(listOf(event), 1_000),
            1_000.0,
        )
        assertEquals(LiveRouteAction.REQUEST_IMMEDIATE_RECALCULATION, decision)
    }

    @Test
    fun trafficPreparesAlternativeWithoutInterruptingRoute() {
        val event = NavigationLiveEvent(
            id = "traffic",
            kind = LiveEventKind.TRAFFIC_JAM,
            latitude = 48.0,
            longitude = 11.0,
            severity = 2,
            confidence = 0.9,
            capturedAtEpochMs = 0,
            validUntilEpochMs = 10_000,
            source = "test",
        )
        val decision = NavigationLiveRoutePolicy.decide(
            NavigationLiveEventPrioritizer.prioritize(listOf(event), 1_000),
            1_000.0,
        )
        assertEquals(LiveRouteAction.PREPARE_ALTERNATIVE, decision)
    }
}
