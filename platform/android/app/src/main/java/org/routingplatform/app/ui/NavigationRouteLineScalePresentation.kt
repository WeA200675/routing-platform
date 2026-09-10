package org.routingplatform.app.ui

import kotlin.math.roundToInt

/*
 * G6.15 navigation route-line scale presentation policy.
 *
 * This policy only resolves visual stroke width and user-facing percentages.
 * It cannot alter route geometry, route selection, positioning, progress,
 * rerouting, confidence, permissions or any safety-critical runtime state.
 */
internal object NavigationRouteLineScalePresentation {
    private const val MinimumScale =
        0.75

    private const val MaximumScale =
        2.0

    private const val PreviewBaseWidth =
        6.0

    private const val ActiveBaseWidth =
        7.0

    fun percent(
        routeLineScale:
            Double,
    ): Int {
        requireValidScale(
            routeLineScale
        )

        return (
            routeLineScale *
                100.0
        ).roundToInt()
    }

    fun scaleForPercent(
        percent:
            Int,
    ): Double {
        require(
            percent in
                setOf(
                    75,
                    100,
                    125,
                    150,
                    175,
                    200,
                )
        ) {
            "Unsupported route-line scale preset."
        }

        return percent /
            100.0
    }

    fun previewWidth(
        routeLineScale:
            Double,
    ): Float {
        requireValidScale(
            routeLineScale
        )

        return (
            PreviewBaseWidth *
                routeLineScale
        ).toFloat()
    }

    fun activeWidth(
        routeLineScale:
            Double,
    ): Float {
        requireValidScale(
            routeLineScale
        )

        return (
            ActiveBaseWidth *
                routeLineScale
        ).toFloat()
    }

    private fun requireValidScale(
        routeLineScale:
            Double,
    ) {
        require(
            routeLineScale.isFinite() &&
                routeLineScale in
                    MinimumScale..MaximumScale
        ) {
            "routeLineScale must be finite and in [0.75, 2.0]."
        }
    }
}