package org.routingplatform.app.navigation

/**
 * Converts a runtime recovery signal into the existing route lifecycle
 * controller callback. The controller remains the single owner of route
 * replacement and its stale-response guards.
 */
internal class NavigationRuntimeRecoveryForwarder(
    private val routeController: NavigationRouteLifecycleController,
    private val snapshotProvider: () -> NavigationUiSnapshot,
    private val onSnapshot: (NavigationUiSnapshot) -> Unit,
    private val onTelemetry: (NavigationRouteAcquisitionTelemetry) -> Unit,
) {
    fun forward(
        telemetry: NavigationRuntimeTelemetry,
    ) {
        routeController.observeTelemetry(
            telemetry = telemetry,
            snapshotProvider = snapshotProvider,
            onSnapshot = onSnapshot,
            onTelemetry = onTelemetry,
        )
    }
}
