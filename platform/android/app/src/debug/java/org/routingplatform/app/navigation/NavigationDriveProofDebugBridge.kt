package org.routingplatform.app.navigation

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.widget.TextView
import java.io.File
import org.routingplatform.app.R

internal object NavigationDriveProofObservationSinkFactory {

    @Suppress("UNUSED_PARAMETER")
    fun create(
        context:
            Context,
    ): NavigationDriveProofObservationSink =
        DebugNavigationDriveProofObservationSink
}

private object DebugNavigationDriveProofObservationSink :
    NavigationDriveProofObservationSink {

    override fun record(
        snapshot:
            NavigationUiSnapshot,

        telemetry:
            NavigationRuntimeTelemetry,

        capturedAtElapsedRealtimeNanos:
            Long,
    ) {
        NavigationDriveProofDebugCaptureController
            .record(
                snapshot =
                    snapshot,

                telemetry =
                    telemetry,

                capturedAtElapsedRealtimeNanos =
                    capturedAtElapsedRealtimeNanos,
            )
    }

    override fun close() {
        NavigationDriveProofDebugCaptureController
            .stopBestEffort()
    }
}

internal data class NavigationDriveProofDebugCaptureState(
    val armedCaptureId:
        String?,

    val activeCaptureId:
        String?,

    val activeSessionId:
        String?,

    val activeRouteId:
        String?,

    val failure:
        String?,
)

internal object NavigationDriveProofDebugCaptureController {

    private var recorder:
        NavigationDriveProofRecorder? =
        null

    private var armedCaptureId:
        String? =
        null

    private var activeCaptureId:
        String? =
        null

    private var activeSessionId:
        String? =
        null

    private var activeRouteId:
        String? =
        null

    private var failure:
        String? =
        null

    @Synchronized
    fun arm(
        context:
            Context,

        captureId:
            String,
    ) {
        require(
            captureId.matches(
                Regex(
                    "[A-Za-z0-9._-]{1,80}"
                )
            )
        )

        check(
            recorder ==
                null &&
                armedCaptureId ==
                    null
        ) {
            "A drive-proof capture is already armed or active."
        }

        recorder =
            NavigationDriveProofRecorder(
                directory =
                    File(
                        context.applicationContext.filesDir,
                        "navigation-drive-proof",
                    )
            )

        armedCaptureId =
            captureId

        activeCaptureId =
            null

        activeSessionId =
            null

        activeRouteId =
            null

        failure =
            null
    }

    /*
     * Real runtime telemetry enters here from MainActivity.
     *
     * No synthetic location/progress/session state is accepted by this
     * controller. It only copies the snapshot and telemetry already
     * published by the normal runtime path.
     */
    @Synchronized
    fun record(
        snapshot:
            NavigationUiSnapshot,

        telemetry:
            NavigationRuntimeTelemetry,

        capturedAtElapsedRealtimeNanos:
            Long,
    ) {
        if (
            failure !=
                null
        ) {
            return
        }

        val activeRecorder =
            recorder
                ?: return

        val armed =
            armedCaptureId

        if (
            activeCaptureId ==
                null
        ) {
            if (
                armed ==
                    null
            ) {
                return
            }

            try {
                activeRecorder.start(
                    captureId =
                        armed,

                    sessionId =
                        snapshot.sessionId,

                    routeId =
                        snapshot.routeId,
                )
            } catch (
                error:
                    Exception
            ) {
                failure =
                    "start:" +
                        (
                            error.message
                                ?: error.javaClass.name
                        )

                return
            }

            activeCaptureId =
                armed

            activeSessionId =
                snapshot.sessionId

            activeRouteId =
                snapshot.routeId

            armedCaptureId =
                null
        }

        if (
            snapshot.sessionId !=
                activeSessionId ||
            snapshot.routeId !=
                activeRouteId
        ) {
            failure =
                "identity-changed"

            stopRecorderBestEffort(
                activeRecorder
            )

            return
        }

        when (
            activeRecorder.record(
                capturedAtElapsedRealtimeNanos =
                    capturedAtElapsedRealtimeNanos,

                telemetry =
                    telemetry,
            )
        ) {
            NavigationDriveProofRecordResult.Recorded -> {
                // Continue capture.
            }

            NavigationDriveProofRecordResult.LimitReached -> {
                failure =
                    "limit-reached"
            }

            NavigationDriveProofRecordResult.Failed -> {
                failure =
                    "recorder-failed"
            }

            NavigationDriveProofRecordResult.Inactive -> {
                failure =
                    "recorder-inactive"
            }
        }
    }

    @Synchronized
    fun stop():
        NavigationDriveProofCaptureSummary {

        val activeRecorder =
            checkNotNull(
                recorder
            ) {
                "No drive-proof capture is armed or active."
            }

        val captureFailure =
            failure

        val summary =
            activeRecorder.stop()

        clearState()

        checkNotNull(
            summary
        ) {
            "Drive-proof capture never received runtime telemetry."
        }

        check(
            captureFailure ==
                null
        ) {
            "Drive-proof capture failed: $captureFailure"
        }

        check(
            !summary.limitReached &&
                !summary.writeFailed
        ) {
            "Drive-proof capture ended with recorder failure."
        }

        return summary
    }

    @Synchronized
    fun status():
        NavigationDriveProofDebugCaptureState =
        NavigationDriveProofDebugCaptureState(
            armedCaptureId =
                armedCaptureId,

            activeCaptureId =
                activeCaptureId,

            activeSessionId =
                activeSessionId,

            activeRouteId =
                activeRouteId,

            failure =
                failure,
        )

    @Synchronized
    fun stopBestEffort() {
        val activeRecorder =
            recorder
                ?: return

        stopRecorderBestEffort(
            activeRecorder
        )

        clearState()
    }

    private fun stopRecorderBestEffort(
        activeRecorder:
            NavigationDriveProofRecorder,
    ) {
        runCatching {
            activeRecorder.stop()
        }
    }

    private fun clearState() {
        recorder =
            null

        armedCaptureId =
            null

        activeCaptureId =
            null

        activeSessionId =
            null

        activeRouteId =
            null

        failure =
            null
    }
}

/*
 * Debug-only ADB control surface.
 *
 * It can arm/stop/status the recorder. It cannot supply coordinates,
 * route progress, navigation state or safety decisions.
 */
class G5R6DriveProofControlActivity :
    Activity() {

    override fun onCreate(
        savedInstanceState:
            Bundle?,
    ) {
        super.onCreate(
            savedInstanceState
        )

        val status =
            TextView(
                this
            ).apply {
                id =
                    R.id.g5r6_drive_proof_status

                textSize =
                    16.0f

                setPadding(
                    32,
                    32,
                    32,
                    32,
                )
            }

        setContentView(
            status
        )

        val mode =
            intent
                .getStringExtra(
                    EXTRA_MODE
                )
                ?.trim()
                .orEmpty()

        val result =
            runCatching {
                when (
                    mode
                ) {
                    "arm" -> {
                        val captureId =
                            intent
                                .getStringExtra(
                                    EXTRA_CAPTURE_ID
                                )
                                ?.trim()
                                .orEmpty()

                        NavigationDriveProofDebugCaptureController
                            .arm(
                                context =
                                    applicationContext,

                                captureId =
                                    captureId,
                            )

                        "capture=$captureId"
                    }

                    "stop" -> {
                        val summary =
                            NavigationDriveProofDebugCaptureController
                                .stop()

                        "capture=" +
                            summary.file.nameWithoutExtension +
                            ";events=" +
                            summary.eventCount +
                            ";bytes=" +
                            summary.bytesWritten
                    }

                    "status" -> {
                        val state =
                            NavigationDriveProofDebugCaptureController
                                .status()

                        "armed=" +
                            (
                                state.armedCaptureId
                                    ?: "none"
                            ) +
                            ";active=" +
                            (
                                state.activeCaptureId
                                    ?: "none"
                            ) +
                            ";session=" +
                            (
                                state.activeSessionId
                                    ?: "none"
                            ) +
                            ";route=" +
                            (
                                state.activeRouteId
                                    ?: "none"
                            ) +
                            ";failure=" +
                            (
                                state.failure
                                    ?: "none"
                            )
                    }

                    else ->
                        error(
                            "Unsupported drive-proof control mode."
                        )
                }
            }

        status.text =
            result.fold(
                onSuccess = {
                        detail ->

                    "PASS|" +
                        mode +
                        "|" +
                        detail
                },

                onFailure = {
                        error ->

                    "FAIL|" +
                        mode +
                        "|" +
                        error.javaClass.simpleName +
                        "|" +
                        (
                            error.message
                                ?: error.javaClass.name
                        )
                            .replace(
                                '|',
                                '/'
                            )
                            .take(
                                240
                            )
                },
            )
    }

    companion object {
        private const val EXTRA_MODE =
            "mode"

        private const val EXTRA_CAPTURE_ID =
            "captureId"
    }
}
