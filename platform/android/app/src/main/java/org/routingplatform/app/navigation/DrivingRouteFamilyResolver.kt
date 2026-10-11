package org.routingplatform.app.navigation

import org.routingplatform.app.profile.DrivingPreferences
import org.routingplatform.app.profile.DrivingStylePreference
import org.routingplatform.app.profile.RouteStabilityPreference
import org.routingplatform.app.profile.RouteStylePreference

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
