package org.routingplatform.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.routingplatform.app.ai.SocialAiResponseContext
import org.routingplatform.app.profile.AiPreferences
import org.routingplatform.app.profile.ExperiencePackCatalog

class SocialAiPresentationTest {

    @Test
    fun explicitProfileLevelsRemainAuthoritative() {
        val preferences =
            AiPreferences(
                humorLevel = 100,
                charmLevel = 80,
                playfulnessLevel = 70,
                proactivityLevel = 60,
                flirtLevel = 50,
                adultFlirtOptIn = true,
            )

        val settings =
            SocialAiPresentation.explicitSettings(
                preferences
            )

        assertEquals(100, settings.humorLevel)
        assertEquals(80, settings.charmLevel)
        assertEquals(70, settings.playfulnessLevel)
        assertEquals(60, settings.proactivityLevel)
        assertEquals(50, settings.flirtLevel)
        assertTrue(settings.adultFlirtOptIn)
    }

    @Test
    fun criticalGuidanceSuppressesSocialEmbellishment() {
        val plan =
            SocialAiPresentation.plan(
                preferences =
                    AiPreferences(
                        humorLevel = 100,
                        charmLevel = 100,
                        playfulnessLevel = 100,
                        proactivityLevel = 100,
                        flirtLevel = 100,
                        adultFlirtOptIn = true,
                    ),
                experiencePack =
                    ExperiencePackCatalog.require(
                        ExperiencePackCatalog.GALACTIC_PACK_ID
                    ),
                context =
                    SocialAiResponseContext.CriticalGuidance,
                engagement = 1.0,
                contextConfidence = 1.0,
            )

        assertEquals(0, plan.humorLevel)
        assertEquals(0, plan.charmLevel)
        assertEquals(0, plan.playfulnessLevel)
        assertEquals(0, plan.proactivityLevel)
        assertEquals(0, plan.flirtLevel)
        assertFalse(plan.allowBanter)
        assertFalse(plan.allowFlirt)
        assertTrue(plan.suppressNonessentialSocial)
    }
}
