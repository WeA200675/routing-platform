package org.routingplatform.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import org.routingplatform.app.profile.ProfileAppearance

@Composable
fun RoutingPlatformTheme(
    appearance:
        ProfileAppearance =
        ProfileAppearance.System,

    automaticNight:
        Boolean =
        false,

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