package org.routingplatform.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationRouteLineScaleUiTestTagsTest {

    @Test
    fun routeLineScaleTagsAreStableUniqueAndNamespaced() {
        assertEquals(
            "rp.navigation.route_line_scale_settings_open",
            NavigationUiTestTags.RouteLineScaleSettingsOpen,
        )

        assertEquals(
            "rp.navigation.route_line_scale_settings_dialog",
            NavigationUiTestTags.RouteLineScaleSettingsDialog,
        )

        assertEquals(
            "rp.navigation.route_line_scale_save",
            NavigationUiTestTags.RouteLineScaleSave,
        )

        assertEquals(
            "rp.navigation.route_line_scale_preset.150",
            NavigationUiTestTags
                .routeLineScalePreset(150),
        )

        val tags =
            listOf(
                NavigationUiTestTags.RouteLineScaleSettingsOpen,
                NavigationUiTestTags.RouteLineScaleSettingsDialog,
                NavigationUiTestTags.RouteLineScaleSave,
                NavigationUiTestTags.routeLineScalePreset(75),
                NavigationUiTestTags.routeLineScalePreset(100),
                NavigationUiTestTags.routeLineScalePreset(125),
                NavigationUiTestTags.routeLineScalePreset(150),
                NavigationUiTestTags.routeLineScalePreset(175),
                NavigationUiTestTags.routeLineScalePreset(200),
            )

        assertEquals(
            tags.size,
            tags.toSet().size,
        )

        assertTrue(
            tags.all {
                it.startsWith("rp.navigation.")
            }
        )
    }
}