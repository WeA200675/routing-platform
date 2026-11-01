package org.routingplatform.app.ai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SocialAiDevelopmentControlTest {
    @Test fun `development mode allows training until emergency stop`() {
        val control = SocialAiDevelopmentControl(SocialAiControlState(SocialAiOperatingMode.Development, false))
        control.requireAllowed(SocialAiOperation.Training)
        control.stop("Benutzerprüfung", 10L)
        assertTrue(control.state().stopped)
        assertThrows(SocialAiStoppedException::class.java) { control.requireAllowed(SocialAiOperation.ConversationInference) }
        control.resume(20L)
        assertFalse(control.state().stopped)
    }

    @Test fun `release blocks training and development blocks promotion`() {
        val release = SocialAiDevelopmentControl(SocialAiControlState(SocialAiOperatingMode.Release, false))
        assertThrows(SocialAiStoppedException::class.java) { release.requireAllowed(SocialAiOperation.Training) }
        val development = SocialAiDevelopmentControl(SocialAiControlState(SocialAiOperatingMode.Development, false))
        assertThrows(SocialAiStoppedException::class.java) { development.requireAllowed(SocialAiOperation.ProductionPromotion) }
    }
}
