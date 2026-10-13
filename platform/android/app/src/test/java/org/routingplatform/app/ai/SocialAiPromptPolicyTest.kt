package org.routingplatform.app.ai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SocialAiPromptPolicyTest {
    @Test
    fun highAdultFlirtIsLimitedToInvitedNonGraphicConversation() {
        val preferences =
            SocialAiPersonalitySettings(
                humorLevel = 100,
                charmLevel = 100,
                playfulnessLevel = 100,
                flirtLevel = 100,
                adultFlirtOptIn = true,
            )
        val conversationPlan =
            SocialAiRuntime.plan(
                SocialAiRuntimeInput(
                    settings = preferences,
                    context = SocialAiResponseContext.Conversation,
                )
            )

        val conversation =
            SocialAiPromptPolicy.systemInstruction(
                SocialAiResponseContext.Conversation,
                conversationPlan,
            )

        assertTrue(conversation.contains("sexually suggestive"))
        assertTrue(conversation.contains("consensual and non-graphic"))

        val navigationPlan =
            SocialAiRuntime.plan(
                SocialAiRuntimeInput(
                    settings = preferences,
                    context = SocialAiResponseContext.NavigationStatus,
                )
            )
        val navigation =
            SocialAiPromptPolicy.systemInstruction(
                SocialAiResponseContext.NavigationStatus,
                navigationPlan,
            )

        assertFalse(navigation.contains("sexually suggestive"))
        assertFalse(navigationPlan.allowFlirt)
    }

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
