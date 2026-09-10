package org.routingplatform.app.ui

import kotlin.math.roundToInt

/*
 * G6.13 navigation text-scale presentation policy.
 *
 * This policy only derives an effective Compose font scale from the already
 * accepted Android system font scale and the persisted profile multiplier.
 * It cannot alter density, layout dp geometry, route data, positioning,
 * progress, rerouting, candidate selection, cost evaluation, or safety state.
 */
internal object NavigationTextScalePresentation {

    fun resolve(
        systemFontScale:
            Float,

        profileTextScale:
            Double,
    ): Float {
        if (
            !systemFontScale.isFinite() ||
            systemFontScale <=
                0.0f
        ) {
            return DEFAULT_SYSTEM_FONT_SCALE
        }

        if (
            !isSupportedProfileScale(
                profileTextScale
            )
        ) {
            return systemFontScale
        }

        val resolved =
            systemFontScale *
                profileTextScale.toFloat()

        return if (
            resolved.isFinite() &&
            resolved >
                0.0f
        ) {
            resolved
        } else {
            systemFontScale
        }
    }

    fun percent(
        profileTextScale:
            Double,
    ): Int =
        if (
            isSupportedProfileScale(
                profileTextScale
            )
        ) {
            (
                profileTextScale *
                    100.0
            ).roundToInt()
        } else {
            100
        }

    fun isSupportedProfileScale(
        profileTextScale:
            Double,
    ): Boolean =
        profileTextScale.isFinite() &&
            profileTextScale in
                MIN_PROFILE_TEXT_SCALE..MAX_PROFILE_TEXT_SCALE

    internal const val MIN_PROFILE_TEXT_SCALE =
        0.8

    internal const val MAX_PROFILE_TEXT_SCALE =
        1.5

    private const val DEFAULT_SYSTEM_FONT_SCALE =
        1.0f
}