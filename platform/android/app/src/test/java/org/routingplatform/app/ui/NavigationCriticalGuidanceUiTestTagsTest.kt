package org.routingplatform.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationCriticalGuidanceUiTestTagsTest {

    @Test
    fun criticalGuidanceTagsAreStableUniqueAndNamespaced() {
        assertEquals(
            "rp.navigation.critical_guidance_settings_open",
            NavigationUiTestTags
                .CriticalGuidanceSettingsOpen,
        )

        assertEquals(
            "rp.navigation.critical_guidance_settings_dialog",
            NavigationUiTestTags
                .CriticalGuidanceSettingsDialog,
        )

        assertEquals(
            "rp.navigation.repeat_critical_toggle",
            NavigationUiTestTags
                .RepeatCriticalToggle,
        )

        assertEquals(
            "rp.navigation.critical_guidance_save",
            NavigationUiTestTags
                .CriticalGuidanceSave,
        )

        val tags =
            listOf(
                NavigationUiTestTags
                    .CriticalGuidanceSettingsOpen,
                NavigationUiTestTags
                    .CriticalGuidanceSettingsDialog,
                NavigationUiTestTags
                    .RepeatCriticalToggle,
                NavigationUiTestTags
                    .CriticalGuidanceSave,
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
