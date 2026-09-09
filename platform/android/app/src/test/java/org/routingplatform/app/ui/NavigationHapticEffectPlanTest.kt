package org.routingplatform.app.ui

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.routingplatform.app.profile.NavigationHapticIntensity

class NavigationHapticEffectPlanTest {

    @Test
    fun fallbackAmplitudeDoesNotRequireAmplitudeControl() {
        val plan =
            NavigationHapticEffectPlanner
                .create(
                    signal =
                        NavigationHapticSignal
                            .Now,

                    intensity =
                        NavigationHapticIntensity
                            .Standard,

                    amplitudeControl =
                        false,
                )

        assertArrayEquals(
            intArrayOf(
                0,
                -1,
                0,
                -1,
            ),
            plan.amplitudes,
        )
    }

    @Test
    fun strongAmplitudeIsHigherThanGentleWhenSupported() {
        val gentle =
            NavigationHapticEffectPlanner
                .create(
                    signal =
                        NavigationHapticSignal
                            .Prepare,

                    intensity =
                        NavigationHapticIntensity
                            .Gentle,

                    amplitudeControl =
                        true,
                )

        val strong =
            NavigationHapticEffectPlanner
                .create(
                    signal =
                        NavigationHapticSignal
                            .Prepare,

                    intensity =
                        NavigationHapticIntensity
                            .Strong,

                    amplitudeControl =
                        true,
                )

        assertEquals(
            true,
            strong.amplitudes[1] >
                gentle.amplitudes[1],
        )

        assertEquals(
            true,
            strong.timings[1] >
                gentle.timings[1],
        )
    }

    @Test
    fun criticalSignalUsesThreeActivePulses() {
        val plan =
            NavigationHapticEffectPlanner
                .create(
                    signal =
                        NavigationHapticSignal
                            .Critical,

                    intensity =
                        NavigationHapticIntensity
                            .Standard,

                    amplitudeControl =
                        true,
                )

        assertEquals(
            3,
            plan.amplitudes
                .count {
                    it >
                        0
                },
        )
    }
}