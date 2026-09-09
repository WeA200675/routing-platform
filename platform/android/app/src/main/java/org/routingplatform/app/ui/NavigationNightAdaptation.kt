package org.routingplatform.app.ui

import kotlin.math.ln
import kotlin.math.roundToInt
import org.routingplatform.app.profile.ProfileMapStyle

internal const val NAVIGATION_BRIGHTNESS_LEVEL_COUNT =
    20

internal const val NAVIGATION_BRIGHTNESS_CORRECTION_MIN =
    -3

internal const val NAVIGATION_BRIGHTNESS_CORRECTION_MAX =
    3

enum class NavigationNightLightSource {
    Sensor,
    AwaitingSensor,
    SystemFallback,
}

/*
 * Presentation-only output of G6.7.
 *
 * No routing, progress, positioning or safety state is represented here.
 */
data class NavigationNightPresentation(
    val active: Boolean,
    val sensorAvailable: Boolean,
    val source: NavigationNightLightSource,
    val smoothedLux: Double?,
    val brightnessLevel: Int?,
    val windowBrightness: Float?,
    val nightMode: Boolean,
    val brightnessCorrection: Int,
) {
    fun effectiveMapStyle(
        base:
            ProfileMapStyle,
    ): ProfileMapStyle =
        if (
            active &&
            nightMode
        ) {
            ProfileMapStyle.Night
        } else {
            base
        }

    fun statusText(): String {
        val sourceText =
            when (source) {
                NavigationNightLightSource.Sensor ->
                    smoothedLux
                        ?.roundToInt()
                        ?.let {
                            "$it lx"
                        }
                        ?: "Sensor"

                NavigationNightLightSource.AwaitingSensor ->
                    "Sensor wird eingemessen"

                NavigationNightLightSource.SystemFallback ->
                    "System-Fallback"
            }

        val levelText =
            brightnessLevel
                ?.let {
                    "  |  $it/$NAVIGATION_BRIGHTNESS_LEVEL_COUNT"
                }
                ?: ""

        val modeText =
            if (nightMode) {
                "  |  Nacht"
            } else {
                "  |  Tag"
            }

        return "Display: Auto  |  $sourceText$levelText$modeText"
    }

    companion object {
        fun inactive(
            systemNightMode:
                Boolean = false,
        ): NavigationNightPresentation =
            NavigationNightPresentation(
                active = false,
                sensorAvailable = false,
                source =
                    NavigationNightLightSource
                        .SystemFallback,
                smoothedLux = null,
                brightnessLevel = null,
                windowBrightness = null,
                nightMode = systemNightMode,
                brightnessCorrection = 0,
            )
    }
}

internal object NavigationNightAdaptationPolicy {
    private const val SMOOTHING_ALPHA =
        0.18

    private const val NIGHT_ENTER_LUX =
        8.0

    private const val NIGHT_EXIT_LUX =
        24.0

    private const val MAX_MAPPED_LUX =
        20_000.0

    private const val MIN_WINDOW_BRIGHTNESS =
        0.10f

    fun normalizeCorrection(
        correction:
            Int,
    ): Int =
        correction.coerceIn(
            NAVIGATION_BRIGHTNESS_CORRECTION_MIN,
            NAVIGATION_BRIGHTNESS_CORRECTION_MAX,
        )

    fun smoothLux(
        previousLux:
            Double?,
        sampleLux:
            Double,
    ): Double {
        require(
            sampleLux.isFinite() &&
                sampleLux >= 0.0
        ) {
            "sampleLux must be finite and non-negative."
        }

        val previous =
            previousLux
                ?.takeIf {
                    it.isFinite() &&
                        it >= 0.0
                }
                ?: return sampleLux

        return previous +
            (
                sampleLux -
                    previous
            ) *
            SMOOTHING_ALPHA
    }

    fun resolveNightMode(
        previousNightMode:
            Boolean,
        smoothedLux:
            Double?,
        systemNightFallback:
            Boolean,
    ): Boolean {
        val lux =
            smoothedLux
                ?.takeIf {
                    it.isFinite() &&
                        it >= 0.0
                }
                ?: return systemNightFallback

        return if (previousNightMode) {
            lux <
                NIGHT_EXIT_LUX
        } else {
            lux <=
                NIGHT_ENTER_LUX
        }
    }

    fun brightnessLevel(
        smoothedLux:
            Double,
        correction:
            Int,
    ): Int {
        require(
            smoothedLux.isFinite() &&
                smoothedLux >= 0.0
        ) {
            "smoothedLux must be finite and non-negative."
        }

        val cappedLux =
            smoothedLux.coerceAtMost(
                MAX_MAPPED_LUX
            )

        val normalized =
            ln(
                cappedLux +
                    1.0
            ) /
                ln(
                    MAX_MAPPED_LUX +
                        1.0
                )

        val automaticLevel =
            (
                1.0 +
                    normalized *
                    (
                        NAVIGATION_BRIGHTNESS_LEVEL_COUNT -
                            1
                    )
            )
                .roundToInt()
                .coerceIn(
                    1,
                    NAVIGATION_BRIGHTNESS_LEVEL_COUNT,
                )

        return (
            automaticLevel +
                normalizeCorrection(
                    correction
                )
        )
            .coerceIn(
                1,
                NAVIGATION_BRIGHTNESS_LEVEL_COUNT,
            )
    }

    fun windowBrightness(
        brightnessLevel:
            Int,
    ): Float {
        require(
            brightnessLevel in
                1..NAVIGATION_BRIGHTNESS_LEVEL_COUNT
        ) {
            "brightnessLevel must be in the internal 20-level range."
        }

        val fraction =
            (
                brightnessLevel -
                    1
            ).toFloat() /
                (
                    NAVIGATION_BRIGHTNESS_LEVEL_COUNT -
                        1
                ).toFloat()

        return MIN_WINDOW_BRIGHTNESS +
            fraction *
            (
                1.0f -
                    MIN_WINDOW_BRIGHTNESS
            )
    }

    fun awaitingSensor(
        systemNightMode:
            Boolean,
        correction:
            Int,
    ): NavigationNightPresentation =
        NavigationNightPresentation(
            active = true,
            sensorAvailable = true,
            source =
                NavigationNightLightSource
                    .AwaitingSensor,
            smoothedLux = null,
            brightnessLevel = null,
            windowBrightness = null,
            nightMode = systemNightMode,
            brightnessCorrection =
                normalizeCorrection(
                    correction
                ),
        )

    fun systemFallback(
        active:
            Boolean,
        systemNightMode:
            Boolean,
        correction:
            Int,
    ): NavigationNightPresentation =
        NavigationNightPresentation(
            active = active,
            sensorAvailable = false,
            source =
                NavigationNightLightSource
                    .SystemFallback,
            smoothedLux = null,
            brightnessLevel = null,
            windowBrightness = null,
            nightMode = systemNightMode,
            brightnessCorrection =
                normalizeCorrection(
                    correction
                ),
        )

    fun fromSensor(
        smoothedLux:
            Double,
        previousNightMode:
            Boolean,
        systemNightFallback:
            Boolean,
        correction:
            Int,
    ): NavigationNightPresentation {
        val normalizedCorrection =
            normalizeCorrection(
                correction
            )

        val level =
            brightnessLevel(
                smoothedLux = smoothedLux,
                correction = normalizedCorrection,
            )

        return NavigationNightPresentation(
            active = true,
            sensorAvailable = true,
            source =
                NavigationNightLightSource.Sensor,
            smoothedLux = smoothedLux,
            brightnessLevel = level,
            windowBrightness =
                windowBrightness(
                    level
                ),
            nightMode =
                resolveNightMode(
                    previousNightMode =
                        previousNightMode,
                    smoothedLux =
                        smoothedLux,
                    systemNightFallback =
                        systemNightFallback,
                ),
            brightnessCorrection =
                normalizedCorrection,
        )
    }
}