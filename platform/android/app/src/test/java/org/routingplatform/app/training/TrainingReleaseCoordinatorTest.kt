package org.routingplatform.app.training

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.routingplatform.app.navigation.LiveRouteAction
import org.routingplatform.app.navigation.NavigationDecisionTelemetry
import org.routingplatform.app.navigation.NavigationRecoveryAction
import org.routingplatform.app.navigation.NavigationWatchdogStatus

class TrainingReleaseCoordinatorTest {
    @Test
    fun acceptsValidArtifactAndProducesTrainingEvent() {
        val coordinator = TrainingReleaseCoordinator(TrainingArtifactRegistry())
        val result = coordinator.prepare(
            telemetry = NavigationDecisionTelemetry(
                1L,
                NavigationWatchdogStatus.HEALTHY,
                LiveRouteAction.KEEP_ROUTE,
                NavigationRecoveryAction.NONE,
                false,
                null,
            ),
            artifact = TrainingArtifact("a", "1.0", TrainingArtifactStage.SHADOW, 0.9, 0, "sig"),
        )
        assertTrue(result.accepted)
        assertEquals("a", result.artifact?.id)
        assertEquals("navigation-decision-v1", result.event?.schemaVersion)
    }

    @Test
    fun rejectsInvalidArtifact() {
        val result = TrainingReleaseCoordinator(TrainingArtifactRegistry()).prepare(
            NavigationDecisionTelemetry(
                1L,
                NavigationWatchdogStatus.HEALTHY,
                LiveRouteAction.KEEP_ROUTE,
                NavigationRecoveryAction.NONE,
                false,
                null,
            ),
            TrainingArtifact("bad", "1.0", TrainingArtifactStage.SHADOW, 1.2, 0, ""),
        )
        assertEquals(false, result.accepted)
    }
}
