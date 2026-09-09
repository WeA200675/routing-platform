package org.routingplatform.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.routingplatform.app.profile.ProfileMapStyle

class NavigationNightAdaptationTest {
    @Test
    fun internalBrightnessScaleHasExactlyTwentyDistinctLevels() {
        val values =
            (1..NAVIGATION_BRIGHTNESS_LEVEL_COUNT)
                .map {
                    NavigationNightAdaptationPolicy
                        .windowBrightness(
                            it
                        )
                }

        assertEquals(
            20,
            values.size,
        )

        assertEquals(
            20,
            values.distinct().size,
        )

        assertTrue(
            values.zipWithNext()
                .all {
                        pair ->

                    pair.first <
                        pair.second
                }
        )
    }

    @Test
    fun lowLuxEntersNightMode() {
        val presentation =
            NavigationNightAdaptationPolicy
                .fromSensor(
                    smoothedLux = 3.0,
                    previousNightMode = false,
                    systemNightFallback = false,
                    correction = 0,
                )

        assertTrue(
            presentation.nightMode
        )

        assertEquals(
            ProfileMapStyle.Night,
            presentation
                .effectiveMapStyle(
                    ProfileMapStyle.Standard
                ),
        )
    }

    @Test
    fun hysteresisKeepsNightModeThroughSingleStreetlightSpike() {
        val filtered =
            NavigationNightAdaptationPolicy
                .smoothLux(
                    previousLux =
                        2.0,
                    sampleLux =
                        100.0,
                )

        assertTrue(
            filtered <
                24.0
        )

        assertTrue(
            NavigationNightAdaptationPolicy
                .resolveNightMode(
                    previousNightMode =
                        true,
                    smoothedLux =
                        filtered,
                    systemNightFallback =
                        false,
                )
        )
    }

    @Test
    fun nightModeExitsOnlyAboveUpperHysteresisThreshold() {
        assertTrue(
            NavigationNightAdaptationPolicy
                .resolveNightMode(
                    previousNightMode =
                        true,
                    smoothedLux =
                        20.0,
                    systemNightFallback =
                        false,
                )
        )

        assertFalse(
            NavigationNightAdaptationPolicy
                .resolveNightMode(
                    previousNightMode =
                        true,
                    smoothedLux =
                        30.0,
                    systemNightFallback =
                        false,
                )
        )
    }

    @Test
    fun userCorrectionIsSimpleAndClamped() {
        val base =
            NavigationNightAdaptationPolicy
                .brightnessLevel(
                    smoothedLux =
                        100.0,
                    correction =
                        0,
                )

        val darker =
            NavigationNightAdaptationPolicy
                .brightnessLevel(
                    smoothedLux =
                        100.0,
                    correction =
                        -1,
                )

        val brighter =
            NavigationNightAdaptationPolicy
                .brightnessLevel(
                    smoothedLux =
                        100.0,
                    correction =
                        1,
                )

        assertEquals(
            base - 1,
            darker,
        )

        assertEquals(
            base + 1,
            brighter,
        )

        assertEquals(
            NAVIGATION_BRIGHTNESS_CORRECTION_MIN,
            NavigationNightAdaptationPolicy
                .normalizeCorrection(
                    -100
                ),
        )

        assertEquals(
            NAVIGATION_BRIGHTNESS_CORRECTION_MAX,
            NavigationNightAdaptationPolicy
                .normalizeCorrection(
                    100
                ),
        )
    }

    @Test
    fun missingSensorFallsBackToSystemWithoutForcingBrightness() {
        val presentation =
            NavigationNightAdaptationPolicy
                .systemFallback(
                    active =
                        true,
                    systemNightMode =
                        true,
                    correction =
                        -2,
                )

        assertTrue(
            presentation.active
        )

        assertFalse(
            presentation.sensorAvailable
        )

        assertTrue(
            presentation.nightMode
        )

        assertNull(
            presentation.windowBrightness
        )

        assertNull(
            presentation.brightnessLevel
        )
    }

    @Test
    fun awaitingFirstFreshLuxDoesNotForceWindowBrightness() {
        val presentation =
            NavigationNightAdaptationPolicy
                .awaitingSensor(
                    systemNightMode =
                        false,
                    correction =
                        0,
                )

        assertTrue(
            presentation.sensorAvailable
        )

        assertEquals(
            NavigationNightLightSource.AwaitingSensor,
            presentation.source,
        )

        assertNull(
            presentation.windowBrightness
        )

        assertFalse(
            presentation.nightMode
        )
    }

    @Test
    fun dayModePreservesConfiguredMapStyle() {
        val presentation =
            NavigationNightAdaptationPolicy
                .fromSensor(
                    smoothedLux =
                        500.0,
                    previousNightMode =
                        false,
                    systemNightFallback =
                        false,
                    correction =
                        0,
                )

        assertFalse(
            presentation.nightMode
        )

        assertEquals(
            ProfileMapStyle.HighContrast,
            presentation
                .effectiveMapStyle(
                    ProfileMapStyle.HighContrast
                ),
        )
    }

    @Test
    fun inactivePresentationNeverOverridesMapStyle() {
        val presentation =
            NavigationNightPresentation
                .inactive(
                    systemNightMode =
                        true
                )

        assertEquals(
            ProfileMapStyle.Minimal,
            presentation
                .effectiveMapStyle(
                    ProfileMapStyle.Minimal
                ),
        )
    }

    @Test
    fun statusTextDoesNotContainMojibakeMarker() {
        val text =
            NavigationNightAdaptationPolicy
                .fromSensor(
                    smoothedLux =
                        3.0,
                    previousNightMode =
                        false,
                    systemNightFallback =
                        false,
                    correction =
                        0,
                )
                .statusText()

        assertFalse(
            text.contains(
                '\u00C2'
            )
        )

        assertTrue(
            text.contains(
                " | "
            )
        )
    }
}