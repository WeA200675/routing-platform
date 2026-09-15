package org.routingplatform.app.ai

import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.tanh

object SocialAiRuntime {

    fun plan(
        input: SocialAiRuntimeInput,
    ): SocialAiResponsePlan {
        val settings = input.settings

        val confidenceGain =
            0.55 +
                0.45 * input.contextConfidence

        val affinityHidden =
            boundedActivation(
                1.10 * normalize(input.warmthLevel) +
                    0.90 * normalize(settings.charmLevel) +
                    0.70 * input.engagement -
                    1.20
            )

        val playfulHidden =
            boundedActivation(
                1.00 * normalize(settings.humorLevel) +
                    1.10 * normalize(settings.playfulnessLevel) +
                    0.40 * input.engagement -
                    1.00
            )

        var humor =
            boundedPercent(
                settings.humorLevel *
                    confidenceGain *
                    (0.75 + 0.25 * playfulHidden)
            )

        var warmth =
            boundedPercent(
                input.warmthLevel *
                    (0.80 + 0.20 * affinityHidden)
            )

        var directness =
            boundedPercent(
                input.directnessLevel *
                    (0.90 + 0.10 * input.contextConfidence)
            )

        var charm =
            boundedPercent(
                settings.charmLevel *
                    confidenceGain *
                    (0.70 + 0.30 * affinityHidden)
            )

        var playfulness =
            boundedPercent(
                settings.playfulnessLevel *
                    confidenceGain *
                    (0.70 + 0.30 * playfulHidden)
            )

        var proactivity =
            boundedPercent(
                settings.proactivityLevel *
                    confidenceGain
            )

        var flirt =
            if (settings.adultFlirtOptIn) {
                boundedPercent(
                    settings.flirtLevel *
                        confidenceGain *
                        (0.60 + 0.40 * affinityHidden)
                )
            } else {
                0
            }

        var allowBanter =
            humor >= 10 ||
                playfulness >= 10

        var allowFlirt =
            settings.adultFlirtOptIn &&
                flirt >= 10

        var suppressNonessentialSocial =
            false

        if (!settings.socialAdaptationEnabled) {
            proactivity = 0
        }

        when (input.context) {
            SocialAiResponseContext.Conversation -> Unit

            SocialAiResponseContext.NavigationStatus -> {
                humor = (humor * 0.35).roundToInt()
                charm = (charm * 0.35).roundToInt()
                playfulness = (playfulness * 0.35).roundToInt()
                proactivity = (proactivity * 0.25).roundToInt()
                flirt = 0
                allowFlirt = false
                allowBanter =
                    humor >= 20 ||
                        playfulness >= 20
            }

            SocialAiResponseContext.CriticalGuidance -> {
                humor = 0
                charm = 0
                playfulness = 0
                proactivity = 0
                flirt = 0
                warmth = max(warmth, 70)
                directness = max(directness, 90)
                allowBanter = false
                allowFlirt = false
                suppressNonessentialSocial = true
            }
        }

        return SocialAiResponsePlan(
            humorLevel = humor,
            warmthLevel = warmth,
            directnessLevel = directness,
            charmLevel = charm,
            playfulnessLevel = playfulness,
            proactivityLevel = proactivity,
            flirtLevel = flirt,
            allowBanter = allowBanter,
            allowFlirt = allowFlirt,
            suppressNonessentialSocial = suppressNonessentialSocial,
        )
    }

    private fun normalize(
        value: Int,
    ): Double =
        value.coerceIn(0, 100) /
            100.0

    private fun boundedActivation(
        value: Double,
    ): Double =
        0.5 +
            0.5 * tanh(value)

    private fun boundedPercent(
        value: Double,
    ): Int =
        value
            .roundToInt()
            .coerceIn(0, 100)
}
