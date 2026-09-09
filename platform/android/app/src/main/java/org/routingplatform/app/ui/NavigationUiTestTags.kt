package org.routingplatform.app.ui

import org.routingplatform.app.navigation.NavigationFaultCode
import org.routingplatform.app.navigation.NavigationRouteAcquisitionState
import org.routingplatform.app.navigation.NavigationSessionState

/*
 * Stable, non-localized UI automation anchors.
 *
 * testTagsAsResourceId is enabled once at the NavigationScreen root,
 * so these tags are available to UIAutomator as resource-id values.
 *
 * Tags are test/presentation metadata only. They carry no routing,
 * positioning, progress or safety authority.
 */
internal object NavigationUiTestTags {
    const val Root =
        "rp.navigation.root"

    const val RouteId =
        "rp.navigation.route_id"

    const val PrimaryAction =
        "rp.navigation.primary_action"

    const val PlannerOpen =
        "rp.navigation.planner_open"

    const val PlannerDialog =
        "rp.navigation.planner_dialog"

    const val SearchField =
        "rp.navigation.search_field"

    const val SearchAction =
        "rp.navigation.search_action"

    const val UseDestination =
        "rp.navigation.use_destination"

    const val AppendVia =
        "rp.navigation.append_via"

    const val CustomFavoriteField =
        "rp.navigation.custom_favorite_field"

    const val CustomFavoriteSave =
        "rp.navigation.custom_favorite_save"

    const val ExperiencePackOpen =
        "rp.navigation.experience_pack_open"

    const val ExperiencePackDialog =
        "rp.navigation.experience_pack_dialog"

    const val WeeklyDiscoveryToggle =
        "rp.navigation.weekly_discovery_toggle"

    fun experiencePack(
        packId:
            String,
    ): String {
        require(
            packId.isNotBlank()
        )

        return "rp.navigation.experience_pack." +
            packId
    }

    fun weeklyIntensity(
        intensityName:
            String,
    ): String {
        require(
            intensityName.isNotBlank()
        )

        return "rp.navigation.weekly_intensity." +
            intensityName
    }

    fun sessionState(
        state:
            NavigationSessionState,
    ): String =
        "rp.navigation.session." +
            state.name

    fun routeAcquisition(
        state:
            NavigationRouteAcquisitionState,

        faultCode:
            NavigationFaultCode?,
    ): String =
        "rp.navigation.acquisition." +
            state.name +
            "." +
            (
                faultCode
                    ?.name
                    ?: "None"
            )

    fun searchResult(
        index:
            Int,
    ): String {
        require(
            index >=
                0
        )

        return "rp.navigation.search_result." +
            index
    }
}