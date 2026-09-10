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

    const val PrimaryControlMoveSide =
        "rp.navigation.primary_control_move_side"

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

    const val VoiceSettingsOpen =
        "rp.navigation.voice_settings_open"

    const val VoiceSettingsDialog =
        "rp.navigation.voice_settings_dialog"

    const val VoicePreview =
        "rp.navigation.voice_preview"

    const val VoiceSave =
        "rp.navigation.voice_save"

    const val CameraZoomSettingsOpen =
        "rp.navigation.camera_zoom_settings_open"

    const val CameraZoomSettingsDialog =
        "rp.navigation.camera_zoom_settings_dialog"

    const val CameraAutoZoomToggle =
        "rp.navigation.camera_auto_zoom_toggle"

    const val CameraZoomSave =
        "rp.navigation.camera_zoom_save"

    const val MapOrientationSettingsOpen =
        "rp.navigation.map_orientation_settings_open"

    const val MapOrientationSettingsDialog =
        "rp.navigation.map_orientation_settings_dialog"

    const val MapOrientationSave =
        "rp.navigation.map_orientation_save"

    const val TextScaleSettingsOpen =
        "rp.navigation.text_scale_settings_open"

    const val TextScaleSettingsDialog =
        "rp.navigation.text_scale_settings_dialog"

    const val TextScaleSave =
        "rp.navigation.text_scale_save"

    const val InformationDensitySettingsOpen =
        "rp.navigation.information_density_settings_open"

    const val InformationDensitySettingsDialog =
        "rp.navigation.information_density_settings_dialog"

    const val InformationDensitySave =
        "rp.navigation.information_density_save"

    const val RouteLineScaleSettingsOpen =
        "rp.navigation.route_line_scale_settings_open"

    const val RouteLineScaleSettingsDialog =
        "rp.navigation.route_line_scale_settings_dialog"

    const val RouteLineScaleSave =
        "rp.navigation.route_line_scale_save"

    const val CriticalGuidanceSettingsOpen =
        "rp.navigation.critical_guidance_settings_open"

    const val CriticalGuidanceSettingsDialog =
        "rp.navigation.critical_guidance_settings_dialog"

    const val RepeatCriticalToggle =
        "rp.navigation.repeat_critical_toggle"

    const val CriticalGuidanceSave =
        "rp.navigation.critical_guidance_save"

    const val HapticSettingsOpen =
        "rp.navigation.haptic_settings_open"

    const val HapticSettingsDialog =
        "rp.navigation.haptic_settings_dialog"

    const val HapticPreview =
        "rp.navigation.haptic_preview"

    const val HapticSave =
        "rp.navigation.haptic_save"

    fun primaryControlSide(
        sideName:
            String,
    ): String {
        require(
            sideName in
                setOf(
                    "Left",
                    "Right",
                )
        )

        return "rp.navigation.primary_control_side." +
            sideName
    }

    fun mapOrientationOption(
        orientationName:
            String,
    ): String {
        require(
            orientationName in
                setOf(
                    "HeadingUp",
                    "NorthUp",
                )
        )

        return "rp.navigation.map_orientation_option." +
            orientationName
    }
    fun routeLineScalePreset(
        percent:
            Int,
    ): String {
        require(
            percent in
                setOf(
                    75,
                    100,
                    125,
                    150,
                    175,
                    200,
                )
        )

        return "rp.navigation.route_line_scale_preset." +
            percent
    }

    fun informationDensityOption(
        preferenceName:
            String,
    ): String {
        require(
            preferenceName in
                setOf(
                    "Minimal",
                    "Standard",
                    "Detailed",
                )
        )

        return "rp.navigation.information_density_option." +
            preferenceName
    }

    fun textScalePreset(
        percent:
            Int,
    ): String {
        require(
            percent in
                80..150
        )

        return "rp.navigation.text_scale_preset." +
            percent
    }

    fun hapticIntensity(
        intensityName:
            String,
    ): String {
        require(
            intensityName.isNotBlank()
        )

        return "rp.navigation.haptic_intensity." +
            intensityName
    }

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

    fun voiceLanguage(
        languageTag:
            String,
    ): String {
        require(
            languageTag.isNotBlank()
        )

        return "rp.navigation.voice_language." +
            languageTag
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