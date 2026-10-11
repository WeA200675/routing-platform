package org.routingplatform.app.navigation

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationDriveProofRecorderTest {

    @Test
    fun deterministicJsonlUsesStableSchemaAndOrdering() {
        val directory =
            Files.createTempDirectory(
                "g5r6-recorder"
            )
                .toFile()

        val recorder =
            NavigationDriveProofRecorder(
                directory =
                    directory,

                maxEvents =
                    10,

                maxBytes =
                    100_000L,
            )

        val file =
            recorder.start(
                captureId =
                    "capture-1",

                sessionId =
                    "session-1",

                routeId =
                    "route-1",
            )

        assertEquals(
            NavigationDriveProofRecordResult.Recorded,
            recorder.record(
                capturedAtElapsedRealtimeNanos =
                    100L,

                telemetry =
                    telemetry(),
            ),
        )

        val summary =
            checkNotNull(
                recorder.stop()
            )

        assertEquals(
            1,
            summary.eventCount,
        )

        assertFalse(
            summary.limitReached
        )

        assertFalse(
            summary.writeFailed
        )

        val line =
            file.readLines(
                Charsets.UTF_8
            )
                .single()

        assertEquals(
            """{"schemaVersion":1,"sequence":1,"capturedAtElapsedRealtimeNanos":100,"sessionId":"session-1","routeId":"route-1","pipelineStatus":"Running","automaticProgressActive":true,"confidence":"Medium","safetyStatus":null,"observedPosition":{"latitude":47.141,"longitude":9.5209},"locationElapsedRealtimeNanos":90,"safetyApprovedCandidate":null,"acceptedProgress":{"shapeSegmentIndex":0,"segmentFraction":0.25},"nativeUpdateAttempted":false,"nativeUpdateAccepted":false,"nativeFailureClass":null,"nativeFailureMessage":null}""",
            line,
        )
    }

    @Test
    fun eventLimitIsHardAndDoesNotOverwriteEarlierEvidence() {
        val recorder =
            NavigationDriveProofRecorder(
                directory =
                    Files.createTempDirectory(
                        "g5r6-limit"
                    )
                        .toFile(),

                maxEvents =
                    2,

                maxBytes =
                    100_000L,
            )

        val file =
            recorder.start(
                captureId =
                    "bounded",

                sessionId =
                    "session-bounded",

                routeId =
                    "route-bounded",
            )

        assertEquals(
            NavigationDriveProofRecordResult.Recorded,
            recorder.record(
                1L,
                telemetry(),
            ),
        )

        assertEquals(
            NavigationDriveProofRecordResult.Recorded,
            recorder.record(
                2L,
                telemetry(),
            ),
        )

        assertEquals(
            NavigationDriveProofRecordResult.LimitReached,
            recorder.record(
                3L,
                telemetry(),
            ),
        )

        val summary =
            checkNotNull(
                recorder.stop()
            )

        assertEquals(
            2,
            summary.eventCount,
        )

        assertTrue(
            summary.limitReached
        )

        assertEquals(
            2,
            file.readLines(
                Charsets.UTF_8
            ).size,
        )
    }

    @Test
    fun byteLimitRejectsWholeEventInsteadOfWritingPartialJson() {
        val recorder =
            NavigationDriveProofRecorder(
                directory =
                    Files.createTempDirectory(
                        "g5r6-bytes"
                    )
                        .toFile(),

                maxEvents =
                    10,

                maxBytes =
                    1L,
            )

        val file =
            recorder.start(
                captureId =
                    "bytes",

                sessionId =
                    "session-bytes",

                routeId =
                    "route-bytes",
            )

        assertEquals(
            NavigationDriveProofRecordResult.LimitReached,
            recorder.record(
                1L,
                telemetry(),
            ),
        )

        val summary =
            checkNotNull(
                recorder.stop()
            )

        assertEquals(
            0,
            summary.eventCount,
        )

        assertTrue(
            summary.limitReached
        )

        assertEquals(
            0L,
            file.length(),
        )
    }

    @Test
    fun malformedObservationFailsRecorderWithoutEscapingToRuntime() {
        val recorder =
            NavigationDriveProofRecorder(
                directory =
                    Files.createTempDirectory(
                        "g5r6-malformed"
                    )
                        .toFile(),
            )

        recorder.start(
            captureId =
                "malformed",

            sessionId =
                "session-malformed",

            routeId =
                "route-malformed",
        )

        val result =
            recorder.record(
                1L,
                telemetry(
                    observedPosition =
                        RoutePoint(
                            latitude =
                                Double.NaN,

                            longitude =
                                9.5209,
                        )
                ),
            )

        assertEquals(
            NavigationDriveProofRecordResult.Failed,
            result,
        )

        val summary =
            checkNotNull(
                recorder.stop()
            )

        assertTrue(
            summary.writeFailed
        )
    }

    @Test
    fun inactiveRecorderCannotCreateEvidenceAndCaptureIdCannotEscapeDirectory() {
        val recorder =
            NavigationDriveProofRecorder(
                directory =
                    Files.createTempDirectory(
                        "g5r6-inactive"
                    )
                        .toFile(),
            )

        assertEquals(
            NavigationDriveProofRecordResult.Inactive,
            recorder.record(
                1L,
                telemetry(),
            ),
        )

        val error =
            runCatching {
                recorder.start(
                    captureId =
                        "../escape",

                    sessionId =
                        "session",

                    routeId =
                        "route",
                )
            }
                .exceptionOrNull()

        assertTrue(
            error is
                IllegalArgumentException
        )
    }

    @Test
    fun newCaptureGetsFreshSequence() {
        val directory =
            Files.createTempDirectory(
                "g5r6-sequence"
            )
                .toFile()

        val recorder =
            NavigationDriveProofRecorder(
                directory =
                    directory,
            )

        val first =
            recorder.start(
                captureId =
                    "first",

                sessionId =
                    "session-1",

                routeId =
                    "route-1",
            )

        assertEquals(
            NavigationDriveProofRecordResult.Recorded,
            recorder.record(
                10L,
                telemetry(),
            ),
        )

        recorder.stop()

        val second =
            recorder.start(
                captureId =
                    "second",

                sessionId =
                    "session-2",

                routeId =
                    "route-2",
            )

        assertEquals(
            NavigationDriveProofRecordResult.Recorded,
            recorder.record(
                20L,
                telemetry(),
            ),
        )

        recorder.stop()

        assertTrue(
            first.readText(
                Charsets.UTF_8
            ).contains(
                "\"sequence\":1"
            )
        )

        assertTrue(
            second.readText(
                Charsets.UTF_8
            ).contains(
                "\"sequence\":1"
            )
        )
    }

    private fun telemetry(
        observedPosition:
            RoutePoint? =
            RoutePoint(
                latitude =
                    47.1410,

                longitude =
                    9.5209,
            ),
    ): NavigationRuntimeTelemetry =
        NavigationRuntimeTelemetry(
            pipelineStatus =
                NavigationRuntimePipelineStatus.Running,

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
                RouteProgressAnchor(
                    shapeSegmentIndex =
                        0,

                    segmentFraction =
                        0.25,
                ),

            lastLocationAccuracyM =
                5.0,

            lastObservedPosition =
                observedPosition,

            lastLocationElapsedRealtimeNanos =
                90L,
        )
}
