package org.routingplatform.app.navigation

data class NavigationLocationSample(
    val position: RoutePoint,
    val horizontalAccuracyM: Double?,
    val elapsedRealtimeNanos: Long,
    val provider: String?,
    val speedMps: Double? = null,
    val speedAccuracyMps: Double? = null,
    val bearingDegrees: Double? = null,
    val bearingAccuracyDegrees: Double? = null,
) {
    init {
        require(
            position.latitude.isFinite() &&
                position.latitude in -90.0..90.0
        ) {
            "Location latitude is invalid."
        }

        require(
            position.longitude.isFinite() &&
                position.longitude in -180.0..180.0
        ) {
            "Location longitude is invalid."
        }

        require(
            horizontalAccuracyM == null ||
                (
                    horizontalAccuracyM.isFinite() &&
                        horizontalAccuracyM >= 0.0
                )
        ) {
            "Location accuracy must be non-negative and finite."
        }

        require(
            elapsedRealtimeNanos >= 0L
        ) {
            "elapsedRealtimeNanos must not be negative."
        }
        require(speedMps == null || (speedMps.isFinite() && speedMps >= 0.0))
        require(speedAccuracyMps == null || (speedAccuracyMps.isFinite() && speedAccuracyMps >= 0.0))
        require(bearingDegrees == null || (bearingDegrees.isFinite() && bearingDegrees >= 0.0 && bearingDegrees < 360.0))
        require(bearingAccuracyDegrees == null || (bearingAccuracyDegrees.isFinite() && bearingAccuracyDegrees >= 0.0))
    }
}

interface NavigationLocationSource {
    fun start(
        onLocation:
            (NavigationLocationSample) -> Unit,
    ): Boolean

    fun stop()
}