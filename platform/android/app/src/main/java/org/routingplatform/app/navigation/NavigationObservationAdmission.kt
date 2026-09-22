package org.routingplatform.app.navigation

/**
 * Platform-neutral admission contract used by Android today and by future iOS
 * measurement adapters. It contains no OS/manufacturer types.
 */
object NavigationObservationAdmission {
    fun calibrationAvailable(capabilities: NavigationDeviceCapabilities): Boolean =
        capabilities.preciseLocationAvailable &&
            capabilities.directObservationAvailable &&
            capabilities.monotonicTimestampAvailable

    fun directFreshObservation(observation: NavigationCalibrationObservation): Boolean {
        val accuracy = observation.horizontalAccuracyM
        return observation.confidence == NavigationPositionConfidence.High &&
            observation.fusionMode == NavigationFusionMode.DirectObservation &&
            observation.elapsedRealtimeNanos != null &&
            observation.elapsedRealtimeNanos >= 0L &&
            accuracy != null &&
            accuracy.isFinite() &&
            accuracy in 0.0..100.0
    }
}
