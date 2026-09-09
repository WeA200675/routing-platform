package org.routingplatform.app.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.routingplatform.app.navigation.ManeuverType

class NavigationCriticalGuidanceRepeatPolicyTest {

    @Test
    fun criticalManeuverRepeatsInsideCloseThresholdWhenEnabled() {
        assertTrue(
            NavigationCriticalGuidanceRepeatPolicy
                .shouldRepeat(
                    enabled =
                        true,

                    maneuverType =
                        ManeuverType.Exit,

                    distanceMeters =
                        15.0,
                )
        )

        assertTrue(
            NavigationCriticalGuidanceRepeatPolicy
                .shouldRepeat(
                    enabled =
                        true,

                    maneuverType =
                        ManeuverType.UTurn,

                    distanceMeters =
                        5.0,
                )
        )
    }

    @Test
    fun disabledOrNonCriticalGuidanceDoesNotRepeat() {
        assertFalse(
            NavigationCriticalGuidanceRepeatPolicy
                .shouldRepeat(
                    enabled =
                        false,

                    maneuverType =
                        ManeuverType.Exit,

                    distanceMeters =
                        5.0,
                )
        )

        assertFalse(
            NavigationCriticalGuidanceRepeatPolicy
                .shouldRepeat(
                    enabled =
                        true,

                    maneuverType =
                        ManeuverType.TurnRight,

                    distanceMeters =
                        5.0,
                )
        )
    }

    @Test
    fun invalidOrTooEarlyDistanceFailsClosed() {
        listOf(
            Double.NaN,
            Double.POSITIVE_INFINITY,
            -1.0,
            15.01,
        ).forEach {
                distance ->

            assertFalse(
                NavigationCriticalGuidanceRepeatPolicy
                    .shouldRepeat(
                        enabled =
                            true,

                        maneuverType =
                            ManeuverType.RoundaboutExit,

                        distanceMeters =
                            distance,
                    )
            )
        }
    }

    @Test
    fun criticalClassificationMatchesExistingDistinctHapticCases() {
        assertTrue(
            NavigationCriticalGuidanceRepeatPolicy
                .isCritical(
                    ManeuverType.UTurn
                )
        )

        assertTrue(
            NavigationCriticalGuidanceRepeatPolicy
                .isCritical(
                    ManeuverType.Exit
                )
        )

        assertTrue(
            NavigationCriticalGuidanceRepeatPolicy
                .isCritical(
                    ManeuverType.RoundaboutEnter
                )
        )

        assertTrue(
            NavigationCriticalGuidanceRepeatPolicy
                .isCritical(
                    ManeuverType.RoundaboutExit
                )
        )

        assertFalse(
            NavigationCriticalGuidanceRepeatPolicy
                .isCritical(
                    ManeuverType.Arrive
                )
        )

        assertFalse(
            NavigationCriticalGuidanceRepeatPolicy
                .isCritical(
                    ManeuverType.TurnLeft
                )
        )
    }
}
