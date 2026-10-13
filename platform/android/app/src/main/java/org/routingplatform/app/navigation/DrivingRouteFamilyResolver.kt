package org.routingplatform.app.navigation

import org.routingplatform.app.profile.DrivingPreferences
import org.routingplatform.app.profile.DrivingStylePreference
import org.routingplatform.app.profile.RouteStabilityPreference
import org.routingplatform.app.profile.RouteStylePreference
import org.routingplatform.app.profile.ExperiencePackRouteStyle
import org.routingplatform.app.profile.ExperiencePackRuntimeResolver
import org.routingplatform.app.profile.NavigationPersonalityPreferences

/** Maps saved driver choices to a supported route family. Explicit intent choices remain authoritative. */
fun routeFamilyForDrivingPreferences(preferences: DrivingPreferences): NavigationRouteFamily =
    when {
        preferences.preferMajorRoads || preferences.routeStyle == RouteStylePreference.MajorRoads -> NavigationRouteFamily.MajorRoads
        preferences.avoidComplexTurns || preferences.routeStyle == RouteStylePreference.SimpleManeuvers -> NavigationRouteFamily.Comfort
        preferences.routeStability == RouteStabilityPreference.Stable || preferences.routeStyle == RouteStylePreference.Stable -> NavigationRouteFamily.Stable
        preferences.style == DrivingStylePreference.Direct || preferences.routeStability == RouteStabilityPreference.Responsive -> NavigationRouteFamily.Fastest
        preferences.style == DrivingStylePreference.Relaxed -> NavigationRouteFamily.Comfort
        else -> NavigationRouteFamily.ProfileOptimal
    }


/**
 * Themes can suggest a non-authoritative route family for the next preview.
 * Turn-by-turn geometry, GPS progress and maneuver truth remain owned by the
 * routing engine and the navigation safety gates.
 */
fun routeFamilyForActiveExperiencePack(
    personality: NavigationPersonalityPreferences,
    weekKey: String = ExperiencePackRuntimeResolver.currentWeekKey(),
): NavigationRouteFamily? =
    ExperiencePackRuntimeResolver
        .resolvePackOverride(
            personality = personality,
            weekKey = weekKey,
        )
        ?.routeStyle
        ?.let { style ->
            when (style) {
                ExperiencePackRouteStyle.Balanced ->
                    NavigationRouteFamily.ProfileOptimal
                ExperiencePackRouteStyle.Comfort ->
                    NavigationRouteFamily.Comfort
                ExperiencePackRouteStyle.Scenic ->
                    NavigationRouteFamily.Scenic
                ExperiencePackRouteStyle.LowCurvature ->
                    NavigationRouteFamily.LowCurvature
                ExperiencePackRouteStyle.LowUrban ->
                    NavigationRouteFamily.LowUrban
            }
        }
