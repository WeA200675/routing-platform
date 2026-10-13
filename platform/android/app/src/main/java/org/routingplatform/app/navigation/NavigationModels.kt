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
) {
    /**
     * Presentation-safe maneuver text. Valhalla's raw maneuver type distinguishes
     * left/right ramps and exits; never speak the opposite side if provider text
     * contradicts that authoritative type.
     */
    val guidanceInstruction: String
        get() {
            val expectedSide = when (engineType) {
                9, 10, 11, 18, 20, 23, 37 -> "right"
                14, 15, 16, 19, 21, 24, 38 -> "left"
                else -> null
            } ?: return instruction

            val saysLeft = Regex("""\\b(links?|linke[nrms]?|left)\\b""", RegexOption.IGNORE_CASE)
                .containsMatchIn(instruction)
            val saysRight = Regex("""\\b(rechts?|rechte[nrms]?|right)\\b""", RegexOption.IGNORE_CASE)
                .containsMatchIn(instruction)
            val contradicts = if (expectedSide == "right") saysLeft && !saysRight else saysRight && !saysLeft
            if (!contradicts) return instruction

            val german = Regex("""\\b(links?|rechts?|ausfahrt|abbiegen|einfädeln)\\b""", RegexOption.IGNORE_CASE)
                .containsMatchIn(instruction)
            return when {
                german && (engineType == 20 || engineType == 21) ->
                    if (expectedSide == "right") "Ausfahrt rechts nehmen. Bitte Beschilderung beachten."
                    else "Ausfahrt links nehmen. Bitte Beschilderung beachten."
                german && (engineType == 18 || engineType == 19 || engineType == 37 || engineType == 38) ->
                    if (expectedSide == "right") "Rechts einfädeln. Bitte Beschilderung beachten."
                    else "Links einfädeln. Bitte Beschilderung beachten."
                german ->
                    if (expectedSide == "right") "Rechts abbiegen. Bitte Beschilderung beachten."
                    else "Links abbiegen. Bitte Beschilderung beachten."
                engineType == 20 || engineType == 21 ->
                    if (expectedSide == "right") "Take the exit on the right. Follow road signs."
                    else "Take the exit on the left. Follow road signs."
                else ->
                    if (expectedSide == "right") "Turn right. Follow road signs."
                    else "Turn left. Follow road signs."
            }
        }
}

enum class NavigationSpeedLimitDataStatus {
    Unavailable,
    Partial,
    Complete,
}

data class NavigationSpeedLimitSegment(
    val beginShapeIndex: Int,
    val endShapeIndex: Int,
    val speedLimitKmh: Double? = null,
    val unlimited: Boolean = false,
    val isUrban: Boolean? = null,
) {
    init {
        require(beginShapeIndex >= 0)
        require(endShapeIndex >= beginShapeIndex)
        require(speedLimitKmh == null || (
            speedLimitKmh.isFinite() && speedLimitKmh in 1.0..300.0
        ))
        require(!unlimited || speedLimitKmh == null)
    }
}

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

    val speedLimitSegments:
        List<NavigationSpeedLimitSegment> =
        emptyList(),

    val speedLimitDataStatus:
        NavigationSpeedLimitDataStatus =
        NavigationSpeedLimitDataStatus.Unavailable,
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