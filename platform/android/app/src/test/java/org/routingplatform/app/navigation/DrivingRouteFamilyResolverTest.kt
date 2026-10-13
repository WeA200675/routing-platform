package org.routingplatform.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Test
import org.routingplatform.app.profile.DrivingPreferences
import org.routingplatform.app.profile.DrivingStylePreference
import org.routingplatform.app.profile.RouteStabilityPreference
import org.routingplatform.app.profile.RouteStylePreference
import org.routingplatform.app.profile.ExperiencePackCatalog
import org.routingplatform.app.profile.NavigationPersonalityPreferences
import org.routingplatform.app.profile.ExperiencePackSelectionSource
import org.routingplatform.app.profile.WeeklyDiscoveryIntensity

class DrivingRouteFamilyResolverTest {
    @Test fun savedPreferencesSelectSupportedRouteFamilies() {
        assertEquals(NavigationRouteFamily.MajorRoads, routeFamilyForDrivingPreferences(DrivingPreferences(preferMajorRoads = true)))
        assertEquals(NavigationRouteFamily.Comfort, routeFamilyForDrivingPreferences(DrivingPreferences(avoidComplexTurns = true)))
        assertEquals(NavigationRouteFamily.Stable, routeFamilyForDrivingPreferences(DrivingPreferences(routeStability = RouteStabilityPreference.Stable)))
        assertEquals(NavigationRouteFamily.Fastest, routeFamilyForDrivingPreferences(DrivingPreferences(style = DrivingStylePreference.Direct)))
        assertEquals(NavigationRouteFamily.Comfort, routeFamilyForDrivingPreferences(DrivingPreferences(style = DrivingStylePreference.Relaxed)))
        assertEquals(NavigationRouteFamily.ProfileOptimal, routeFamilyForDrivingPreferences(DrivingPreferences()))
    }

    @Test fun activeExperiencePackSuggestsItsRouteFamily() {
        val expected = mapOf(
            ExperiencePackCatalog.CLASSIC_PACK_ID to NavigationRouteFamily.ProfileOptimal,
            ExperiencePackCatalog.ZEN_PACK_ID to NavigationRouteFamily.Comfort,
            "cozy" to NavigationRouteFamily.Scenic,
            "focus" to NavigationRouteFamily.LowCurvature,
            ExperiencePackCatalog.GALACTIC_PACK_ID to NavigationRouteFamily.LowUrban,
        )

        expected.forEach { (packId, family) ->
            val prefs = NavigationPersonalityPreferences(
                selectedPackId = packId,
                selectionSource = ExperiencePackSelectionSource.Explicit,
            )
            assertEquals(family, routeFamilyForActiveExperiencePack(prefs))
        }
    }

    @Test fun weeklyPackSuggestsItsRouteFamilyAndDisabledDefaultHasNoOverride() {
        val prefs = NavigationPersonalityPreferences(
            selectedPackId = ExperiencePackCatalog.CLASSIC_PACK_ID,
            weeklyDiscoveryEnabled = true,
            weeklyDiscoveryIntensity = WeeklyDiscoveryIntensity.Wild,
        )
        val weeklyPack = org.routingplatform.app.profile.WeeklyExperiencePackSelector.select(
            weekKey = "2026-W37",
            currentPackId = ExperiencePackCatalog.CLASSIC_PACK_ID,
            intensity = WeeklyDiscoveryIntensity.Wild,
        )
        val expectedFamily = when (weeklyPack.routeStyle) {
            org.routingplatform.app.profile.ExperiencePackRouteStyle.Balanced ->
                NavigationRouteFamily.ProfileOptimal
            org.routingplatform.app.profile.ExperiencePackRouteStyle.Comfort ->
                NavigationRouteFamily.Comfort
            org.routingplatform.app.profile.ExperiencePackRouteStyle.Scenic ->
                NavigationRouteFamily.Scenic
            org.routingplatform.app.profile.ExperiencePackRouteStyle.LowCurvature ->
                NavigationRouteFamily.LowCurvature
            org.routingplatform.app.profile.ExperiencePackRouteStyle.LowUrban ->
                NavigationRouteFamily.LowUrban
        }

        assertEquals(
            expectedFamily,
            routeFamilyForActiveExperiencePack(prefs, weekKey = "2026-W37"),
        )
        assertEquals(
            null,
            routeFamilyForActiveExperiencePack(NavigationPersonalityPreferences()),
        )
    }

    @Test fun majorRoadPreferenceWinsConflictingChoices() {
        assertEquals(
            NavigationRouteFamily.MajorRoads,
            routeFamilyForDrivingPreferences(DrivingPreferences(routeStyle = RouteStylePreference.MajorRoads, avoidComplexTurns = true)),
        )
    }
}
