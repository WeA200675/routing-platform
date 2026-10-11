package org.routingplatform.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationRuntimeHealthTest {
    @Test fun stoppedTelemetryProjectsToBoundedHealth() {
        val health = NavigationRuntimeTelemetry.stopped().toHealth()

        assertEquals(NavigationRuntimePipelineStatus.Stopped, health.pipelineStatus)
        assertFalse(health.automaticProgressActive)
        assertEquals(NavigationPositionConfidence.Lost, health.observationConfidence)
        assertFalse(health.nativeUpdateAttempted)
        assertFalse(health.nativeUpdateAccepted)
    }

    @Test fun acceptedNativeUpdateRequiresAttempt() {
        val failure = runCatching {
            NavigationRuntimeHealth(
                pipelineStatus = NavigationRuntimePipelineStatus.Running,
                automaticProgressActive = true,
                observationConfidence = NavigationPositionConfidence.High,
                safetyStatus = null,
                nativeUpdateAttempted = false,
                nativeUpdateAccepted = true,
            )
        }
        assertTrue(failure.isFailure)
    }

    @Test fun stoppedPipelineCannotClaimAutomaticProgress() {
        val failure = runCatching {
            NavigationRuntimeHealth(
                pipelineStatus = NavigationRuntimePipelineStatus.Stopped,
                automaticProgressActive = true,
                observationConfidence = NavigationPositionConfidence.Lost,
                safetyStatus = null,
                nativeUpdateAttempted = false,
                nativeUpdateAccepted = false,
            )
        }
        assertTrue(failure.isFailure)
    }
}
