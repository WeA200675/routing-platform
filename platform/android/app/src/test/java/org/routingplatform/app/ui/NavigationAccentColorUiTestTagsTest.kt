package org.routingplatform.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.routingplatform.app.profile.ProfileAccentColor

class NavigationAccentColorUiTestTagsTest {

    @Test
    fun accentSettingsTagsAreStableAndUnique() {
        assertEquals(
            "rp.navigation.accent_color_settings_open",
            NavigationUiTestTags
                .AccentColorSettingsOpen,
        )

        assertEquals(
            "rp.navigation.accent_color_settings_dialog",
            NavigationUiTestTags
                .AccentColorSettingsDialog,
        )

        assertEquals(
            "rp.navigation.accent_color_save",
            NavigationUiTestTags
                .AccentColorSave,
        )

        val optionTags =
            ProfileAccentColor
                .values()
                .map {
                    NavigationUiTestTags
                        .accentColorOption(
                            it.name
                        )
                }

        assertEquals(
            10,
            optionTags.size,
        )

        assertEquals(
            optionTags.size,
            optionTags
                .toSet()
                .size,
        )

        assertTrue(
            optionTags.all {
                it.startsWith(
                    "rp.navigation.accent_color_option."
                )
            }
        )
    }
}