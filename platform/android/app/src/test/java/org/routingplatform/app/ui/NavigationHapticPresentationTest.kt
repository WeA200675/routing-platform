package org.routingplatform.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.routingplatform.app.navigation.ManeuverType
import org.routingplatform.app.navigation.NavigationManeuver
import org.routingplatform.app.navigation.NavigationSessionState
import org.routingplatform.app.navigation.NavigationUiSnapshot
import org.routingplatform.app.navigation.RoutePoint
import org.routingplatform.app.profile.InstructionLeadTimePreference
import org.routingplatform.app.profile.NavigationHapticIntensity
import org.routingplatform.app.profile.NavigationPreferences

class NavigationHapticPresentationTest {

    @Test
    fun disabledPreviewArrivedAndPassiveManeuversFailClosed() {
        assertNull(
            NavigationHapticPresentation
                .cue(
                    snapshot =
                        snapshot(
                            distanceMeters =
                                20.0
                        ),

                    preferences =
                        preferences(
                            enabled =
                                false
                        ),
                )
        )

        assertNull(
            NavigationHapticPresentation
                .cue(
                    snapshot =
                        snapshot(
                            state =
                                NavigationSessionState
                                    .Preview,

                            distanceMeters =
                                20.0,
                        ),

                    preferences =
                        preferences(),
                )
        )

        assertNull(
            NavigationHapticPresentation
                .cue(
                    snapshot =
                        snapshot(
                            state =
                                NavigationSessionState
                                    .Arrived,

                            arrived =
                                true,

                            distanceMeters =
                                0.0,
                        ),

                    preferences =
                        preferences(),
                )
        )

        assertNull(
            NavigationHapticPresentation
                .cue(
                    snapshot =
                        snapshot(
                            distanceMeters =
                                20.0,

                            maneuverType =
                                ManeuverType
                                    .Continue,
                        ),

                    preferences =
                        preferences(),
                )
        )
    }

    @Test
    fun standardLeadTimeEmitsPrepareAndNow() {
        assertNull(
            cue(
                distanceMeters =
                    181.0
            )
        )

        assertEquals(
            NavigationHapticCueStage.Prepare,
            cue(
                distanceMeters =
                    180.0
            )
                ?.key
                ?.stage,
        )

        assertEquals(
            NavigationHapticCueStage.Now,
            cue(
                distanceMeters =
                    35.0
            )
                ?.key
                ?.stage,
        )
    }

    @Test
    fun instructionLeadTimeMovesOnlyPresentationThresholds() {
        assertNull(
            cue(
                distanceMeters =
                    130.0,

                leadTime =
                    InstructionLeadTimePreference
                        .Late,
            )
        )

        assertEquals(
            NavigationHapticCueStage.Prepare,
            cue(
                distanceMeters =
                    240.0,

                leadTime =
                    InstructionLeadTimePreference
                        .Early,
            )
                ?.key
                ?.stage,
        )
    }

    @Test
    fun criticalAndArrivalManeuversHaveDistinctNowSignals() {
        assertEquals(
            NavigationHapticSignal.Critical,
            cue(
                distanceMeters =
                    20.0,

                maneuverType =
                    ManeuverType
                        .UTurn,
            )
                ?.signal,
        )

        assertEquals(
            NavigationHapticSignal.Arrival,
            cue(
                distanceMeters =
                    20.0,

                maneuverType =
                    ManeuverType
                        .Arrive,
            )
                ?.signal,
        )
    }

    @Test
    fun intensityIsCarriedWithoutMutatingNavigationTruth() {
        val snapshot =
            snapshot(
                distanceMeters =
                    20.0
            )

        val cue =
            NavigationHapticPresentation
                .cue(
                    snapshot =
                        snapshot,

                    preferences =
                        preferences(
                            intensity =
                                NavigationHapticIntensity
                                    .Strong
                        ),
                )

        assertEquals(
            NavigationHapticIntensity.Strong,
            cue?.intensity,
        )

        assertEquals(
            true,
            snapshot.presentationBoundaryIntact,
        )

        assertEquals(
            false,
            snapshot.rerouteRequested,
        )

        assertEquals(
            false,
            snapshot.routeRecomputed,
        )

        assertEquals(
            false,
            snapshot.routingEngineInvoked,
        )

        assertEquals(
            false,
            snapshot.candidateSelectionInvoked,
        )

        assertEquals(
            false,
            snapshot.costEngineInvoked,
        )

        assertEquals(
            false,
            snapshot.productionRouteMutationAllowed,
        )
    }

    private fun cue(
        distanceMeters:
            Double,

        leadTime:
            InstructionLeadTimePreference =
            InstructionLeadTimePreference
                .Standard,

        maneuverType:
            ManeuverType =
            ManeuverType.TurnRight,

        repeatCritical:
            Boolean =
            true,
    ): NavigationHapticCue? =
        NavigationHapticPresentation
            .cue(
                snapshot =
                    snapshot(
                        distanceMeters =
                            distanceMeters,

                        maneuverType =
                            maneuverType,
                    ),

                preferences =
                    preferences(
                        leadTime =
                            leadTime,

                        repeatCritical =
                            repeatCritical,
                    ),
            )

    private fun preferences(
        enabled:
            Boolean =
            true,

        intensity:
            NavigationHapticIntensity =
            NavigationHapticIntensity
                .Standard,

        leadTime:
            InstructionLeadTimePreference =
            InstructionLeadTimePreference
                .Standard,

        repeatCritical:
            Boolean =
            true,
    ): NavigationPreferences =
        NavigationPreferences(
            instructionLeadTime =
                leadTime,

            repeatCriticalInstructions =
                repeatCritical,

            hapticGuidanceEnabled =
                enabled,

            hapticIntensity =
                intensity,
        )

    private fun snapshot(
        state:
            NavigationSessionState =
            NavigationSessionState
                .Navigating,

        arrived:
            Boolean =
            false,

        distanceMeters:
            Double,

        maneuverType:
            ManeuverType =
            ManeuverType.TurnRight,

        beginShapeIndex:
            Int? =
            0,

        endShapeIndex:
            Int? =
            1,

        instruction:
            String =
            "Rechts abbiegen",
    ): NavigationUiSnapshot =
        NavigationUiSnapshot(
            sessionId =
                "session-haptic",

            state =
                state,

            routeId =
                "route-haptic",

            routeDistanceM =
                1000.0,

            routeDurationS =
                120.0,

            geometry =
                listOf(
                    RoutePoint(
                        latitude =
                            52.0,

                        longitude =
                            13.0,
                    ),

                    RoutePoint(
                        latitude =
                            52.01,

                        longitude =
                            13.01,
                    ),
                ),

            progressFraction =
                0.5,

            remainingDistanceM =
                500.0,

            remainingDurationS =
                60.0,

            currentManeuver =
                NavigationManeuver(
                    type =
                        maneuverType,

                    instruction =
                        instruction,

                    distanceM =
                        distanceMeters,

                    durationS =
                        10.0,

                    beginShapeIndex =
                        beginShapeIndex,

                    endShapeIndex =
                        endShapeIndex,
                ),

            nextManeuver =
                null,

            distanceToCurrentManeuverEndM =
                distanceMeters,

            arrived =
                arrived,
        )

    @Test
    fun criticalRepeatUsesDistinctHapticStageAndCriticalSignal() {
        val repeated =
            cue(
                distanceMeters =
                    15.0,

                maneuverType =
                    ManeuverType.Exit,

                repeatCritical =
                    true,
            )

        val disabled =
            cue(
                distanceMeters =
                    15.0,

                maneuverType =
                    ManeuverType.Exit,

                repeatCritical =
                    false,
            )

        assertEquals(
            NavigationHapticCueStage.CriticalRepeat,
            repeated?.key?.stage,
        )

        assertEquals(
            NavigationHapticSignal.Critical,
            repeated?.signal,
        )

        assertEquals(
            NavigationHapticCueStage.Now,
            disabled?.key?.stage,
        )
    }
}