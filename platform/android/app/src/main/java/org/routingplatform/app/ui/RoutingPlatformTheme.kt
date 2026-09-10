package org.routingplatform.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import org.routingplatform.app.profile.ProfileAppearance

@Composable
fun RoutingPlatformTheme(
    appearance:
        ProfileAppearance =
        ProfileAppearance.System,

    automaticNight:
        Boolean =
        false,

    textScale:
        Double =
        1.0,

    content:
        @Composable () -> Unit,
) {
    val systemDark =
        isSystemInDarkTheme()

    val profileDark =
        when (appearance) {
            ProfileAppearance.System ->
                systemDark

            ProfileAppearance.Light ->
                false

            ProfileAppearance.Dark ->
                true
        }

    val baseDensity =
        LocalDensity.current

    val effectiveFontScale =
        NavigationTextScalePresentation
            .resolve(
                systemFontScale =
                    baseDensity.fontScale,

                profileTextScale =
                    textScale,
            )

    val presentationDensity =
        Density(
            density =
                baseDensity.density,

            fontScale =
                effectiveFontScale,
        )

    CompositionLocalProvider(
        LocalDensity provides
            presentationDensity,
    ) {
        MaterialTheme(
            colorScheme =
                if (
                    automaticNight ||
                    profileDark
                ) {
                    darkColorScheme()
                } else {
                    lightColorScheme()
                },

            content =
                content,
        )
    }
}