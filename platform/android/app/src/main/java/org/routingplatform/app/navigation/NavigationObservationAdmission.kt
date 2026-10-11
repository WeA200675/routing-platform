package org.routingplatform.app.navigation

/**
 * Platform-neutral admission contract used by Android today and by future iOS
 * measurement adapters. It contains no OS/manufacturer types.
 */
object NavigationObservationAdmission {
    const val MAX_DIRECT_OBSERVATION_ACCURACY_M = 100.0

    fun calibrationAvailable(capabilities: NavigationDeviceCapabilities): Boolean =
        capabilities.preciseLocationAvailable &&
            capabilities.directObservationAvailable &&
            capabilities.monotonicTimestampAvailable

    fun directFreshObservation(observation: NavigationCalibrationObservation): Boolean {
        val accuracy = observation.horizontalAccuracyM
        val elapsedRealtimeNanos = observation.elapsedRealtimeNanos
        return observation.confidence == NavigationPositionConfidence.High &&
            observation.fusionMode == NavigationFusionMode.DirectObservation &&
            elapsedRealtimeNanos != null &&
            elapsedRealtimeNanos >= 0L &&
            accuracy != null &&
            accuracy.isFinite() &&
            accuracy in 0.0..MAX_DIRECT_OBSERVATION_ACCURACY_M
    }

    /**
     * P19 observability contract: expose only bounded semantic health flags.
     * No coordinates, route geometry, sensor payloads or security evidence leave
     * the admission boundary through this diagnostic snapshot.
     */
    fun healthSnapshot(
        capabilities: NavigationDeviceCapabilities,
        observation: NavigationCalibrationObservation?,
    ): NavigationAdmissionHealth =
        NavigationAdmissionHealth(
            calibrationAvailable = calibrationAvailable(capabilities),
            directFreshObservationAvailable =
                observation?.let(::directFreshObservation) ?: false,
        )
}

data class NavigationAdmissionHealth(
    val calibrationAvailable: Boolean,
    val directFreshObservationAvailable: Boolean,
)
