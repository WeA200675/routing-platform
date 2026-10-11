package org.routingplatform.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationHapticUiTestTagsTest {

    @Test
    fun hapticTagsAreStableAndNamespaced() {
        assertEquals(
            "rp.navigation.haptic_settings_open",
            NavigationUiTestTags
                .HapticSettingsOpen,
        )

        assertEquals(
            "rp.navigation.haptic_settings_dialog",
            NavigationUiTestTags
                .HapticSettingsDialog,
        )

        assertEquals(
            "rp.navigation.haptic_preview",
            NavigationUiTestTags
                .HapticPreview,
        )

        assertEquals(
            "rp.navigation.haptic_save",
            NavigationUiTestTags
                .HapticSave,
        )

        assertEquals(
            "rp.navigation.haptic_intensity.Strong",
            NavigationUiTestTags
                .hapticIntensity(
                    "Strong"
                ),
        )
    }
}