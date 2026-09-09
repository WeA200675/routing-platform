package org.routingplatform.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationCameraZoomUiTestTagsTest {

    @Test
    fun cameraZoomTagsAreStableUniqueAndNamespaced() {
        assertEquals(
            "rp.navigation.camera_zoom_settings_open",
            NavigationUiTestTags
                .CameraZoomSettingsOpen,
        )

        assertEquals(
            "rp.navigation.camera_zoom_settings_dialog",
            NavigationUiTestTags
                .CameraZoomSettingsDialog,
        )

        assertEquals(
            "rp.navigation.camera_auto_zoom_toggle",
            NavigationUiTestTags
                .CameraAutoZoomToggle,
        )

        assertEquals(
            "rp.navigation.camera_zoom_save",
            NavigationUiTestTags
                .CameraZoomSave,
        )

        val tags =
            listOf(
                NavigationUiTestTags
                    .CameraZoomSettingsOpen,
                NavigationUiTestTags
                    .CameraZoomSettingsDialog,
                NavigationUiTestTags
                    .CameraAutoZoomToggle,
                NavigationUiTestTags
                    .CameraZoomSave,
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
