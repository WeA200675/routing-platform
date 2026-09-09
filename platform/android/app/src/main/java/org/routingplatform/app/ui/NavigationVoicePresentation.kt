package org.routingplatform.app.ui

import org.routingplatform.app.navigation.ManeuverType
import org.routingplatform.app.navigation.NavigationSessionState
import org.routingplatform.app.navigation.NavigationUiSnapshot
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
)

internal object NavigationVoicePresentation {

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
            maneuver.instruction
                .trim()

        if (
            instruction.isEmpty()
        ) {
            return null
        }

        val stage =
            cueStage(
                distanceMeters =
                    snapshot
                        .distanceToCurrentManeuverEndM,

                verbosity =
                    voice.verbosity,
            )
                ?: return null

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
                instruction,

            languageTag =
                voice.languageTag,

            voiceId =
                voice.voiceId,

            speechRate =
                voice.speechRate,
        )
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
                VoiceGuidanceVerbosity.Minimal ->
                null

            distanceMeters <=
                PREPARE_DISTANCE_METERS ->
                NavigationVoiceCueStage.Prepare

            verbosity ==
                VoiceGuidanceVerbosity.Standard ->
                null

            distanceMeters <=
                EARLY_DISTANCE_METERS ->
                NavigationVoiceCueStage.Early

            else ->
                null
        }
}
