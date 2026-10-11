package org.routingplatform.app.navigation

import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream

internal enum class NavigationDriveProofRecordResult {
    Recorded,
    LimitReached,
    Inactive,
    Failed,
}

internal data class NavigationDriveProofCaptureSummary(
    val file:
        File,

    val eventCount:
        Int,

    val bytesWritten:
        Long,

    val limitReached:
        Boolean,

    val writeFailed:
        Boolean,
)

/*
 * Debug-source-set-only bounded recorder.
 *
 * record() is best-effort and never throws into the navigation runtime.
 * It owns no native bridge, route source, matcher, safety gate or
 * location source. Its only input is already-published immutable telemetry.
 */
internal class NavigationDriveProofRecorder(
    private val directory:
        File,

    private val maxEvents:
        Int =
        DEFAULT_MAX_EVENTS,

    private val maxBytes:
        Long =
        DEFAULT_MAX_BYTES,
) {
    init {
        require(
            maxEvents >
                0
        )

        require(
            maxBytes >
                0L
        )
    }

    private var writer:
        BufferedWriter? =
        null

    private var file:
        File? =
        null

    private var sessionId:
        String? =
        null

    private var routeId:
        String? =
        null

    private var sequencer:
        NavigationDriveProofObservationSequencer? =
        null

    private var eventCount =
        0

    private var bytesWritten =
        0L

    private var limitReached =
        false

    private var writeFailed =
        false

    @Synchronized
    fun start(
        captureId:
            String,

        sessionId:
            String,

        routeId:
            String,
    ): File {

        check(
            writer ==
                null
        ) {
            "A drive-proof capture is already active."
        }

        require(
            CAPTURE_ID_PATTERN.matches(
                captureId
            )
        ) {
            "Drive-proof capture id is invalid."
        }

        require(
            sessionId.isNotBlank()
        )

        require(
            routeId.isNotBlank()
        )

        check(
            directory.exists() ||
                directory.mkdirs()
        ) {
            "Drive-proof directory could not be created."
        }

        check(
            directory.isDirectory
        ) {
            "Drive-proof path is not a directory."
        }

        val captureFile =
            File(
                directory,
                "$captureId.jsonl",
            )

        check(
            captureFile.createNewFile()
        ) {
            "Drive-proof capture already exists."
        }

        val openedWriter =
            FileOutputStream(
                captureFile,
                false,
            )
                .bufferedWriter(
                    Charsets.UTF_8
                )

        file =
            captureFile

        this.sessionId =
            sessionId

        this.routeId =
            routeId

        sequencer =
            NavigationDriveProofObservationSequencer()

        eventCount =
            0

        bytesWritten =
            0L

        limitReached =
            false

        writeFailed =
            false

        writer =
            openedWriter

        return captureFile
    }

    /*
     * Best-effort by construction: recording failures are converted to a
     * result value and cannot propagate into navigation truth.
     */
    @Synchronized
    fun record(
        capturedAtElapsedRealtimeNanos:
            Long,

        telemetry:
            NavigationRuntimeTelemetry,
    ): NavigationDriveProofRecordResult {

        val activeWriter =
            writer
                ?: return NavigationDriveProofRecordResult.Inactive

        if (
            limitReached
        ) {
            return NavigationDriveProofRecordResult.LimitReached
        }

        if (
            writeFailed
        ) {
            return NavigationDriveProofRecordResult.Failed
        }

        return try {
            val activeSequencer =
                checkNotNull(
                    sequencer
                )

            val observation =
                activeSequencer.next(
                    sessionId =
                        checkNotNull(
                            sessionId
                        ),

                    routeId =
                        checkNotNull(
                            routeId
                        ),

                    capturedAtElapsedRealtimeNanos =
                        capturedAtElapsedRealtimeNanos,

                    telemetry =
                        telemetry,
                )

            val line =
                NavigationDriveProofJsonlCodec
                    .encode(
                        observation
                    )

            val encodedBytes =
                (
                    line +
                        "\n"
                )
                    .toByteArray(
                        Charsets.UTF_8
                    )
                    .size
                    .toLong()

            if (
                eventCount >=
                    maxEvents ||
                bytesWritten +
                    encodedBytes >
                    maxBytes
            ) {
                limitReached =
                    true

                NavigationDriveProofRecordResult.LimitReached
            } else {
                activeWriter.write(
                    line
                )

                activeWriter.newLine()

                /*
                 * Debug evidence favors crash-resilient visibility over
                 * throughput. The recorder remains bounded and debug-only.
                 */
                activeWriter.flush()

                eventCount +=
                    1

                bytesWritten +=
                    encodedBytes

                NavigationDriveProofRecordResult.Recorded
            }
        } catch (
            error:
                Exception
        ) {
            writeFailed =
                true

            runCatching {
                activeWriter.close()
            }

            writer =
                null

            NavigationDriveProofRecordResult.Failed
        }
    }

    @Synchronized
    fun stop():
        NavigationDriveProofCaptureSummary? {

        val captureFile =
            file
                ?: return null

        val activeWriter =
            writer

        if (
            activeWriter !=
                null
        ) {
            try {
                activeWriter.close()
            } catch (
                error:
                    Exception
            ) {
                writeFailed =
                    true
            }
        }

        val summary =
            NavigationDriveProofCaptureSummary(
                file =
                    captureFile,

                eventCount =
                    eventCount,

                bytesWritten =
                    bytesWritten,

                limitReached =
                    limitReached,

                writeFailed =
                    writeFailed,
            )

        writer =
            null

        file =
            null

        sessionId =
            null

        routeId =
            null

        sequencer =
            null

        eventCount =
            0

        bytesWritten =
            0L

        limitReached =
            false

        writeFailed =
            false

        return summary
    }
}

internal object NavigationDriveProofJsonlCodec {

    fun encode(
        observation:
            NavigationDriveProofObservation,
    ): String =
        buildString {
            append(
                '{'
            )

            appendName(
                "schemaVersion"
            )

            append(
                observation.schemaVersion
            )

            appendCommaName(
                "sequence"
            )

            append(
                observation.sequence
            )

            appendCommaName(
                "capturedAtElapsedRealtimeNanos"
            )

            append(
                observation.capturedAtElapsedRealtimeNanos
            )

            appendCommaName(
                "sessionId"
            )

            appendJsonString(
                observation.sessionId
            )

            appendCommaName(
                "routeId"
            )

            appendJsonString(
                observation.routeId
            )

            appendCommaName(
                "pipelineStatus"
            )

            appendJsonString(
                observation.pipelineStatus.name
            )

            appendCommaName(
                "automaticProgressActive"
            )

            append(
                observation.automaticProgressActive
            )

            appendCommaName(
                "confidence"
            )

            appendJsonString(
                observation.confidence.name
            )

            appendCommaName(
                "safetyStatus"
            )

            appendNullableString(
                observation.safetyStatus
                    ?.name
            )

            appendCommaName(
                "observedPosition"
            )

            appendPoint(
                observation.observedPosition
            )

            appendCommaName(
                "locationElapsedRealtimeNanos"
            )

            appendNullableLong(
                observation.locationElapsedRealtimeNanos
            )

            appendCommaName(
                "safetyApprovedCandidate"
            )

            appendAnchor(
                observation.safetyApprovedCandidate
            )

            appendCommaName(
                "acceptedProgress"
            )

            appendAnchor(
                observation.acceptedProgress
            )

            appendCommaName(
                "nativeUpdateAttempted"
            )

            append(
                observation.nativeUpdateAttempted
            )

            appendCommaName(
                "nativeUpdateAccepted"
            )

            append(
                observation.nativeUpdateAccepted
            )

            appendCommaName(
                "nativeFailureClass"
            )

            appendNullableString(
                observation.nativeFailureClass
            )

            appendCommaName(
                "nativeFailureMessage"
            )

            appendNullableString(
                observation.nativeFailureMessage
            )

            append(
                '}'
            )
        }

    private fun StringBuilder.appendName(
        name:
            String,
    ) {
        appendJsonString(
            name
        )

        append(
            ':'
        )
    }

    private fun StringBuilder.appendCommaName(
        name:
            String,
    ) {
        append(
            ','
        )

        appendName(
            name
        )
    }

    private fun StringBuilder.appendNullableString(
        value:
            String?,
    ) {
        if (
            value ==
                null
        ) {
            append(
                "null"
            )
        } else {
            appendJsonString(
                value
            )
        }
    }

    private fun StringBuilder.appendNullableLong(
        value:
            Long?,
    ) {
        if (
            value ==
                null
        ) {
            append(
                "null"
            )
        } else {
            append(
                value
            )
        }
    }

    private fun StringBuilder.appendPoint(
        point:
            RoutePoint?,
    ) {
        if (
            point ==
                null
        ) {
            append(
                "null"
            )

            return
        }

        require(
            point.latitude.isFinite() &&
                point.longitude.isFinite()
        )

        append(
            '{'
        )

        appendName(
            "latitude"
        )

        append(
            point.latitude
        )

        appendCommaName(
            "longitude"
        )

        append(
            point.longitude
        )

        append(
            '}'
        )
    }

    private fun StringBuilder.appendAnchor(
        anchor:
            RouteProgressAnchor?,
    ) {
        if (
            anchor ==
                null
        ) {
            append(
                "null"
            )

            return
        }

        require(
            anchor.segmentFraction.isFinite()
        )

        append(
            '{'
        )

        appendName(
            "shapeSegmentIndex"
        )

        append(
            anchor.shapeSegmentIndex
        )

        appendCommaName(
            "segmentFraction"
        )

        append(
            anchor.segmentFraction
        )

        append(
            '}'
        )
    }

    private fun StringBuilder.appendJsonString(
        value:
            String,
    ) {
        append(
            '"'
        )

        value.forEach {
                character ->

            when (
                character
            ) {
                '"' ->
                    append(
                        "\\\""
                    )

                '\\' ->
                    append(
                        "\\\\"
                    )

                '\b' ->
                    append(
                        "\\b"
                    )

                '\u000c' ->
                    append(
                        "\\f"
                    )

                '\n' ->
                    append(
                        "\\n"
                    )

                '\r' ->
                    append(
                        "\\r"
                    )

                '\t' ->
                    append(
                        "\\t"
                    )

                else ->
                    if (
                        character.code <
                            0x20
                    ) {
                        append(
                            "\\u"
                        )

                        append(
                            character
                                .code
                                .toString(
                                    16
                                )
                                .padStart(
                                    4,
                                    '0',
                                )
                        )
                    } else {
                        append(
                            character
                        )
                    }
            }
        }

        append(
            '"'
        )
    }
}

private const val DEFAULT_MAX_EVENTS =
    7_200

private const val DEFAULT_MAX_BYTES =
    8L *
        1024L *
        1024L

private val CAPTURE_ID_PATTERN =
    Regex(
        "[A-Za-z0-9._-]{1,80}"
    )
