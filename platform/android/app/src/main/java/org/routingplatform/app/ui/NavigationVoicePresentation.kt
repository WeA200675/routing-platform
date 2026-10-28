package org.routingplatform.app.ui

import org.routingplatform.app.navigation.ManeuverType
import org.routingplatform.app.navigation.NavigationSessionState
import org.routingplatform.app.navigation.NavigationUiSnapshot
import org.routingplatform.app.profile.NavigationPreferences
import org.routingplatform.app.profile.VoiceGuidanceVerbosity
import org.routingplatform.app.profile.VoicePreferences

/*
 * Voice guidance is based on route progress and the active maneuver only.
 * Future maneuvers must never be announced as if they were the current one.
 */
internal enum class NavigationVoiceCueStage {
    Early,
    Prepare,
    Now,
    CriticalRepeat,
}

internal data class NavigationVoiceCueKey(
    val sessionId: String,
    val routeId: String,
    val maneuverType: ManeuverType,
    val instruction: String,
    val beginShapeIndex: Int?,
    val endShapeIndex: Int?,
    val stage: NavigationVoiceCueStage,
)

internal data class NavigationVoiceCue(
    val key: NavigationVoiceCueKey,
    val text: String,
    val languageTag: String,
    val voiceId: String?,
    val speechRate: Double,
)

internal object NavigationVoicePresentation {
    /*
     * "Early" is intentionally kept within a short, actionable window.
     * The previous 500 m threshold was long enough to sound unrelated to the
     * upcoming turn on fast roads and made stale/preceding instructions seem
     * to fire at the wrong time.
     */
    private const val NOW_DISTANCE_METERS = 35.0
    private const val PREPARE_DISTANCE_METERS = 180.0
    private const val EARLY_DISTANCE_METERS = 350.0
    private const val MINIMUM_ACTIONABLE_SPEED_MPS = 1.0
    private const val MAXIMUM_EARLY_DISTANCE_METERS = 350.0

    fun cue(
        snapshot: NavigationUiSnapshot,
        voice: VoicePreferences,
        navigationPreferences: NavigationPreferences = NavigationPreferences(),
    ): NavigationVoiceCue? {
        if (
            !voice.enabled ||
            snapshot.state != NavigationSessionState.Navigating ||
            snapshot.arrived
        ) {
            return null
        }

        val maneuver = snapshot.currentManeuver ?: return null
        val instruction = maneuver.instruction.trim()
        if (instruction.isEmpty()) return null

        val distance = snapshot.distanceToCurrentManeuverEndM
        if (!distance.isFinite() || distance < 0.0) return null

        val repeatCritical =
            NavigationCriticalGuidanceRepeatPolicy.shouldRepeat(
                enabled = navigationPreferences.repeatCriticalInstructions,
                maneuverType = maneuver.type,
                distanceMeters = distance,
            )

        val stage =
            if (repeatCritical) {
                NavigationVoiceCueStage.CriticalRepeat
            } else {
                cueStage(
                    distanceMeters = distance,
                    verbosity = voice.verbosity,
                    speedMps = snapshot.currentSpeedMps,
                ) ?: return null
            }

        return NavigationVoiceCue(
            key = NavigationVoiceCueKey(
                sessionId = snapshot.sessionId,
                routeId = snapshot.routeId,
                maneuverType = maneuver.type,
                instruction = instruction,
                beginShapeIndex = maneuver.beginShapeIndex,
                endShapeIndex = maneuver.endShapeIndex,
                stage = stage,
            ),
            text = instruction,
            languageTag = voice.languageTag,
            voiceId = voice.voiceId,
            speechRate = voice.speechRate,
        )
    }

    private fun cueStage(
        distanceMeters: Double,
        verbosity: VoiceGuidanceVerbosity,
        speedMps: Double?,
    ): NavigationVoiceCueStage? {
        val earlyLimit =
            if (speedMps != null && speedMps.isFinite() && speedMps > 0.0) {
                minOf(
                    EARLY_DISTANCE_METERS,
                    (speedMps * 20.0).coerceAtMost(MAXIMUM_EARLY_DISTANCE_METERS),
                )
            } else {
                EARLY_DISTANCE_METERS
            }

        return when {
            distanceMeters <= NOW_DISTANCE_METERS ->
                NavigationVoiceCueStage.Now

            verbosity == VoiceGuidanceVerbosity.Minimal ->
                null

            distanceMeters <= PREPARE_DISTANCE_METERS ->
                NavigationVoiceCueStage.Prepare

            verbosity == VoiceGuidanceVerbosity.Standard ->
                null

            distanceMeters <= earlyLimit &&
                distanceMeters > MINIMUM_ACTIONABLE_SPEED_MPS ->
                NavigationVoiceCueStage.Early

            else ->
                null
        }
    }
}
