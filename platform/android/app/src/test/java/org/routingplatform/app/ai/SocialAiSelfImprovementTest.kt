package org.routingplatform.app.ai

import kotlin.test.Test
import kotlin.test.assertEquals

class SocialAiSelfImprovementTest {
    @Test fun `improvement starts before recovery threshold`() {
        val controller = SocialAiSelfImprovementController()
        assertEquals(SocialAiImprovementAction.Observe, controller.recordFailure("timeout", 1L))
        assertEquals(SocialAiImprovementAction.ImproveSoon, controller.recordFailure("timeout", 2L))
        assertEquals(SocialAiImprovementAction.RecoveryTrainingRequired, controller.recordFailure("timeout", 3L))
        assertEquals(true, controller.snapshot().recoveryRequired)
    }

    @Test fun `success clears recovery requirement`() {
        val controller = SocialAiSelfImprovementController()
        repeat(3) { controller.recordFailure("quality", it.toLong()) }
        controller.recordSuccess()
        assertEquals(0, controller.snapshot().recentFailures)
        assertEquals(false, controller.snapshot().recoveryRequired)
    }
}
