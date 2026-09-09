package org.routingplatform.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import org.routingplatform.app.navigation.ManeuverType
import org.routingplatform.app.navigation.NavigationManeuver
import org.routingplatform.app.navigation.NavigationSessionState
import org.routingplatform.app.navigation.NavigationUiSnapshot
import org.routingplatform.app.navigation.RoutePoint
import org.routingplatform.app.profile.NavigationPreferences

class NavigationHapticPlaybackRuntimeTest {

    @Test
    fun identicalCueIsDeliveredOnlyOnce() {
        val sink =
            RecordingSink()

        val runtime =
            NavigationHapticPlaybackRuntime(
                sink
            )

        val snapshot =
            snapshot(
                distanceMeters =
                    100.0
            )

        runtime.present(
            snapshot,
            NavigationPreferences(),
        )

        runtime.present(
            snapshot,
            NavigationPreferences(),
        )

        assertEquals(
            1,
            sink.cues.size,
        )
    }

    @Test
    fun prepareAndNowStagesAreBothDelivered() {
        val sink =
            RecordingSink()

        val runtime =
            NavigationHapticPlaybackRuntime(
                sink
            )

        runtime.present(
            snapshot(
                distanceMeters =
                    100.0
            ),
            NavigationPreferences(),
        )

        runtime.present(
            snapshot(
                distanceMeters =
                    20.0
            ),
            NavigationPreferences(),
        )

        assertEquals(
            listOf(
                NavigationHapticCueStage.Prepare,
                NavigationHapticCueStage.Now,
            ),
            sink.cues
                .map {
                    it.key.stage
                },
        )
    }

    @Test
    fun rejectedCueIsRetried() {
        val sink =
            RecordingSink(
                rejectFirst =
                    true
            )

        val runtime =
            NavigationHapticPlaybackRuntime(
                sink
            )

        val snapshot =
            snapshot(
                distanceMeters =
                    20.0
            )

        runtime.present(
            snapshot,
            NavigationPreferences(),
        )

        runtime.present(
            snapshot,
            NavigationPreferences(),
        )

        assertEquals(
            2,
            sink.submitAttempts,
        )

        assertEquals(
            1,
            sink.cues.size,
        )
    }

    @Test
    fun routeChangeResetsDeliveryIdentityAndCancelsOutput() {
        val sink =
            RecordingSink()

        val runtime =
            NavigationHapticPlaybackRuntime(
                sink
            )

        runtime.present(
            snapshot(
                routeId =
                    "route-a",

                distanceMeters =
                    20.0
            ),
            NavigationPreferences(),
        )

        runtime.present(
            snapshot(
                routeId =
                    "route-b",

                distanceMeters =
                    20.0
            ),
            NavigationPreferences(),
        )

        assertEquals(
            2,
            sink.cues.size,
        )

        assertEquals(
            1,
            sink.cancelCount,
        )
    }

    @Test
    fun fallbackManeuverDistanceResetCreatesNewDeliveryEpoch() {
        val sink =
            RecordingSink()

        val runtime =
            NavigationHapticPlaybackRuntime(
                sink
            )

        runtime.present(
            snapshot(
                distanceMeters =
                    20.0,

                beginShapeIndex =
                    null,

                endShapeIndex =
                    null,
            ),
            NavigationPreferences(),
        )

        runtime.present(
            snapshot(
                distanceMeters =
                    100.0,

                beginShapeIndex =
                    null,

                endShapeIndex =
                    null,
            ),
            NavigationPreferences(),
        )

        runtime.present(
            snapshot(
                distanceMeters =
                    20.0,

                beginShapeIndex =
                    null,

                endShapeIndex =
                    null,
            ),
            NavigationPreferences(),
        )

        assertEquals(
            3,
            sink.cues.size,
        )
    }

    @Test
    fun disablingHapticsCancelsActiveSession() {
        val sink =
            RecordingSink()

        val runtime =
            NavigationHapticPlaybackRuntime(
                sink
            )

        runtime.present(
            snapshot(
                distanceMeters =
                    20.0
            ),
            NavigationPreferences(),
        )

        runtime.present(
            snapshot(
                distanceMeters =
                    20.0
            ),
            NavigationPreferences(
                hapticGuidanceEnabled =
                    false
            ),
        )

        assertEquals(
            1,
            sink.cancelCount,
        )
    }

    private class RecordingSink(
        private val rejectFirst:
            Boolean =
            false,
    ) :
        NavigationHapticSink {

        val cues =
            mutableListOf<
                NavigationHapticCue
            >()

        var submitAttempts =
            0

        var cancelCount =
            0

        override fun submit(
            cue:
                NavigationHapticCue,
        ): NavigationHapticSubmission {
            submitAttempts +=
                1

            if (
                rejectFirst &&
                submitAttempts ==
                    1
            ) {
                return NavigationHapticSubmission
                    .Rejected
            }

            cues.add(
                cue
            )

            return NavigationHapticSubmission
                .Performed
        }

        override fun cancel() {
            cancelCount +=
                1
        }

        override fun close() {
            Unit
        }
    }

    private fun snapshot(
        routeId:
            String =
            "route-haptic",

        state:
            NavigationSessionState =
            NavigationSessionState
                .Navigating,

        distanceMeters:
            Double,

        beginShapeIndex:
            Int? =
            0,

        endShapeIndex:
            Int? =
            1,
    ): NavigationUiSnapshot =
        NavigationUiSnapshot(
            sessionId =
                "session-haptic",

            state =
                state,

            routeId =
                routeId,

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
                        ManeuverType.TurnRight,

                    instruction =
                        "Rechts abbiegen",

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
                false,
        )
}