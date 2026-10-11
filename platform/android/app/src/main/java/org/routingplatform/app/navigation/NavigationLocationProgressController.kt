package org.routingplatform.app.navigation

class NavigationLocationProgressController(
    private val maxHorizontalAccuracyM: Double,
    private val maxDistanceToRouteM: Double,
) {
    init {
        require(
            maxHorizontalAccuracyM.isFinite() &&
                maxHorizontalAccuracyM > 0.0
        ) {
            "maxHorizontalAccuracyM must be positive and finite."
        }

        require(
            maxDistanceToRouteM.isFinite() &&
                maxDistanceToRouteM > 0.0
        ) {
            "maxDistanceToRouteM must be positive and finite."
        }
    }

    private var lastSeenElapsedRealtimeNanos:
        Long? =
        null

    fun project(
        snapshot: NavigationUiSnapshot,
        sample: NavigationLocationSample,
    ): RouteProjection? {

        if (
            snapshot.state !=
                NavigationSessionState.Navigating
        ) {
            return null
        }

        val previousElapsedRealtimeNanos =
            lastSeenElapsedRealtimeNanos

        if (
            previousElapsedRealtimeNanos != null &&
            sample.elapsedRealtimeNanos <=
                previousElapsedRealtimeNanos
        ) {
            return null
        }

        lastSeenElapsedRealtimeNanos =
            sample.elapsedRealtimeNanos

        val horizontalAccuracyM =
            sample.horizontalAccuracyM

        if (
            horizontalAccuracyM != null &&
            horizontalAccuracyM >
                maxHorizontalAccuracyM
        ) {
            return null
        }

        val minimumProgress =
            RouteProgressAnchor(
                shapeSegmentIndex =
                    snapshot.shapeSegmentIndex,

                segmentFraction =
                    snapshot.segmentFraction,
            )

        val projection =
            projectPointToRoute(
                points =
                    snapshot.geometry,

                position =
                    sample.position,

                minimumProgress =
                    minimumProgress,
            )

        if (
            projection.distanceToRouteM >
                maxDistanceToRouteM
        ) {
            return null
        }

        if (
            projection.shapePosition <=
                minimumProgress.shapePosition +
                    PROGRESS_EPSILON
        ) {
            return null
        }

        return projection
    }

    fun reset() {
        lastSeenElapsedRealtimeNanos =
            null
    }
}

private const val PROGRESS_EPSILON =
    1.0e-9