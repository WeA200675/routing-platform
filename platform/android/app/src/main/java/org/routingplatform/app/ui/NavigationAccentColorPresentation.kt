package org.routingplatform.app.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import org.routingplatform.app.profile.ProfileAccentColor

internal object NavigationAccentColorPresentation {

    val presets:
        List<ProfileAccentColor> =
        ProfileAccentColor
            .values()
            .toList()

    fun label(
        preference:
            ProfileAccentColor,
    ): String =
        when (preference) {
            ProfileAccentColor.Standard ->
                "Standard"

            ProfileAccentColor.Blue ->
                "Blau"

            ProfileAccentColor.Teal ->
                "Türkis"

            ProfileAccentColor.Green ->
                "Grün"

            ProfileAccentColor.Yellow ->
                "Gelb"

            ProfileAccentColor.Orange ->
                "Orange"

            ProfileAccentColor.Red ->
                "Rot"

            ProfileAccentColor.Pink ->
                "Pink"

            ProfileAccentColor.Purple ->
                "Lila"

            ProfileAccentColor.Rainbow ->
                "Regenbogen"
        }

    fun primaryColor(
        preference:
            ProfileAccentColor,

        dark:
            Boolean,
    ): Color? =
        when (preference) {
            ProfileAccentColor.Standard ->
                null

            ProfileAccentColor.Blue ->
                if (dark) {
                    Color(0xFF90CAF9)
                } else {
                    Color(0xFF1565C0)
                }

            ProfileAccentColor.Teal ->
                if (dark) {
                    Color(0xFF80CBC4)
                } else {
                    Color(0xFF00796B)
                }

            ProfileAccentColor.Green ->
                if (dark) {
                    Color(0xFFA5D6A7)
                } else {
                    Color(0xFF2E7D32)
                }

            ProfileAccentColor.Yellow ->
                if (dark) {
                    Color(0xFFFFE082)
                } else {
                    Color(0xFF8A6100)
                }

            ProfileAccentColor.Orange ->
                if (dark) {
                    Color(0xFFFFB74D)
                } else {
                    Color(0xFFC2410C)
                }

            ProfileAccentColor.Red ->
                if (dark) {
                    Color(0xFFEF9A9A)
                } else {
                    Color(0xFFC62828)
                }

            ProfileAccentColor.Pink ->
                if (dark) {
                    Color(0xFFF48FB1)
                } else {
                    Color(0xFFAD1457)
                }

            ProfileAccentColor.Purple ->
                if (dark) {
                    Color(0xFFCE93D8)
                } else {
                    Color(0xFF6A1B9A)
                }

            ProfileAccentColor.Rainbow ->
                rainbowColor(
                    index =
                        0,

                    dark =
                        dark,
                )
        }

    fun rainbowColor(
        index:
            Int,

        dark:
            Boolean,
    ): Color {
        require(
            index >=
                0
        )

        val palette =
            if (dark) {
                DARK_RAINBOW
            } else {
                LIGHT_RAINBOW
            }

        return palette[
            index %
                palette.size
        ]
    }

    fun tileColor(
        preference:
            ProfileAccentColor,

        index:
            Int,

        dark:
            Boolean,

        fallback:
            Color,
    ): Color =
        when (preference) {
            ProfileAccentColor.Standard ->
                fallback

            ProfileAccentColor.Rainbow ->
                rainbowColor(
                    index =
                        index,

                    dark =
                        dark,
                )

            else ->
                checkNotNull(
                    primaryColor(
                        preference =
                            preference,

                        dark =
                            dark,
                    )
                )
        }

    fun colorScheme(
        preference:
            ProfileAccentColor,

        dark:
            Boolean,
    ): ColorScheme {
        if (
            preference ==
                ProfileAccentColor.Standard
        ) {
            return if (dark) {
                darkColorScheme()
            } else {
                lightColorScheme()
            }
        }

        if (
            preference ==
                ProfileAccentColor.Rainbow
        ) {
            val primary =
                rainbowColor(
                    0,
                    dark,
                )

            val secondary =
                rainbowColor(
                    3,
                    dark,
                )

            val tertiary =
                rainbowColor(
                    6,
                    dark,
                )

            val onAccent =
                if (dark) {
                    Color.Black
                } else {
                    Color.White
                }

            return if (dark) {
                darkColorScheme(
                    primary =
                        primary,

                    onPrimary =
                        onAccent,

                    secondary =
                        secondary,

                    onSecondary =
                        onAccent,

                    tertiary =
                        tertiary,

                    onTertiary =
                        onAccent,
                )
            } else {
                lightColorScheme(
                    primary =
                        primary,

                    onPrimary =
                        onAccent,

                    secondary =
                        secondary,

                    onSecondary =
                        onAccent,

                    tertiary =
                        tertiary,

                    onTertiary =
                        onAccent,
                )
            }
        }

        val primary =
            checkNotNull(
                primaryColor(
                    preference =
                        preference,

                    dark =
                        dark,
                )
            )

        val onAccent =
            if (dark) {
                Color.Black
            } else {
                Color.White
            }

        return if (dark) {
            darkColorScheme(
                primary =
                    primary,

                onPrimary =
                    onAccent,

                secondary =
                    primary,

                onSecondary =
                    onAccent,

                tertiary =
                    primary,

                onTertiary =
                    onAccent,
            )
        } else {
            lightColorScheme(
                primary =
                    primary,

                onPrimary =
                    onAccent,

                secondary =
                    primary,

                onSecondary =
                    onAccent,

                tertiary =
                    primary,

                onTertiary =
                    onAccent,
            )
        }
    }

    private val LIGHT_RAINBOW =
        listOf(
            Color(0xFFC62828),
            Color(0xFFC2410C),
            Color(0xFF8A6100),
            Color(0xFF2E7D32),
            Color(0xFF00796B),
            Color(0xFF1565C0),
            Color(0xFF4527A0),
            Color(0xFFAD1457),
        )

    private val DARK_RAINBOW =
        listOf(
            Color(0xFFEF9A9A),
            Color(0xFFFFB74D),
            Color(0xFFFFE082),
            Color(0xFFA5D6A7),
            Color(0xFF80CBC4),
            Color(0xFF90CAF9),
            Color(0xFFB39DDB),
            Color(0xFFF48FB1),
        )
}