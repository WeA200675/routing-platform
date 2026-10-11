package org.routingplatform.app.navigation

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sqrt

enum class NavigationStartOrientationConfidence {
    High,
    Medium,
    Unknown,
}

data class NavigationStartRoadContext(
    val routeRoadName: String?,
    val routeRoadRef: String?,
    val routeDirectionLabel: String?,
    val oppositeRoadName: String?,
    val oppositeRoadRef: String?,
    val oppositeDirectionLabel: String?,
) {
    init {
        require(
            routeRoadName == null ||
                routeRoadName.isNotBlank()
        )

        require(
            routeRoadRef == null ||
                routeRoadRef.isNotBlank()
        )

        require(
            routeDirectionLabel == null ||
                routeDirectionLabel.isNotBlank()
        )

        require(
            oppositeRoadName == null ||
                oppositeRoadName.isNotBlank()
        )

        require(
            oppositeRoadRef == null ||
                oppositeRoadRef.isNotBlank()
        )

        require(
            oppositeDirectionLabel == null ||
                oppositeDirectionLabel.isNotBlank()
        )
    }
}

data class NavigationStartOrientationInfo(
    val routeBearingDegrees: Double,
    val routeCardinalDirection: String,
    val oppositeBearingDegrees: Double,
    val oppositeCardinalDirection: String,

    val routeRoadName: String?,
    val routeRoadRef: String?,
    val routeDirectionLabel: String?,

    val oppositeRoadName: String?,
    val oppositeRoadRef: String?,
    val oppositeDirectionLabel: String?,

    val confidence:
        NavigationStartOrientationConfidence,
)

class NavigationStartOrientationEngine(
    private val minimumBearingBaselineM:
        Double =
        20.0,
) {
    init {
        require(
            minimumBearingBaselineM.isFinite() &&
                minimumBearingBaselineM > 0.0
        )
    }

    fun calculate(
        route: List<RoutePoint>,
        roadContext:
            NavigationStartRoadContext? =
            null,
    ): NavigationStartOrientationInfo {

        require(
            route.size >= 2
        )

        val start =
            route.first()

        var target =
            route[1]

        var accumulatedDistance =
            0.0

        for (
            index in
                1 until route.size
        ) {
            val previous =
                route[
                    index -
                        1
                ]

            val current =
                route[index]

            accumulatedDistance +=
                startOrientationDistanceMeters(
                    previous,
                    current,
                )

            target =
                current

            if (
                accumulatedDistance >=
                    minimumBearingBaselineM
            ) {
                break
            }
        }

        val routeBearing =
            startOrientationBearingDegrees(
                start,
                target,
            )

        val oppositeBearing =
            normalizeStartBearing(
                routeBearing +
                    180.0
            )

        val hasDirectionalMetadata =
            roadContext
                ?.let {
                    it.routeDirectionLabel !=
                        null ||
                        it.oppositeDirectionLabel !=
                            null
                }
                ?: false

        return NavigationStartOrientationInfo(
            routeBearingDegrees =
                routeBearing,

            routeCardinalDirection =
                cardinalDirection(
                    routeBearing
                ),

            oppositeBearingDegrees =
                oppositeBearing,

            oppositeCardinalDirection =
                cardinalDirection(
                    oppositeBearing
                ),

            routeRoadName =
                roadContext?.routeRoadName,

            routeRoadRef =
                roadContext?.routeRoadRef,

            routeDirectionLabel =
                roadContext
                    ?.routeDirectionLabel,

            oppositeRoadName =
                roadContext
                    ?.oppositeRoadName,

            oppositeRoadRef =
                roadContext
                    ?.oppositeRoadRef,

            oppositeDirectionLabel =
                roadContext
                    ?.oppositeDirectionLabel,

            confidence =
                if (
                    hasDirectionalMetadata
                ) {
                    NavigationStartOrientationConfidence.High
                } else {
                    NavigationStartOrientationConfidence.Medium
                },
        )
    }
}

data class NavigationRouteEvent(
    val anchor:
        RouteProgressAnchor,

    val type:
        ManeuverType,

    val instruction:
        String?,

    val roadName:
        String?,

    val roadRef:
        String?,

    val directionLabel:
        String?,
)

data class NavigationRouteEventAhead(
    val event:
        NavigationRouteEvent,

    val distanceAheadM:
        Double,
)

class NavigationExitLookaheadEngine(
    private val maximumLookaheadM:
        Double =
        1_500.0,
) {
    init {
        require(
            maximumLookaheadM.isFinite() &&
                maximumLookaheadM > 0.0
        )
    }

    fun eventsAhead(
        route:
            List<RoutePoint>,

        currentProgress:
            RouteProgressAnchor,

        events:
            List<NavigationRouteEvent>,
    ): List<NavigationRouteEventAhead> {

        val currentDistance =
            alongRouteDistanceMeters(
                route =
                    route,

                anchor =
                    currentProgress,
            )

        return events
            .mapNotNull {
                    event ->

                val eventDistance =
                    alongRouteDistanceMeters(
                        route =
                            route,

                        anchor =
                            event.anchor,
                    )

                val distanceAhead =
                    eventDistance -
                        currentDistance

                if (
                    distanceAhead <
                        0.0 ||
                    distanceAhead >
                        maximumLookaheadM
                ) {
                    null
                } else {
                    NavigationRouteEventAhead(
                        event =
                            event,

                        distanceAheadM =
                            distanceAhead,
                    )
                }
            }
            .sortedBy {
                it.distanceAheadM
            }
    }

    fun criticalEventAhead(
        route:
            List<RoutePoint>,

        currentProgress:
            RouteProgressAnchor,

        events:
            List<NavigationRouteEvent>,
    ): NavigationRouteEventAhead? =
        eventsAhead(
            route =
                route,

            currentProgress =
                currentProgress,

            events =
                events,
        )
            .firstOrNull {
                it.event.type in
                    CRITICAL_MANEUVER_TYPES
            }
}

class NavigationStartOrientationVisibilityController(
    private val maximumVisibleDistanceM:
        Double =
        150.0,

    private val maximumVisibleDurationNanos:
        Long =
        30_000_000_000L,
) {
    init {
        require(
            maximumVisibleDistanceM >
                0.0
        )

        require(
            maximumVisibleDurationNanos >
                0L
        )
    }

    fun shouldShow(
        state:
            NavigationSessionState,

        distanceFromStartM:
            Double,

        navigationElapsedNanos:
            Long,
    ): Boolean {

        if (
            state ==
                NavigationSessionState.Arrived
        ) {
            return false
        }

        if (
            state ==
                NavigationSessionState.Preview
        ) {
            return true
        }

        if (
            distanceFromStartM >=
                maximumVisibleDistanceM
        ) {
            return false
        }

        if (
            navigationElapsedNanos >=
                maximumVisibleDurationNanos
        ) {
            return false
        }

        return true
    }
}

/*
 * Supplies graph-derived road direction metadata later.
 * Geometry-only code never invents a destination label.
 */
interface NavigationStartRoadContextProvider {
    fun contextForStart(
        routeId: String,
    ): NavigationStartRoadContext?
}

private fun startOrientationBearingDegrees(
    from: RoutePoint,
    to: RoutePoint,
): Double {

    val meanLatitudeRadians =
        (
            (
                from.latitude +
                    to.latitude
            ) /
                2.0
        ) *
            START_DEGREES_TO_RADIANS

    val east =
        (
            to.longitude -
                from.longitude
        ) *
            START_DEGREES_TO_RADIANS *
            START_EARTH_RADIUS_M *
            cos(
                meanLatitudeRadians
            )

    val north =
        (
            to.latitude -
                from.latitude
        ) *
            START_DEGREES_TO_RADIANS *
            START_EARTH_RADIUS_M

    if (
        sqrt(
            east *
                east +
                north *
                    north
        ) <=
        1.0e-6
    ) {
        return 0.0
    }

    return normalizeStartBearing(
        atan2(
            east,
            north,
        ) *
            START_RADIANS_TO_DEGREES
    )
}

private fun startOrientationDistanceMeters(
    first: RoutePoint,
    second: RoutePoint,
): Double {

    val meanLatitudeRadians =
        (
            (
                first.latitude +
                    second.latitude
            ) /
                2.0
        ) *
            START_DEGREES_TO_RADIANS

    val east =
        (
            second.longitude -
                first.longitude
        ) *
            START_DEGREES_TO_RADIANS *
            START_EARTH_RADIUS_M *
            cos(
                meanLatitudeRadians
            )

    val north =
        (
            second.latitude -
                first.latitude
        ) *
            START_DEGREES_TO_RADIANS *
            START_EARTH_RADIUS_M

    return sqrt(
        east *
            east +
            north *
                north
    )
}

private fun cardinalDirection(
    bearing: Double,
): String {

    val normalized =
        normalizeStartBearing(
            bearing
        )

    return when {
        normalized < 22.5 ||
            normalized >= 337.5 ->
            "N"

        normalized < 67.5 ->
            "NE"

        normalized < 112.5 ->
            "E"

        normalized < 157.5 ->
            "SE"

        normalized < 202.5 ->
            "S"

        normalized < 247.5 ->
            "SW"

        normalized < 292.5 ->
            "W"

        else ->
            "NW"
    }
}

private fun normalizeStartBearing(
    bearing: Double,
): Double =
    (
        (
            bearing %
                360.0
        ) +
            360.0
    ) %
        360.0

private val CRITICAL_MANEUVER_TYPES =
    setOf(
        ManeuverType.Exit,
        ManeuverType.Merge,
        ManeuverType.TurnLeft,
        ManeuverType.TurnRight,
        ManeuverType.UTurn,
        ManeuverType.RoundaboutEnter,
        ManeuverType.RoundaboutExit,
    )

private const val START_EARTH_RADIUS_M =
    6371008.8

private const val START_DEGREES_TO_RADIANS =
    PI / 180.0

private const val START_RADIANS_TO_DEGREES =
    180.0 / PI