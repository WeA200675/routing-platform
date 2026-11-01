package org.routingplatform.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SocialAiSelfImprovementTest {
    @Test fun improvementStartsBeforeRecoveryThreshold() {
        val controller = SocialAiSelfImprovementController()
        assertEquals(SocialAiImprovementAction.Observe, controller.recordFailure("timeout", 1L))
        assertEquals(SocialAiImprovementAction.ImproveSoon, controller.recordFailure("timeout", 2L))
        assertEquals(SocialAiImprovementAction.RecoveryTrainingRequired, controller.recordFailure("timeout", 3L))
        assertTrue(controller.snapshot().recoveryRequired)
    }

    @Test fun successClearsRecoveryRequirement() {
        val controller = SocialAiSelfImprovementController()
        repeat(3) { controller.recordFailure("quality", it.toLong()) }
        controller.recordSuccess()
        assertEquals(0, controller.snapshot().recentFailures)
        assertFalse(controller.snapshot().recoveryRequired)
    }
}
