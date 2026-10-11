package org.routingplatform.app.navigation

/*
 * Immutable evidence only.
 *
 * The drive-proof observer can copy navigation truth, but it has no
 * bridge, matcher, routing source, safety gate, location source or
 * mutation callback. It cannot create or advance route progress.
 */
data class NavigationDriveProofObservation(
    val schemaVersion:
        Int =
        SCHEMA_VERSION,

    val sequence:
        Long,

    val capturedAtElapsedRealtimeNanos:
        Long,

    val sessionId:
        String,

    val routeId:
        String,

    val pipelineStatus:
        NavigationRuntimePipelineStatus,

    val automaticProgressActive:
        Boolean,

    val confidence:
        NavigationPositionConfidence,

    val safetyStatus:
        NavigationRouteProgressSafetyStatus?,

    val observedPosition:
        RoutePoint?,

    val locationElapsedRealtimeNanos:
        Long?,

    val safetyApprovedCandidate:
        RouteProgressAnchor?,

    val acceptedProgress:
        RouteProgressAnchor?,

    val nativeUpdateAttempted:
        Boolean,

    val nativeUpdateAccepted:
        Boolean,

    val nativeFailureClass:
        String?,

    val nativeFailureMessage:
        String?,
) {
    init {
        require(
            schemaVersion ==
                SCHEMA_VERSION
        )

        require(
            sequence >
                0L
        )

        require(
            capturedAtElapsedRealtimeNanos >=
                0L
        )

        require(
            sessionId.isNotBlank()
        )

        require(
            routeId.isNotBlank()
        )

        require(
            locationElapsedRealtimeNanos ==
                null ||
                locationElapsedRealtimeNanos >=
                    0L
        )

        require(
            !nativeUpdateAccepted ||
                nativeUpdateAttempted
        )

        require(
            nativeUpdateAttempted ||
                (
                    nativeFailureClass ==
                        null &&
                    nativeFailureMessage ==
                        null
                )
        )

        require(
            !nativeUpdateAccepted ||
                (
                    nativeFailureClass ==
                        null &&
                    nativeFailureMessage ==
                        null
                )
        )

        if (
            pipelineStatus ==
                NavigationRuntimePipelineStatus.NativeProgressUpdated
        ) {
            require(
                nativeUpdateAttempted &&
                    nativeUpdateAccepted
            )
        }

        if (
            pipelineStatus ==
                NavigationRuntimePipelineStatus.NativeUpdateFailed
        ) {
            require(
                nativeUpdateAttempted &&
                    !nativeUpdateAccepted &&
                    !nativeFailureClass.isNullOrBlank()
            )
        }
    }

    companion object {
        const val SCHEMA_VERSION =
            1
    }
}

internal class NavigationDriveProofObservationSequencer {

    private var lastSequence =
        0L

    @Synchronized
    fun next(
        sessionId:
            String,

        routeId:
            String,

        capturedAtElapsedRealtimeNanos:
            Long,

        telemetry:
            NavigationRuntimeTelemetry,
    ): NavigationDriveProofObservation {

        check(
            lastSequence <
                Long.MAX_VALUE
        )

        lastSequence +=
            1L

        return NavigationDriveProofObservation(
            sequence =
                lastSequence,

            capturedAtElapsedRealtimeNanos =
                capturedAtElapsedRealtimeNanos,

            sessionId =
                sessionId,

            routeId =
                routeId,

            pipelineStatus =
                telemetry.pipelineStatus,

            automaticProgressActive =
                telemetry.automaticProgressActive,

            confidence =
                telemetry.confidence,

            safetyStatus =
                telemetry.safetyStatus,

            observedPosition =
                telemetry.lastObservedPosition,

            locationElapsedRealtimeNanos =
                telemetry.lastLocationElapsedRealtimeNanos,

            safetyApprovedCandidate =
                telemetry.safetyApprovedCandidate,

            acceptedProgress =
                telemetry.acceptedProgress,

            nativeUpdateAttempted =
                telemetry.nativeUpdateAttempted,

            nativeUpdateAccepted =
                telemetry.nativeUpdateAccepted,

            nativeFailureClass =
                telemetry.nativeFailureClass,

            nativeFailureMessage =
                telemetry.nativeFailureMessage,
        )
    }
}