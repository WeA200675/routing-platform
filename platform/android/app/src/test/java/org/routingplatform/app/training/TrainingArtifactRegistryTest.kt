package org.routingplatform.app.training

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainingArtifactRegistryTest {
    @Test
    fun requiresCanaryBeforeActivationAndSupportsRollback() {
        val registry = TrainingArtifactRegistry()
        assertTrue(registry.register(artifact("a", "1.0")))
        assertTrue(registry.register(artifact("b", "2.0")))
        assertTrue(registry.promoteCanary("a"))
        assertTrue(registry.activate("a"))
        assertEquals("a", registry.active()?.id)

        assertTrue(registry.promoteCanary("b"))
        assertTrue(registry.activate("b"))
        assertEquals("b", registry.active()?.id)

        assertTrue(registry.rollback())
        assertEquals("a", registry.active()?.id)
    }

    @Test
    fun rejectsRegressionArtifact() {
        val registry = TrainingArtifactRegistry()
        assertTrue(registry.register(artifact("bad", "1.0", regressions = 1)))
        assertTrue(registry.promoteCanary("bad"))
        assertFalse(registry.activate("bad"))
    }

    private fun artifact(id: String, version: String, regressions: Int = 0) =
        TrainingArtifact(id, version, TrainingArtifactStage.SHADOW, 0.9, regressions, "sig")
}
