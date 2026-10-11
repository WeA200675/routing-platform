package org.routingplatform.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationDriveProofObservationTest {

    @Test
    fun sequenceIsMonotonicAndIdentityIsExplicit() {
        val sequencer =
            NavigationDriveProofObservationSequencer()

        val first =
            sequencer.next(
                sessionId =
                    "session-1",

                routeId =
                    "route-1",

                capturedAtElapsedRealtimeNanos =
                    100L,

                telemetry =
                    telemetry(),
            )

        val second =
            sequencer.next(
                sessionId =
                    "session-1",

                routeId =
                    "route-1",

                capturedAtElapsedRealtimeNanos =
                    200L,

                telemetry =
                    telemetry(),
            )

        assertEquals(
            1L,
            first.sequence,
        )

        assertEquals(
            2L,
            second.sequence,
        )

        assertEquals(
            "session-1",
            second.sessionId,
        )

        assertEquals(
            "route-1",
            second.routeId,
        )
    }

    @Test
    fun observationDoesNotConflatePositionCandidateAndNativeProgress() {
        val observed =
            RoutePoint(
                latitude =
                    47.1410,

                longitude =
                    9.5209,
            )

        val candidate =
            RouteProgressAnchor(
                shapeSegmentIndex =
                    1,

                segmentFraction =
                    0.4,
            )

        val accepted =
            RouteProgressAnchor(
                shapeSegmentIndex =
                    0,

                segmentFraction =
                    0.8,
            )

        val observation =
            NavigationDriveProofObservationSequencer()
                .next(
                    sessionId =
                        "session-separation",

                    routeId =
                        "route-separation",

                    capturedAtElapsedRealtimeNanos =
                        2_000L,

                    telemetry =
                        telemetry(
                            observedPosition =
                                observed,

                            safetyApprovedCandidate =
                                candidate,

                            acceptedProgress =
                                accepted,
                        ),
                )

        assertSame(
            observed,
            observation.observedPosition,
        )

        assertSame(
            candidate,
            observation.safetyApprovedCandidate,
        )

        assertSame(
            accepted,
            observation.acceptedProgress,
        )

        assertTrue(
            !observation.nativeUpdateAttempted
        )
    }

    @Test
    fun nativeFailureRemainsFailureEvenWhenMessageFallsBackToClass() {
        val accepted =
            RouteProgressAnchor(
                shapeSegmentIndex =
                    2,

                segmentFraction =
                    0.1,
            )

        val failureClass =
            IllegalStateException::class.java.name

        val observation =
            NavigationDriveProofObservationSequencer()
                .next(
                    sessionId =
                        "session-failure",

                    routeId =
                        "route-failure",

                    capturedAtElapsedRealtimeNanos =
                        3_000L,

                    telemetry =
                        telemetry(
                            pipelineStatus =
                                NavigationRuntimePipelineStatus.NativeUpdateFailed,

                            acceptedProgress =
                                accepted,

                            nativeUpdateAttempted =
                                true,

                            nativeUpdateAccepted =
                                false,

                            nativeFailureClass =
                                failureClass,

                            nativeFailureMessage =
                                failureClass,
                        ),
                )

        assertSame(
            accepted,
            observation.acceptedProgress,
        )

        assertEquals(
            failureClass,
            observation.nativeFailureClass,
        )

        assertTrue(
            observation.nativeUpdateAttempted
        )

        assertTrue(
            !observation.nativeUpdateAccepted
        )
    }

    @Test
    fun nativeAcceptanceCannotExistWithoutAttempt() {
        val error =
            runCatching {
                NavigationDriveProofObservation(
                    sequence =
                        1L,

                    capturedAtElapsedRealtimeNanos =
                        1L,

                    sessionId =
                        "session-invalid",

                    routeId =
                        "route-invalid",

                    pipelineStatus =
                        NavigationRuntimePipelineStatus.Running,

                    automaticProgressActive =
                        true,

                    confidence =
                        NavigationPositionConfidence.High,

                    safetyStatus =
                        NavigationRouteProgressSafetyStatus.Accepted,

                    observedPosition =
                        null,

                    locationElapsedRealtimeNanos =
                        null,

                    safetyApprovedCandidate =
                        null,

                    acceptedProgress =
                        null,

                    nativeUpdateAttempted =
                        false,

                    nativeUpdateAccepted =
                        true,

                    nativeFailureClass =
                        null,

                    nativeFailureMessage =
                        null,
                )
            }
                .exceptionOrNull()

        assertTrue(
            error is
                IllegalArgumentException
        )
    }

    private fun telemetry(
        pipelineStatus:
            NavigationRuntimePipelineStatus =
            NavigationRuntimePipelineStatus.Running,

        observedPosition:
            RoutePoint? =
            null,

        safetyApprovedCandidate:
            RouteProgressAnchor? =
            null,

        acceptedProgress:
            RouteProgressAnchor? =
            null,

        nativeUpdateAttempted:
            Boolean =
            false,

        nativeUpdateAccepted:
            Boolean =
            false,

        nativeFailureClass:
            String? =
            null,

        nativeFailureMessage:
            String? =
            null,
    ): NavigationRuntimeTelemetry =
        NavigationRuntimeTelemetry(
            pipelineStatus =
                pipelineStatus,

            automaticProgressActive =
                true,

            confidence =
                NavigationPositionConfidence.Medium,

            fusionMode =
                null,

            safetyStatus =
                null,

            rawGnssQuality =
                null,

            radioObservationCount =
                0,

            unknownSatelliteCount =
                0,

            acceptedProgress =
                acceptedProgress,

            lastLocationAccuracyM =
                null,

            lastObservedPosition =
                observedPosition,

            lastLocationElapsedRealtimeNanos =
                1_000L,

            safetyApprovedCandidate =
                safetyApprovedCandidate,

            nativeUpdateAttempted =
                nativeUpdateAttempted,

            nativeUpdateAccepted =
                nativeUpdateAccepted,

            nativeFailureClass =
                nativeFailureClass,

            nativeFailureMessage =
                nativeFailureMessage,
        )
}