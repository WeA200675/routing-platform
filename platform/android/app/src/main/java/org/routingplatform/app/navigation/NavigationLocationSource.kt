package org.routingplatform.app.navigation

data class NavigationLocationSample(
    val position: RoutePoint,
    val horizontalAccuracyM: Double?,
    val elapsedRealtimeNanos: Long,
    val provider: String?,
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
    }
}

interface NavigationLocationSource {
    fun start(
        onLocation:
            (NavigationLocationSample) -> Unit,
    ): Boolean

    fun stop()
}