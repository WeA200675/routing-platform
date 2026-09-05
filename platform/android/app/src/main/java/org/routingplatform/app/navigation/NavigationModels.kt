package org.routingplatform.app.navigation

enum class NavigationSessionState {
    Preview,
    Navigating,
    Arrived,
}

enum class ManeuverType {
    Unknown,
    Start,
    Continue,
    TurnLeft,
    TurnRight,
    UTurn,
    Merge,
    Exit,
    RoundaboutEnter,
    RoundaboutExit,
    Arrive,
}

data class RoutePoint(
    val latitude: Double,
    val longitude: Double,
)

data class NavigationManeuver(
    val type: ManeuverType,
    val instruction: String,
    val distanceM: Double,
    val durationS: Double,
)

data class NavigationUiSnapshot(
    val schemaVersion: Int = 1,
    val sessionId: String,
    val state: NavigationSessionState,
    val routeId: String,

    val routeDistanceM: Double,
    val routeDurationS: Double,

    val geometry: List<RoutePoint>,

    val progressFraction: Double,
    val remainingDistanceM: Double,
    val remainingDurationS: Double,

    val currentManeuver: NavigationManeuver?,
    val nextManeuver: NavigationManeuver?,
    val distanceToCurrentManeuverEndM: Double,

    val arrived: Boolean,

    // Mirrors the hard boundaries in NavigationSnapshot schema v1.
    val rerouteRequested: Boolean = false,
    val routeRecomputed: Boolean = false,
    val routingEngineInvoked: Boolean = false,
    val candidateSelectionInvoked: Boolean = false,
    val costEngineInvoked: Boolean = false,
    val productionRouteMutationAllowed: Boolean = false,
) {
    init {
        require(schemaVersion == 1) {
            "Only Navigation Runtime schema v1 is supported."
        }

        require(sessionId.isNotBlank()) {
            "sessionId must not be blank."
        }

        require(routeId.isNotBlank()) {
            "routeId must not be blank."
        }

        require(routeDistanceM > 0.0) {
            "routeDistanceM must be positive."
        }

        require(routeDurationS > 0.0) {
            "routeDurationS must be positive."
        }

        require(geometry.size >= 2) {
            "Route geometry needs at least two points."
        }

        require(progressFraction in 0.0..1.0) {
            "progressFraction must be in [0, 1]."
        }

        require(remainingDistanceM >= 0.0)
        require(remainingDurationS >= 0.0)
        require(distanceToCurrentManeuverEndM >= 0.0)
    }

    val presentationBoundaryIntact: Boolean
        get() =
            !rerouteRequested &&
                !routeRecomputed &&
                !routingEngineInvoked &&
                !candidateSelectionInvoked &&
                !costEngineInvoked &&
                !productionRouteMutationAllowed
}
