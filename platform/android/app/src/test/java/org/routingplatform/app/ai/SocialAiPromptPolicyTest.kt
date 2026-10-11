package org.routingplatform.app.ai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SocialAiPromptPolicyTest {
    @Test
    fun criticalGuidanceInstructionIsExplicitlyNonSocial() {
        val plan =
            SocialAiRuntime.plan(
                SocialAiRuntimeInput(
                    settings =
                        SocialAiPersonalitySettings(
                            humorLevel = 100,
                            charmLevel = 100,
                            playfulnessLevel = 100,
                            proactivityLevel = 100,
                            flirtLevel = 100,
                            adultFlirtOptIn = true,
                        ),
                    context = SocialAiResponseContext.CriticalGuidance,
                )
            )

        val instruction =
            SocialAiPromptPolicy.systemInstruction(
                SocialAiResponseContext.CriticalGuidance,
                plan,
            )

        assertTrue(instruction.contains("No humor"))
        assertTrue(instruction.contains("flirt=0"))
        assertFalse(plan.allowFlirt)
    }
}
