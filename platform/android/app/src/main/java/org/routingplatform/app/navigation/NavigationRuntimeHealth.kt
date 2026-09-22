package org.routingplatform.app.navigation

/**
 * P19 privacy-safe production health projection.
 *
 * This projection intentionally excludes positions, route geometry, accuracy
 * values, native exception messages and raw radio/sensor evidence.
 */
data class NavigationRuntimeHealth(
    val pipelineStatus: NavigationRuntimePipelineStatus,
    val automaticProgressActive: Boolean,
    val observationConfidence: NavigationPositionConfidence,
    val safetyStatus: NavigationRouteProgressSafetyStatus?,
    val nativeUpdateAttempted: Boolean,
    val nativeUpdateAccepted: Boolean,
) {
    init {
        require(!nativeUpdateAccepted || nativeUpdateAttempted)
        require(
            !automaticProgressActive ||
                pipelineStatus != NavigationRuntimePipelineStatus.Stopped
        )
    }
}

fun NavigationRuntimeTelemetry.toHealth(): NavigationRuntimeHealth =
    NavigationRuntimeHealth(
        pipelineStatus = pipelineStatus,
        automaticProgressActive = automaticProgressActive,
        observationConfidence = confidence,
        safetyStatus = safetyStatus,
        nativeUpdateAttempted = nativeUpdateAttempted,
        nativeUpdateAccepted = nativeUpdateAccepted,
    )
