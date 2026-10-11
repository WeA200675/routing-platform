package org.routingplatform.app.ui

import org.routingplatform.app.navigation.ManeuverType
import org.routingplatform.app.navigation.NavigationSessionState
import org.routingplatform.app.navigation.NavigationUiSnapshot
import org.routingplatform.app.profile.InstructionLeadTimePreference
import org.routingplatform.app.profile.NavigationHapticIntensity
import org.routingplatform.app.profile.NavigationPreferences

/*
 * Haptic-guidance presentation boundary.
 *
 * It consumes maneuver truth already present in NavigationUiSnapshot and
 * explicit presentation preferences. It cannot mutate route geometry,
 * route progress, positioning, rerouting, candidate selection, cost
 * evaluation or any safety-critical state.
 */
internal enum class NavigationHapticCueStage {
    Prepare,
    Now,
    CriticalRepeat,
}

internal enum class NavigationHapticSignal {
    Prepare,
    Now,
    Critical,
    Arrival,
}

internal data class NavigationHapticCueKey(
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
        NavigationHapticCueStage,
)

internal data class NavigationHapticCue(
    val key:
        NavigationHapticCueKey,

    val signal:
        NavigationHapticSignal,

    val intensity:
        NavigationHapticIntensity,
)

internal object NavigationHapticPresentation {

    fun cue(
        snapshot:
            NavigationUiSnapshot,

        preferences:
            NavigationPreferences,
    ): NavigationHapticCue? {
        if (
            !preferences
                .hapticGuidanceEnabled ||
            snapshot.state !=
                NavigationSessionState
                    .Navigating ||
            snapshot.arrived
        ) {
            return null
        }

        val maneuver =
            snapshot.currentManeuver
                ?: return null

        if (
            maneuver.type !in
                ACTIONABLE_MANEUVER_TYPES
        ) {
            return null
        }

        val stage =
            cueStage(
                distanceMeters =
                    snapshot
                        .distanceToCurrentManeuverEndM,

                leadTime =
                    preferences
                        .instructionLeadTime,

                maneuverType =
                    maneuver.type,

                repeatCriticalInstructions =
                    preferences
                        .repeatCriticalInstructions,
            )
                ?: return null

        val signal =
            when (
                stage
            ) {
                NavigationHapticCueStage
                    .Prepare ->
                    NavigationHapticSignal
                        .Prepare

                NavigationHapticCueStage
                    .Now ->
                    when {
                        maneuver.type ==
                            ManeuverType.Arrive ->
                            NavigationHapticSignal
                                .Arrival

                        NavigationCriticalGuidanceRepeatPolicy
                            .isCritical(
                                maneuver.type
                            ) ->
                            NavigationHapticSignal
                                .Critical

                        else ->
                            NavigationHapticSignal
                                .Now
                    }

                NavigationHapticCueStage
                    .CriticalRepeat ->
                    NavigationHapticSignal
                        .Critical
            }

        return NavigationHapticCue(
            key =
                NavigationHapticCueKey(
                    sessionId =
                        snapshot.sessionId,

                    routeId =
                        snapshot.routeId,

                    maneuverType =
                        maneuver.type,

                    instruction =
                        maneuver
                            .instruction
                            .trim(),

                    beginShapeIndex =
                        maneuver
                            .beginShapeIndex,

                    endShapeIndex =
                        maneuver
                            .endShapeIndex,

                    stage =
                        stage,
                ),

            signal =
                signal,

            intensity =
                preferences
                    .hapticIntensity,
        )
    }

    private fun cueStage(
        distanceMeters:
            Double,

        leadTime:
            InstructionLeadTimePreference,

        maneuverType:
            ManeuverType,

        repeatCriticalInstructions:
            Boolean,
    ): NavigationHapticCueStage? {
        if (
            NavigationCriticalGuidanceRepeatPolicy
                .shouldRepeat(
                    enabled =
                        repeatCriticalInstructions,

                    maneuverType =
                        maneuverType,

                    distanceMeters =
                        distanceMeters,
                )
        ) {
            return NavigationHapticCueStage
                .CriticalRepeat
        }

        val thresholds =
            when (
                leadTime
            ) {
                InstructionLeadTimePreference.Late ->
                    25.0 to
                        120.0

                InstructionLeadTimePreference.Standard ->
                    35.0 to
                        180.0

                InstructionLeadTimePreference.Early ->
                    45.0 to
                        250.0
            }

        val nowDistance =
            thresholds.first

        val prepareDistance =
            thresholds.second

        return when {
            distanceMeters <=
                nowDistance ->
                NavigationHapticCueStage
                    .Now

            distanceMeters <=
                prepareDistance ->
                NavigationHapticCueStage
                    .Prepare

            else ->
                null
        }
    }

    private val ACTIONABLE_MANEUVER_TYPES =
        setOf(
            ManeuverType.TurnLeft,
            ManeuverType.TurnRight,
            ManeuverType.UTurn,
            ManeuverType.Merge,
            ManeuverType.Exit,
            ManeuverType.RoundaboutEnter,
            ManeuverType.RoundaboutExit,
            ManeuverType.Arrive,
        )
}