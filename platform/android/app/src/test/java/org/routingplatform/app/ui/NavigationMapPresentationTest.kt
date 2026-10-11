package org.routingplatform.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.routingplatform.app.profile.DisplayPreferences
import org.routingplatform.app.profile.ProfileMapStyle

class NavigationMapPresentationTest {

    @Test
    fun everyProfileMapStyleUsesDistinctHttpsStreetStyle() {
        val descriptors =
            ProfileMapStyle
                .values()
                .map {
                    NavigationMapPresentation
                        .styleDescriptor(
                            it
                        )
                }

        assertEquals(
            ProfileMapStyle
                .values()
                .size,

            descriptors
                .map {
                    it.styleUri
                }
                .toSet()
                .size,
        )

        assertTrue(
            descriptors.all {
                it.styleUri
                    .startsWith(
                        "https://"
                    )
            }
        )
    }

    @Test
    fun standardProfileUsesLibertyStreetStyle() {
        val descriptor =
            NavigationMapPresentation
                .styleDescriptor(
                    ProfileMapStyle.Standard
                )

        assertEquals(
            "https://tiles.openfreemap.org/styles/liberty",
            descriptor.styleUri,
        )
    }

    @Test
    fun nightProfileUsesDarkStreetStyle() {
        val descriptor =
            NavigationMapPresentation
                .styleDescriptor(
                    ProfileMapStyle.Night
                )

        assertEquals(
            "https://tiles.openfreemap.org/styles/dark",
            descriptor.styleUri,
        )
    }

    @Test
    fun routeLineScaleChangesOnlyPresentationWidth() {
        val normal =
            DisplayPreferences(
                routeLineScale =
                    1.0
            )

        val large =
            DisplayPreferences(
                routeLineScale =
                    2.0
            )

        assertEquals(
            6.0f,
            NavigationMapPresentation
                .previewRouteLineWidth(
                    normal
                ),
            0.001f,
        )

        assertEquals(
            12.0f,
            NavigationMapPresentation
                .previewRouteLineWidth(
                    large
                ),
            0.001f,
        )

        assertEquals(
            14.0f,
            NavigationMapPresentation
                .activeRouteLineWidth(
                    large
                ),
            0.001f,
        )
    }

    @Test
    fun loadStateHasExplicitUserVisibleStatus() {
        assertEquals(
            "Karte: Straßenkarte aktiv",
            NavigationMapPresentation
                .mapStatusText(
                    NavigationMapLoadState.Ready
                ),
        )

        assertEquals(
            "Karte: Laden fehlgeschlagen",
            NavigationMapPresentation
                .mapStatusText(
                    NavigationMapLoadState.Failed
                ),
        )
    }
}