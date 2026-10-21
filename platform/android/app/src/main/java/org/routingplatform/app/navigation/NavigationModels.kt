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

enum class NavigationManeuverSide {
    Left,
    Right,
    Straight,
    UTurn,
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

    val destinationSign: String? = null,

    /** Provider supplied lane indications; absent data stays absent. */
    val laneIndications: List<String> = emptyList(),

    /** Side supplied by the routing engine, normalized across providers. */
    val maneuverSide: NavigationManeuverSide? = null,
) {
    /**
     * Presentation-safe maneuver text. Valhalla's raw maneuver type distinguishes
     * left/right ramps and exits; never speak the opposite side if provider text
     * contradicts that authoritative type.
     */
    val guidanceInstruction: String
        get() {
            val engineSide = valhallaManeuverSide(engineType)
            val normalizedSide = when (maneuverSide) {
                NavigationManeuverSide.Left -> "left"
                NavigationManeuverSide.Right -> "right"
                NavigationManeuverSide.UTurn -> "uturn"
                NavigationManeuverSide.Straight, null -> null
            }
            val typeSide = when (type) {
                ManeuverType.TurnLeft -> "left"
                ManeuverType.TurnRight -> "right"
                ManeuverType.UTurn -> "uturn"
                else -> null
            }
            val expectedSide = engineSide ?: normalizedSide ?: typeSide
            val measuredSide = bearingTurnSide(bearingBeforeDeg, bearingAfterDeg)
            val saysLeft = Regex("""\b(links?|linke[nrms]?|linken|linker|linkes|linkem|left)\b""", RegexOption.IGNORE_CASE).containsMatchIn(instruction)
            val saysRight = Regex("""\b(rechts?|rechte[nrms]?|rechten|rechter|rechtes|rechtem|right)\b""", RegexOption.IGNORE_CASE).containsMatchIn(instruction)
            val saysUTurn = Regex("""\b(umkehren|wenden|u-turn|uturn)\b""", RegexOption.IGNORE_CASE).containsMatchIn(instruction)
            val isExit = type == ManeuverType.Exit || engineType == 20 || engineType == 21
            val isUTurn = type == ManeuverType.UTurn || engineType == 12 || engineType == 13
            val isRamp = engineType == 18 || engineType == 19
            val isMerge = type == ManeuverType.Merge || engineType == 25 || engineType == 37 || engineType == 38
            val german = Regex("""\b(links?|rechts?|ausfahrt|abbiegen|einfädeln|wenden)\b""", RegexOption.IGNORE_CASE)
                .containsMatchIn(instruction)

            fun uncertain(): String =
                if (german) "Manöverdaten widersprüchlich. Bitte Beschilderung beachten."
                else "Maneuver data conflicts. Follow the road signs."

            fun directionOnly(): String? = when {
                isExit && expectedSide == "right" ->
                    if (german) "Ausfahrt rechts nehmen. Bitte Beschilderung beachten." else "Take the exit on the right. Follow road signs."
                isExit && expectedSide == "left" ->
                    if (german) "Ausfahrt links nehmen. Bitte Beschilderung beachten." else "Take the exit on the left. Follow road signs."
                isUTurn || expectedSide == "uturn" ->
                    if (german) "Wenden. Bitte Beschilderung beachten." else "Make a U-turn. Follow road signs."
                isRamp && expectedSide == "right" ->
                    if (german) "Rampe rechts nehmen. Bitte Beschilderung beachten." else "Take the ramp on the right. Follow road signs."
                isRamp && expectedSide == "left" ->
                    if (german) "Rampe links nehmen. Bitte Beschilderung beachten." else "Take the ramp on the left. Follow road signs."
                isRamp && expectedSide == "right" ->
                    if (german) "Rampe rechts nehmen. Bitte Beschilderung beachten." else "Take the ramp on the right. Follow road signs."
                isRamp && expectedSide == "left" ->
                    if (german) "Rampe links nehmen. Bitte Beschilderung beachten." else "Take the ramp on the left. Follow road signs."
                isMerge && expectedSide == "right" ->
                    if (german) "Rechts einfädeln. Bitte Beschilderung beachten." else "Merge right. Follow road signs."
                isMerge && expectedSide == "left" ->
                    if (german) "Links einfädeln. Bitte Beschilderung beachten." else "Merge left. Follow road signs."
                expectedSide == "right" ->
                    if (german) "Rechts abbiegen. Bitte Beschilderung beachten." else "Turn right. Follow road signs."
                expectedSide == "left" ->
                    if (german) "Links abbiegen. Bitte Beschilderung beachten." else "Turn left. Follow road signs."
                else -> null
            }

            val hasRoadConnector = Regex("""\b(auf|in die|in den|in das|onto|into)\b""", RegexOption.IGNORE_CASE)
                .containsMatchIn(instruction)
            val routeNameAppears = streetNames.any { it.length >= 3 && instruction.contains(it, ignoreCase = true) } ||
                destinationSign?.let { it.length >= 3 && instruction.contains(it, ignoreCase = true) } == true
            if (hasRoadConnector && streetNames.isNotEmpty() && !routeNameAppears) {
                return directionOnly() ?: uncertain()
            }

            // Keep one authoritative interpretation. Conflicting provider fields or
            // route bearings make direction and street-name narration unsafe.
            if (engineSide != null && normalizedSide != null && engineSide != normalizedSide) return uncertain()
            if (engineSide != null && typeSide != null && engineSide != typeSide) return uncertain()
            if (expectedSide != null && measuredSide != null && expectedSide != measuredSide) return uncertain()

            if ((isExit || isUTurn) && expectedSide == null && (saysLeft || saysRight || saysUTurn)) {
                return if (isExit) {
                    if (german) "Ausfahrt nehmen. Bitte Beschilderung beachten."
                    else "Take the exit. Follow road signs."
                } else {
                    if (german) "Wenden. Bitte Beschilderung beachten."
                    else "Make a U-turn. Follow road signs."
                }
            }

            val contradicts = when (expectedSide) {
                "right" -> saysLeft || saysUTurn
                "left" -> saysRight || saysUTurn
                "uturn" -> saysLeft || saysRight
                else -> false
            }
            if (!contradicts) return instruction

            return when {
                isExit && expectedSide == "right" ->
                    if (german) "Ausfahrt rechts nehmen. Bitte Beschilderung beachten."
                    else "Take the exit on the right. Follow road signs."
                isExit && expectedSide == "left" ->
                    if (german) "Ausfahrt links nehmen. Bitte Beschilderung beachten."
                    else "Take the exit on the left. Follow road signs."
                isUTurn ->
                    if (german) "Wenden. Bitte Beschilderung beachten."
                    else "Make a U-turn. Follow road signs."
                isMerge && expectedSide == "right" ->
                    if (german) "Rechts einfädeln. Bitte Beschilderung beachten."
                    else "Merge right. Follow road signs."
                isMerge && expectedSide == "left" ->
                    if (german) "Links einfädeln. Bitte Beschilderung beachten."
                    else "Merge left. Follow road signs."
                expectedSide == "right" ->
                    if (german) "Rechts abbiegen. Bitte Beschilderung beachten."
                    else "Turn right. Follow road signs."
                expectedSide == "left" ->
                    if (german) "Links abbiegen. Bitte Beschilderung beachten."
                    else "Turn left. Follow road signs."
                else -> uncertain()
            }
        }
}

private fun valhallaManeuverSide(engineType: Int?): String? =
    when (engineType) {
        9, 10, 11, 18, 20, 23, 37 -> "right"
        12, 13 -> "uturn"
        14, 15, 16, 19, 21, 24, 38 -> "left"
        else -> null
    }

/** Positive bearing change is clockwise (right); negative is counter-clockwise (left). */
private fun bearingTurnSide(before: Int?, after: Int?): String? {
    if (before == null || after == null || before !in 0..359 || after !in 0..359) return null
    val signedDelta = ((after - before + 540) % 360) - 180
    return when {
        kotlin.math.abs(signedDelta) < 25 -> null
        kotlin.math.abs(signedDelta) > 155 -> "uturn"
        signedDelta > 0 -> "right"
        else -> "left"
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
