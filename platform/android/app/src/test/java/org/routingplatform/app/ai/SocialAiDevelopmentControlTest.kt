package org.routingplatform.app.ai

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SocialAiDevelopmentControlTest {
    @Test fun `development mode allows training until emergency stop`() {
        val control = SocialAiDevelopmentControl(SocialAiControlState(SocialAiOperatingMode.Development, false))
        control.requireAllowed(SocialAiOperation.Training)
        control.stop("Benutzerprüfung", 10L)
        assertTrue(control.state().stopped)
        assertFailsWith<SocialAiStoppedException> { control.requireAllowed(SocialAiOperation.ConversationInference) }
        control.resume(20L)
        assertFalse(control.state().stopped)
    }

    @Test fun `release blocks training and development blocks promotion`() {
        val release = SocialAiDevelopmentControl(SocialAiControlState(SocialAiOperatingMode.Release, false))
        assertFailsWith<SocialAiStoppedException> { release.requireAllowed(SocialAiOperation.Training) }
        val development = SocialAiDevelopmentControl(SocialAiControlState(SocialAiOperatingMode.Development, false))
        assertFailsWith<SocialAiStoppedException> { development.requireAllowed(SocialAiOperation.ProductionPromotion) }
    }
}
