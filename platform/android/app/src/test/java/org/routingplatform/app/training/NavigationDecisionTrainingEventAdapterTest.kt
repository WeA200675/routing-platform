package org.routingplatform.app.training

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.routingplatform.app.navigation.LiveRouteAction
import org.routingplatform.app.navigation.NavigationDecisionTelemetry
import org.routingplatform.app.navigation.NavigationRecoveryAction
import org.routingplatform.app.navigation.NavigationWatchdogStatus

class NavigationDecisionTrainingEventAdapterTest {
    @Test
    fun adaptsDecisionToFifoTrainingEvent() {
        val event = NavigationDecisionTrainingEventAdapter.toTrainingEvent(
            NavigationDecisionTelemetry(
                10L,
                NavigationWatchdogStatus.PROGRESS_STALE,
                LiveRouteAction.PREPARE_ALTERNATIVE,
                NavigationRecoveryAction.NONE,
                false,
                "stale progress",
            ),
            sessionId = "s",
            routeId = "r",
        )
        assertEquals("navigation-decision-v1", event.schemaVersion)
        assertEquals("s", event.sessionId)
        assertEquals("r", event.routeId)
        assertTrue(event.sizeBytes > 0)
        assertTrue(event.payload.contains("stale progress"))
    }
}
