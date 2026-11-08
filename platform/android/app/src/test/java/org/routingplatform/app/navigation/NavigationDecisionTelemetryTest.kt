package org.routingplatform.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationDecisionTelemetryTest {
    @Test
    fun preservesDecisionReasonAndActions() {
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
        val decision = NavigationDecisionCoordinator().evaluate(
            nowElapsedMs = 20_000,
            lastPositionElapsedMs = 1_000,
            lastProgressElapsedMs = 1_000,
            navigationActive = true,
            liveEvents = NavigationLiveEventPrioritizer.prioritize(listOf(event), 1_000),
            nearestLiveEventDistanceMeters = 500.0,
        )
        val telemetry = NavigationDecisionTelemetryBuilder().build(20_000, decision)

        assertTrue(telemetry.recalculationRequested)
        assertEquals(decision.liveAction, telemetry.liveAction)
        assertEquals(decision.recovery.action, telemetry.recoveryAction)
        assertEquals(decision.recovery.watchdog.reason, telemetry.reason)
    }
}
