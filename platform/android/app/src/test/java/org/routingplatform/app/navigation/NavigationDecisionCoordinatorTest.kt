package org.routingplatform.app.navigation

import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationDecisionCoordinatorTest {
    @Test
    fun combinesRecoveryAndLiveImmediateRecalculation() {
        val event = NavigationLiveEvent(
            id = "closure",
            kind = LiveEventKind.ROAD_CLOSURE,
            latitude = 48.0,
            longitude = 11.0,
            severity = 5,
            confidence = 1.0,
            capturedAtEpochMs = 0,
            validUntilEpochMs = 10_000,
            source = "test",
        )
        val result = NavigationDecisionCoordinator().evaluate(
            nowElapsedMs = 2_000,
            lastPositionElapsedMs = 1_900,
            lastProgressElapsedMs = 1_900,
            navigationActive = true,
            liveEvents = NavigationLiveEventPrioritizer.prioritize(listOf(event), 1_000),
            nearestLiveEventDistanceMeters = 500.0,
        )
        assertTrue(result.shouldRequestRecalculation)
    }
}
