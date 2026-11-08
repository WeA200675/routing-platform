package org.routingplatform.app.training

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.routingplatform.app.navigation.LiveRouteAction
import org.routingplatform.app.navigation.NavigationDecisionTelemetry
import org.routingplatform.app.navigation.NavigationRecoveryAction
import org.routingplatform.app.navigation.NavigationWatchdogStatus

class NavigationDecisionTrainingEventFactoryTest {
    @Test
    fun createsStableTrainingEventShape() {
        val event = NavigationDecisionTrainingEventFactory.create(
            NavigationDecisionTelemetry(
                capturedAtElapsedMs = 42L,
                watchdogStatus = NavigationWatchdogStatus.RECOVERY_REQUIRED,
                liveAction = LiveRouteAction.REQUEST_IMMEDIATE_RECALCULATION,
                recoveryAction = NavigationRecoveryAction.REQUEST_RECALCULATION,
                recalculationRequested = true,
                reason = "test",
            ),
        )
        assertNotNull(event.eventId)
        assertEquals("navigation.decision", event.eventType)
        assertEquals(true, event.recalculationRequested)
        assertEquals("test", event.reason)
    }
}
