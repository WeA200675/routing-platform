package org.routingplatform.app.navigation

internal interface NavigationDriveProofObservationSink {

    /*
     * Observation-only callback.
     *
     * Implementations may persist already-published telemetry. They must
     * not own or call routing, matching, safety, location or native-progress
     * mutation APIs.
     */
    fun record(
        snapshot:
            NavigationUiSnapshot,

        telemetry:
            NavigationRuntimeTelemetry,

        capturedAtElapsedRealtimeNanos:
            Long,
    )

    fun close()
}

internal object NoOpNavigationDriveProofObservationSink :
    NavigationDriveProofObservationSink {

    override fun record(
        snapshot:
            NavigationUiSnapshot,

        telemetry:
            NavigationRuntimeTelemetry,

        capturedAtElapsedRealtimeNanos:
            Long,
    ) {
        // Production/release sink intentionally records nothing.
    }

    override fun close() {
        // No-op.
    }
}
