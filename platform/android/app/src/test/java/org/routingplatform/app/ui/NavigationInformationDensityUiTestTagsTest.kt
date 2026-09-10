package org.routingplatform.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationInformationDensityUiTestTagsTest {

    @Test
    fun informationDensityTagsAreStableUniqueAndNamespaced() {
        assertEquals(
            "rp.navigation.information_density_settings_open",
            NavigationUiTestTags.InformationDensitySettingsOpen,
        )

        assertEquals(
            "rp.navigation.information_density_settings_dialog",
            NavigationUiTestTags.InformationDensitySettingsDialog,
        )

        assertEquals(
            "rp.navigation.information_density_save",
            NavigationUiTestTags.InformationDensitySave,
        )

        assertEquals(
            "rp.navigation.information_density_option.Detailed",
            NavigationUiTestTags
                .informationDensityOption("Detailed"),
        )

        val tags =
            listOf(
                NavigationUiTestTags.InformationDensitySettingsOpen,
                NavigationUiTestTags.InformationDensitySettingsDialog,
                NavigationUiTestTags.InformationDensitySave,
                NavigationUiTestTags.informationDensityOption("Minimal"),
                NavigationUiTestTags.informationDensityOption("Standard"),
                NavigationUiTestTags.informationDensityOption("Detailed"),
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