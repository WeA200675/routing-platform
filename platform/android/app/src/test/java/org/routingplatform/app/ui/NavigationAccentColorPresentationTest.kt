package org.routingplatform.app.ui

import androidx.compose.material3.lightColorScheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.routingplatform.app.profile.ProfileAccentColor

class NavigationAccentColorPresentationTest {

    @Test
    fun labelsCoverAllTenPresets() {
        assertEquals(
            10,
            NavigationAccentColorPresentation
                .presets
                .size,
        )

        assertEquals(
            "Standard",
            NavigationAccentColorPresentation
                .label(
                    ProfileAccentColor.Standard
                ),
        )

        assertEquals(
            "Türkis",
            NavigationAccentColorPresentation
                .label(
                    ProfileAccentColor.Teal
                ),
        )

        assertEquals(
            "Regenbogen",
            NavigationAccentColorPresentation
                .label(
                    ProfileAccentColor.Rainbow
                ),
        )
    }

    @Test
    fun standardPreservesMaterialDefaultPrimary() {
        assertEquals(
            lightColorScheme()
                .primary,

            NavigationAccentColorPresentation
                .colorScheme(
                    preference =
                        ProfileAccentColor.Standard,

                    dark =
                        false,
                )
                .primary,
        )
    }

    @Test
    fun explicitBlueChangesPrimaryColor() {
        assertNotEquals(
            lightColorScheme()
                .primary,

            NavigationAccentColorPresentation
                .colorScheme(
                    preference =
                        ProfileAccentColor.Blue,

                    dark =
                        false,
                )
                .primary,
        )
    }

    @Test
    fun rainbowCyclesDeterministically() {
        val first =
            NavigationAccentColorPresentation
                .rainbowColor(
                    0,
                    false,
                )

        val wrapped =
            NavigationAccentColorPresentation
                .rainbowColor(
                    8,
                    false,
                )

        assertEquals(
            first,
            wrapped,
        )
    }
}