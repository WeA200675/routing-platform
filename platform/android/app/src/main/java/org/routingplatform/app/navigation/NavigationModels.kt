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

    val streetNames:
        List<String> =
        emptyList(),

    val beginShapeIndex:
        Int? =
        null,

    val endShapeIndex:
        Int? =
        null,

    val bearingBeforeDeg:
        Int? =
        null,

    val bearingAfterDeg:
        Int? =
        null,

    val engineType:
        Int? =
        null,
)

data class NavigationUiSnapshot(
    val schemaVersion: Int = 1,
    val sessionId: String,
    val state: NavigationSessionState,
    val routeId: String,

    val routeDistanceM: Double,
    val routeDurationS: Double,

    val geometry: List<RoutePoint>,

    val shapeSegmentIndex: Int = 0,
    val segmentFraction: Double = 0.0,

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

    // Route-preview metadata supplied by the installed route contract.
    val routeManeuvers:
        List<NavigationManeuver> =
        emptyList(),

    val engineName:
        String? =
        null,

    val engineVersion:
        String? =
        null,

    val segmentDataStatus:
        NavigationRouteSegmentDataStatus =
        NavigationRouteSegmentDataStatus.Unspecified,

    val routeDiagnostics:
        List<NavigationRouteDiagnostic> =
        emptyList(),
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

        require(
            shapeSegmentIndex in
                0 until geometry.lastIndex
        ) {
            "shapeSegmentIndex must reference a route segment."
        }

        require(segmentFraction in 0.0..1.0) {
            "segmentFraction must be in [0, 1]."
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