package org.routingplatform.app.ui

import org.routingplatform.app.navigation.ManeuverType
import org.routingplatform.app.navigation.NavigationSessionState
import org.routingplatform.app.navigation.NavigationUiSnapshot
import org.routingplatform.app.profile.ExperiencePackRuntimeResolver
import org.routingplatform.app.profile.NavigationPersonalityPreferences
import org.routingplatform.app.profile.NavigationPersonalityTone
import org.routingplatform.app.profile.NavigationPreferences
import org.routingplatform.app.profile.VoiceGuidanceVerbosity
import org.routingplatform.app.profile.VoicePreferences

/*
 * Voice-guidance presentation boundary.
 *
 * This policy consumes already-produced maneuver truth and effective voice
 * preferences. It does not alter route geometry, route progress, positioning,
 * rerouting, candidate selection, cost evaluation or any safety gate.
 *
 * Android text-to-speech output is intentionally outside this milestone.
 */
internal enum class NavigationVoiceCueStage {
    Early,
    Prepare,
    Now,
    CriticalRepeat,
}

internal data class NavigationVoiceCueKey(
    val sessionId:
        String,

    val routeId:
        String,

    val maneuverType:
        ManeuverType,

    val instruction:
        String,

    val beginShapeIndex:
        Int?,

    val endShapeIndex:
        Int?,

    val stage:
        NavigationVoiceCueStage,
)

internal data class NavigationVoiceCue(
    val key:
        NavigationVoiceCueKey,

    val text:
        String,

    val languageTag:
        String,

    val voiceId:
        String?,

    val speechRate:
        Double,

    val speechPitch:
        Double =
        1.0,
)

internal object NavigationVoicePresentation {
    fun speedLimitCue(
        snapshot: NavigationUiSnapshot,
        warning: NavigationSpeedLimitWarning,
        voice: VoicePreferences,
    ): NavigationVoiceCue? {
        if (!voice.enabled || snapshot.state != NavigationSessionState.Navigating || snapshot.arrived || warning.routeId != snapshot.routeId) return null

        val stage = when (warning.kind) {
            NavigationSpeedLimitWarningKind.Approaching500m -> NavigationVoiceCueStage.Early
            NavigationSpeedLimitWarningKind.Approaching150m -> NavigationVoiceCueStage.Prepare
            NavigationSpeedLimitWarningKind.OverLimit -> NavigationVoiceCueStage.Now
        }
        val german = voice.languageTag.startsWith("de", ignoreCase = true)
        val text = when (warning.kind) {
            NavigationSpeedLimitWarningKind.Approaching500m -> if (german) "In 500 Metern gilt ein Tempolimit von ${warning.limitKmh} Stundenkilometern." else "Speed limit of ${warning.limitKmh} kilometers per hour in 500 meters."
            NavigationSpeedLimitWarningKind.Approaching150m -> if (german) "In 150 Metern gilt ein Tempolimit von ${warning.limitKmh} Stundenkilometern." else "Speed limit of ${warning.limitKmh} kilometers per hour in 150 meters."
            NavigationSpeedLimitWarningKind.OverLimit -> if (german) "Achtung. Das Tempolimit beträgt ${warning.limitKmh} Stundenkilometer." else "Caution. The speed limit is ${warning.limitKmh} kilometers per hour."
        }
        return NavigationVoiceCue(
            key = NavigationVoiceCueKey(
                sessionId = snapshot.sessionId, routeId = snapshot.routeId,
                maneuverType = ManeuverType.Continue,
                instruction = "speed-limit:${warning.deliveryId}",
                beginShapeIndex = warning.beginShapeIndex, endShapeIndex = warning.beginShapeIndex, stage = stage,
            ),
            text = text, languageTag = voice.languageTag, voiceId = voice.voiceId, speechRate = voice.speechRate,
        )
    }

    private const val NOW_DISTANCE_METERS =
        35.0

    private const val PREPARE_DISTANCE_METERS =
        180.0

    private const val EARLY_DISTANCE_METERS =
        500.0

    fun cue(
        snapshot:
            NavigationUiSnapshot,

        voice:
            VoicePreferences,

        navigationPreferences:
            NavigationPreferences =
            NavigationPreferences(),

        personality:
            NavigationPersonalityPreferences =
            NavigationPersonalityPreferences(),
    ): NavigationVoiceCue? {
        if (
            !voice.enabled ||
            snapshot.state !=
                NavigationSessionState.Navigating ||
            snapshot.arrived
        ) {
            return null
        }

        val maneuver =
            snapshot.currentManeuver
                ?: return null

        val instruction =
            maneuver.guidanceInstruction
                .trim()

        if (
            instruction.isEmpty()
        ) {
            return null
        }

        val repeatCritical =
            NavigationCriticalGuidanceRepeatPolicy
                .shouldRepeat(
                    enabled =
                        navigationPreferences
                            .repeatCriticalInstructions,

                    maneuverType =
                        maneuver.type,

                    distanceMeters =
                        snapshot
                            .distanceToCurrentManeuverEndM,
                )

        val stage =
            if (
                repeatCritical
            ) {
                NavigationVoiceCueStage
                    .CriticalRepeat
            } else {
                cueStage(
                    distanceMeters =
                        snapshot
                            .distanceToCurrentManeuverEndM,

                    verbosity =
                        voice.verbosity,
                )
                    ?: return null
            }

        val activePack =
            ExperiencePackRuntimeResolver
                .resolvePackOverride(personality)

        // Keep the maneuver intact; only ordinary Galactic cues get a short themed lead-in.
        // Critical repeats remain word-for-word neutral.
        val galactic =
            stage != NavigationVoiceCueStage.CriticalRepeat &&
                activePack?.tone == NavigationPersonalityTone.Futuristic
        val speechPitch = if (galactic) 1.08 else 1.0
        val speechText =
            if (galactic) {
                "${galacticLeadIn(stage, voice.languageTag)} $instruction"
            } else {
                instruction
            }

        return NavigationVoiceCue(
            key =
                NavigationVoiceCueKey(
                    sessionId =
                        snapshot.sessionId,

                    routeId =
                        snapshot.routeId,

                    maneuverType =
                        maneuver.type,

                    instruction =
                        instruction,

                    beginShapeIndex =
                        maneuver.beginShapeIndex,

                    endShapeIndex =
                        maneuver.endShapeIndex,

                    stage =
                        stage,
                ),

            text =
                speechText,

            languageTag =
                voice.languageTag,

            voiceId =
                voice.voiceId,

            speechRate =
                voice.speechRate,

            speechPitch =
                speechPitch,
        )
    }

    private fun galacticLeadIn(
        stage: NavigationVoiceCueStage,
        languageTag: String,
    ): String {
        val german = languageTag.startsWith("de", ignoreCase = true)
        return when (stage) {
            NavigationVoiceCueStage.Early -> if (german) "Sternenkarte voraus:" else "Star chart ahead:"
            NavigationVoiceCueStage.Prepare -> if (german) "Sprungfenster voraus:" else "Jump window ahead:"
            NavigationVoiceCueStage.Now -> if (german) "Jetzt, Pilot:" else "Now, pilot:"
            NavigationVoiceCueStage.CriticalRepeat -> ""
        }
    }

    private fun cueStage(
        distanceMeters:
            Double,

        verbosity:
            VoiceGuidanceVerbosity,
    ): NavigationVoiceCueStage? =
        when {
            distanceMeters <=
                NOW_DISTANCE_METERS ->
                NavigationVoiceCueStage.Now

            verbosity ==
                VoiceGuidanceVerbosity.Minimal &&
                distanceMeters <=
                    PREPARE_DISTANCE_METERS ->
                null

            verbosity ==
                VoiceGuidanceVerbosity.Minimal &&
                distanceMeters <=
                    EARLY_DISTANCE_METERS ->
                NavigationVoiceCueStage.Early

            distanceMeters <=
                PREPARE_DISTANCE_METERS ->
                NavigationVoiceCueStage.Prepare

            distanceMeters <=
                EARLY_DISTANCE_METERS ->
                NavigationVoiceCueStage.Early

            else ->
                null
        }
}
