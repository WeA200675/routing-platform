package org.routingplatform.app.ui

import org.routingplatform.app.ai.SocialAiPersonalitySettings
import org.routingplatform.app.ai.SocialAiResponseContext
import org.routingplatform.app.ai.SocialAiResponsePlan
import org.routingplatform.app.ai.SocialAiRuntime
import org.routingplatform.app.ai.SocialAiRuntimeInput
import org.routingplatform.app.profile.AiPreferences
import org.routingplatform.app.profile.ExperiencePackDefinition

object SocialAiPresentation {

    fun explicitSettings(
        preferences: AiPreferences,
        experiencePack: ExperiencePackDefinition? = null,
    ): SocialAiPersonalitySettings =
        SocialAiPersonalitySettings(
            socialAdaptationEnabled =
                preferences.socialAdaptationEnabled,
            humorLevel =
                maxOf(preferences.humorLevel, experiencePack?.humorLevel ?: 0),
            charmLevel =
                preferences.charmLevel,
            playfulnessLevel =
                maxOf(preferences.playfulnessLevel, experiencePack?.humorLevel ?: 0),
            proactivityLevel =
                preferences.proactivityLevel,
            flirtLevel =
                preferences.flirtLevel,
            adultFlirtOptIn =
                preferences.adultFlirtOptIn,
        )

    fun plan(
        preferences: AiPreferences,
        experiencePack: ExperiencePackDefinition,
        context: SocialAiResponseContext,
        engagement: Double = 0.5,
        contextConfidence: Double = 1.0,
    ): SocialAiResponsePlan =
        SocialAiRuntime.plan(
            SocialAiRuntimeInput(
                settings =
                    explicitSettings(
                        preferences = preferences,
                        experiencePack = experiencePack,
                    ),
                warmthLevel =
                    experiencePack.warmthLevel,
                directnessLevel =
                    experiencePack.directnessLevel,
                engagement =
                    engagement,
                contextConfidence =
                    contextConfidence,
                context =
                    context,
            )
        )
}
