package org.routingplatform.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Test
import org.routingplatform.app.profile.DrivingPreferences
import org.routingplatform.app.profile.DrivingStylePreference
import org.routingplatform.app.profile.RouteStabilityPreference
import org.routingplatform.app.profile.RouteStylePreference

class DrivingRouteFamilyResolverTest {
    @Test fun savedPreferencesSelectSupportedRouteFamilies() {
        assertEquals(NavigationRouteFamily.MajorRoads, routeFamilyForDrivingPreferences(DrivingPreferences(preferMajorRoads = true)))
        assertEquals(NavigationRouteFamily.Comfort, routeFamilyForDrivingPreferences(DrivingPreferences(avoidComplexTurns = true)))
        assertEquals(NavigationRouteFamily.Stable, routeFamilyForDrivingPreferences(DrivingPreferences(routeStability = RouteStabilityPreference.Stable)))
        assertEquals(NavigationRouteFamily.Fastest, routeFamilyForDrivingPreferences(DrivingPreferences(style = DrivingStylePreference.Direct)))
        assertEquals(NavigationRouteFamily.Comfort, routeFamilyForDrivingPreferences(DrivingPreferences(style = DrivingStylePreference.Relaxed)))
        assertEquals(NavigationRouteFamily.ProfileOptimal, routeFamilyForDrivingPreferences(DrivingPreferences()))
    }

    @Test fun majorRoadPreferenceWinsConflictingChoices() {
        assertEquals(
            NavigationRouteFamily.MajorRoads,
            routeFamilyForDrivingPreferences(DrivingPreferences(routeStyle = RouteStylePreference.MajorRoads, avoidComplexTurns = true)),
        )
    }
}
