package org.routingplatform.app.ui

import org.routingplatform.app.navigation.ManeuverType

/*
 * G6.12 critical-guidance repeat policy.
 *
 * This policy classifies only already-produced maneuver presentation truth.
 * It cannot alter route geometry, route progress, positioning, rerouting,
 * candidate selection, cost evaluation, or any safety state.
 */
internal object NavigationCriticalGuidanceRepeatPolicy {

    fun isCritical(
        maneuverType:
            ManeuverType,
    ): Boolean =
        maneuverType in
            CRITICAL_MANEUVER_TYPES

    fun shouldRepeat(
        enabled:
            Boolean,

        maneuverType:
            ManeuverType,

        distanceMeters:
            Double,
    ): Boolean {
        if (
            !enabled ||
            !distanceMeters.isFinite() ||
            distanceMeters <
                0.0
        ) {
            return false
        }

        return isCritical(
            maneuverType
        ) &&
            distanceMeters <=
            CRITICAL_REPEAT_DISTANCE_METERS
    }

    internal const val CRITICAL_REPEAT_DISTANCE_METERS =
        15.0

    private val CRITICAL_MANEUVER_TYPES =
        setOf(
            ManeuverType.UTurn,
            ManeuverType.Exit,
            ManeuverType.RoundaboutEnter,
            ManeuverType.RoundaboutExit,
        )
}
