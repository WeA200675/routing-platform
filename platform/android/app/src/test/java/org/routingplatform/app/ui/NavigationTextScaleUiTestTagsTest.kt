package org.routingplatform.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationTextScaleUiTestTagsTest {

    @Test
    fun textScaleTagsAreStableUniqueAndNamespaced() {
        assertEquals(
            "rp.navigation.text_scale_settings_open",
            NavigationUiTestTags
                .TextScaleSettingsOpen,
        )

        assertEquals(
            "rp.navigation.text_scale_settings_dialog",
            NavigationUiTestTags
                .TextScaleSettingsDialog,
        )

        assertEquals(
            "rp.navigation.text_scale_save",
            NavigationUiTestTags
                .TextScaleSave,
        )

        assertEquals(
            "rp.navigation.text_scale_preset.120",
            NavigationUiTestTags
                .textScalePreset(
                    120
                ),
        )

        val tags =
            listOf(
                NavigationUiTestTags
                    .TextScaleSettingsOpen,
                NavigationUiTestTags
                    .TextScaleSettingsDialog,
                NavigationUiTestTags
                    .TextScaleSave,
                NavigationUiTestTags
                    .textScalePreset(
                        80
                    ),
                NavigationUiTestTags
                    .textScalePreset(
                        120
                    ),
                NavigationUiTestTags
                    .textScalePreset(
                        150
                    ),
            )

        assertEquals(
            tags.size,
            tags.toSet().size,
        )

        assertTrue(
            tags.all {
                it.startsWith(
                    "rp.navigation."
                )
            }
        )
    }
}