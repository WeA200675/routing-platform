package org.routingplatform.app.training

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseReadinessEvaluatorTest {
    @Test
    fun readyOnlyWhenAllGatesPass() {
        val health = TrainingServiceHealthMonitor().evaluate(true, true, true, true, true)
        val registry = TrainingArtifactRegistry()
        registry.register(TrainingArtifact("a", "1", TrainingArtifactStage.SHADOW, 0.9, 0, "sig"))
        registry.promoteCanary("a")
        registry.activate("a")

        val result = ReleaseReadinessEvaluator().evaluate(
            health = health,
            navigationReady = true,
            fallbackReady = true,
            activeArtifact = registry.active(),
        )
        assertTrue(result.isReady)
    }

    @Test
    fun failsWhenFallbackOrArtifactIsMissing() {
        val health = TrainingServiceHealthMonitor().evaluate(true, true, true, true, true)
        val result = ReleaseReadinessEvaluator().evaluate(
            health = health,
            navigationReady = true,
            fallbackReady = false,
            activeArtifact = null,
        )
        assertFalse(result.isReady)
    }
}
