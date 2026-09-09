package org.routingplatform.app.ui

import kotlin.math.roundToLong
import org.routingplatform.app.profile.NavigationHapticIntensity

internal data class NavigationHapticEffectPlan(
    val timings:
        LongArray,

    val amplitudes:
        IntArray,
) {
    init {
        require(
            timings.isNotEmpty() &&
                timings.size ==
                amplitudes.size
        )

        require(
            timings.all {
                it >=
                    0L
            }
        )

        require(
            amplitudes.all {
                it ==
                    0 ||
                    it ==
                    DEFAULT_AMPLITUDE ||
                    it in
                    1..255
            }
        )
    }

    companion object {
        const val DEFAULT_AMPLITUDE =
            -1
    }
}

internal object NavigationHapticEffectPlanner {

    fun create(
        signal:
            NavigationHapticSignal,

        intensity:
            NavigationHapticIntensity,

        amplitudeControl:
            Boolean,
    ): NavigationHapticEffectPlan {
        val durationScale =
            when (
                intensity
            ) {
                NavigationHapticIntensity.Gentle ->
                    0.80

                NavigationHapticIntensity.Standard ->
                    1.00

                NavigationHapticIntensity.Strong ->
                    1.20
            }

        val activeAmplitude =
            if (
                amplitudeControl
            ) {
                when (
                    intensity
                ) {
                    NavigationHapticIntensity.Gentle ->
                        90

                    NavigationHapticIntensity.Standard ->
                        160

                    NavigationHapticIntensity.Strong ->
                        230
                }
            } else {
                NavigationHapticEffectPlan
                    .DEFAULT_AMPLITUDE
            }

        val baseTimings =
            when (
                signal
            ) {
                NavigationHapticSignal.Prepare ->
                    longArrayOf(
                        0L,
                        28L,
                    )

                NavigationHapticSignal.Now ->
                    longArrayOf(
                        0L,
                        36L,
                        48L,
                        36L,
                    )

                NavigationHapticSignal.Critical ->
                    longArrayOf(
                        0L,
                        46L,
                        42L,
                        46L,
                        42L,
                        72L,
                    )

                NavigationHapticSignal.Arrival ->
                    longArrayOf(
                        0L,
                        30L,
                        58L,
                        82L,
                    )
            }

        val timings =
            LongArray(
                baseTimings.size
            ) {
                    index ->

                if (
                    index ==
                        0
                ) {
                    0L
                } else {
                    (
                        baseTimings[index] *
                            durationScale
                    )
                        .roundToLong()
                        .coerceAtLeast(
                            1L
                        )
                }
            }

        val amplitudes =
            IntArray(
                baseTimings.size
            ) {
                    index ->

                if (
                    index %
                        2 ==
                        1
                ) {
                    activeAmplitude
                } else {
                    0
                }
            }

        return NavigationHapticEffectPlan(
            timings =
                timings,

            amplitudes =
                amplitudes,
        )
    }
}