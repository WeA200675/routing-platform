package org.routingplatform.app.navigation

/**
 * Platform-neutral calibration contract. Platform adapters provide observations;
 * authoritative navigation never consumes manufacturer/model names.
 */
data class NavigationDeviceCapabilities(
    val preciseLocationAvailable: Boolean,
    val directObservationAvailable: Boolean,
    val monotonicTimestampAvailable: Boolean,
)

data class NavigationCalibrationObservation(
    val confidence: NavigationPositionConfidence,
    val fusionMode: NavigationFusionMode?,
    val horizontalAccuracyM: Double?,
    val elapsedRealtimeNanos: Long?,
)

data class NavigationDeviceCalibrationProfile(
    val schemaVersion: Int = 1,
    val acceptedDirectSamples: Int = 0,
    val rejectedSamples: Int = 0,
    val bestObservedAccuracyM: Double? = null,
) {
    init {
        require(schemaVersion == 1)
        require(acceptedDirectSamples >= 0 && rejectedSamples >= 0)
        require(bestObservedAccuracyM == null || (bestObservedAccuracyM.isFinite() && bestObservedAccuracyM >= 0.0))
    }
}

/**
 * Deterministic, bounded learner. It records quality evidence only: it has no
 * coordinate, route, ETA, traffic, maneuver or progress fields by design.
 */
object NavigationDeviceCalibration {
    fun observe(
        profile: NavigationDeviceCalibrationProfile,
        observation: NavigationCalibrationObservation,
    ): NavigationDeviceCalibrationProfile {
        val accuracy = observation.horizontalAccuracyM
        val admissible =
            observation.confidence == NavigationPositionConfidence.High &&
                observation.fusionMode == NavigationFusionMode.DirectObservation &&
                observation.elapsedRealtimeNanos != null &&
                observation.elapsedRealtimeNanos >= 0L &&
                accuracy != null &&
                accuracy.isFinite() &&
                accuracy in 0.0..100.0

        if (!admissible) {
            return profile.copy(rejectedSamples = if (profile.rejectedSamples == Int.MAX_VALUE) Int.MAX_VALUE else profile.rejectedSamples + 1)
        }

        return profile.copy(
            acceptedDirectSamples = if (profile.acceptedDirectSamples == Int.MAX_VALUE) Int.MAX_VALUE else profile.acceptedDirectSamples + 1,
            bestObservedAccuracyM =
                profile.bestObservedAccuracyM?.let { minOf(it, accuracy!!) } ?: accuracy,
        )
    }
}
