package org.routingplatform.app.training

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainingConnectionRecoveryCoordinatorTest {
    @Test
    fun retriesWithBackoffAndOpensCircuit() {
        val coordinator = TrainingConnectionRecoveryCoordinator(
            maxFailuresBeforeCircuitOpen = 3,
            baseRetryDelayMs = 100L,
            maxRetryDelayMs = 500L,
        )
        assertEquals(TrainingRecoveryState.RECONNECTING, coordinator.onProbeFailure(0L).state)
        assertTrue(coordinator.shouldRetry(100L))
        assertEquals(TrainingRecoveryState.RECONNECTING, coordinator.onProbeFailure(100L).state)
        assertEquals(TrainingRecoveryState.CIRCUIT_OPEN, coordinator.onProbeFailure(300L).state)
        assertFalse(coordinator.shouldRetry(10_000L))
        assertTrue(coordinator.allowHalfOpenProbe(10_000L))
    }

    @Test
    fun successResetsFailuresAndKeepsNavigationIndependent() {
        val coordinator = TrainingConnectionRecoveryCoordinator()
        coordinator.onProbeFailure(0L)
        val snapshot = coordinator.onProbeSuccess(1_000L)
        assertEquals(TrainingRecoveryState.ONLINE, snapshot.state)
        assertEquals(0, snapshot.consecutiveFailures)
        assertEquals(1_000L, snapshot.lastSuccessAtElapsedMs)
    }
}
