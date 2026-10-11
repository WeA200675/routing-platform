package org.routingplatform.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SocialAiRuntimeTest {

    @Test
    fun adultFlirtRequiresExplicitOptIn() {
        val disabled =
            SocialAiRuntime.plan(
                SocialAiRuntimeInput(
                    settings =
                        SocialAiPersonalitySettings(
                            humorLevel = 80,
                            charmLevel = 80,
                            playfulnessLevel = 80,
                            proactivityLevel = 60,
                            flirtLevel = 100,
                            adultFlirtOptIn = false,
                        )
                )
            )

        assertEquals(0, disabled.flirtLevel)
        assertFalse(disabled.allowFlirt)

        val enabled =
            SocialAiRuntime.plan(
                SocialAiRuntimeInput(
                    settings =
                        SocialAiPersonalitySettings(
                            humorLevel = 80,
                            charmLevel = 80,
                            playfulnessLevel = 80,
                            proactivityLevel = 60,
                            flirtLevel = 100,
                            adultFlirtOptIn = true,
                        )
                )
            )

        assertTrue(enabled.flirtLevel > 0)
        assertTrue(enabled.allowFlirt)
    }

    @Test
    fun criticalGuidanceSuppressesNonessentialSocialOutput() {
        val plan =
            SocialAiRuntime.plan(
                SocialAiRuntimeInput(
                    settings =
                        SocialAiPersonalitySettings(
                            socialAdaptationEnabled = true,
                            humorLevel = 100,
                            charmLevel = 100,
                            playfulnessLevel = 100,
                            proactivityLevel = 100,
                            flirtLevel = 100,
                            adultFlirtOptIn = true,
                        ),
                    warmthLevel = 10,
                    directnessLevel = 10,
                    engagement = 1.0,
                    contextConfidence = 1.0,
                    context =
                        SocialAiResponseContext.CriticalGuidance,
                )
            )

        assertEquals(0, plan.humorLevel)
        assertEquals(0, plan.charmLevel)
        assertEquals(0, plan.playfulnessLevel)
        assertEquals(0, plan.proactivityLevel)
        assertEquals(0, plan.flirtLevel)
        assertTrue(plan.warmthLevel >= 70)
        assertTrue(plan.directnessLevel >= 90)
        assertFalse(plan.allowBanter)
        assertFalse(plan.allowFlirt)
        assertTrue(plan.suppressNonessentialSocial)
    }

    @Test
    fun navigationStatusReducesSocialIntensityAndAlwaysDisablesFlirt() {
        val settings =
            SocialAiPersonalitySettings(
                humorLevel = 100,
                charmLevel = 100,
                playfulnessLevel = 100,
                proactivityLevel = 100,
                flirtLevel = 100,
                adultFlirtOptIn = true,
            )

        val conversation =
            SocialAiRuntime.plan(
                SocialAiRuntimeInput(
                    settings = settings,
                    engagement = 1.0,
                    context =
                        SocialAiResponseContext.Conversation,
                )
            )

        val navigation =
            SocialAiRuntime.plan(
                SocialAiRuntimeInput(
                    settings = settings,
                    engagement = 1.0,
                    context =
                        SocialAiResponseContext.NavigationStatus,
                )
            )

        assertTrue(navigation.humorLevel < conversation.humorLevel)
        assertTrue(navigation.charmLevel < conversation.charmLevel)
        assertTrue(navigation.playfulnessLevel < conversation.playfulnessLevel)
        assertEquals(0, navigation.flirtLevel)
        assertFalse(navigation.allowFlirt)
    }
}
