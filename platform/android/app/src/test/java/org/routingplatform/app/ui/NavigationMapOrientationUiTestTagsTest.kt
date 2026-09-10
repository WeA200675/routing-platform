package org.routingplatform.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationMapOrientationUiTestTagsTest {

    @Test
    fun mapOrientationTagsAreStableUniqueAndNamespaced() {
        assertEquals(
            "rp.navigation.map_orientation_settings_open",
            NavigationUiTestTags.MapOrientationSettingsOpen,
        )

        assertEquals(
            "rp.navigation.map_orientation_settings_dialog",
            NavigationUiTestTags.MapOrientationSettingsDialog,
        )

        assertEquals(
            "rp.navigation.map_orientation_save",
            NavigationUiTestTags.MapOrientationSave,
        )

        assertEquals(
            "rp.navigation.map_orientation_option.HeadingUp",
            NavigationUiTestTags
                .mapOrientationOption("HeadingUp"),
        )

        assertEquals(
            "rp.navigation.map_orientation_option.NorthUp",
            NavigationUiTestTags
                .mapOrientationOption("NorthUp"),
        )

        val tags =
            listOf(
                NavigationUiTestTags.MapOrientationSettingsOpen,
                NavigationUiTestTags.MapOrientationSettingsDialog,
                NavigationUiTestTags.MapOrientationSave,
                NavigationUiTestTags
                    .mapOrientationOption("HeadingUp"),
                NavigationUiTestTags
                    .mapOrientationOption("NorthUp"),
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
