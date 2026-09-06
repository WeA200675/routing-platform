package org.routingplatform.app.ui

import org.routingplatform.app.profile.DisplayPreferences
import org.routingplatform.app.profile.ProfileMapStyle

/*
 * Presentation-only map policy.
 *
 * No routing, map matching, route progress or positioning decisions
 * are made here. The map only renders already-produced state.
 */
internal data class NavigationMapStyleDescriptor(
    val styleUri:
        String,
)

internal enum class NavigationMapLoadState {
    Loading,
    Rendering,
    Ready,
    Failed,
}

internal object NavigationMapPresentation {

    fun styleDescriptor(
        mapStyle:
            ProfileMapStyle,
    ): NavigationMapStyleDescriptor =
        NavigationMapStyleDescriptor(
            styleUri =
                when (mapStyle) {
                    ProfileMapStyle.Standard ->
                        "https://tiles.openfreemap.org/styles/liberty"

                    ProfileMapStyle.Minimal ->
                        "https://tiles.openfreemap.org/styles/positron"

                    ProfileMapStyle.HighContrast ->
                        "https://tiles.openfreemap.org/styles/bright"

                    ProfileMapStyle.Night ->
                        "https://tiles.openfreemap.org/styles/dark"
                }
        )

    fun previewRouteLineWidth(
        preferences:
            DisplayPreferences,
    ): Float =
        (
            6.0 *
                preferences.routeLineScale
        ).toFloat()

    fun activeRouteLineWidth(
        preferences:
            DisplayPreferences,
    ): Float =
        (
            7.0 *
                preferences.routeLineScale
        ).toFloat()

    fun mapStatusText(
        state:
            NavigationMapLoadState,
    ): String =
        when (state) {
            NavigationMapLoadState.Loading ->
                "Karte: Straßenstil wird geladen"

            NavigationMapLoadState.Rendering ->
                "Karte: Straßen werden geladen"

            NavigationMapLoadState.Ready ->
                "Karte: Straßenkarte aktiv"

            NavigationMapLoadState.Failed ->
                "Karte: Laden fehlgeschlagen"
        }
}