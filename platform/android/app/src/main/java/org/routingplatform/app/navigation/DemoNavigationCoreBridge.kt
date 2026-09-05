package org.routingplatform.app.navigation

class DemoNavigationCoreBridge :
    NavigationCoreBridge {

    private val routeDistanceM =
        3174.0

    private val routeDurationS =
        242.0

    private val geometry =
        listOf(
            RoutePoint(
                latitude = 47.1410,
                longitude = 9.5209,
            ),
            RoutePoint(
                latitude = 47.1396,
                longitude = 9.5200,
            ),
            RoutePoint(
                latitude = 47.1366,
                longitude = 9.5185,
            ),
            RoutePoint(
                latitude = 47.1326,
                longitude = 9.5149,
            ),
            RoutePoint(
                latitude = 47.1290,
                longitude = 9.5205,
            ),
            RoutePoint(
                latitude = 47.1248,
                longitude = 9.5246,
            ),
        )

    private val maneuvers =
        listOf(
            NavigationManeuver(
                type =
                    ManeuverType.Start,
                instruction =
                    "Route starten",
                distanceM =
                    620.0,
                durationS =
                    48.0,
            ),
            NavigationManeuver(
                type =
                    ManeuverType.TurnRight,
                instruction =
                    "Rechts abbiegen",
                distanceM =
                    780.0,
                durationS =
                    59.0,
            ),
            NavigationManeuver(
                type =
                    ManeuverType.Continue,
                instruction =
                    "Dem Straßenverlauf folgen",
                distanceM =
                    1260.0,
                durationS =
                    91.0,
            ),
            NavigationManeuver(
                type =
                    ManeuverType.Arrive,
                instruction =
                    "Ziel befindet sich voraus",
                distanceM =
                    514.0,
                durationS =
                    44.0,
            ),
        )

    private var started =
        false

    private var progressFraction =
        0.0

    override fun currentSnapshot():
        NavigationUiSnapshot =
        makeSnapshot()

    override fun startNavigation():
        NavigationUiSnapshot {
        started =
            true

        return makeSnapshot()
    }

    override fun updateProgress(
        shapeSegmentIndex: Int,
        segmentFraction: Double,
    ): NavigationUiSnapshot {
        require(
            shapeSegmentIndex in
                0 until geometry.lastIndex
        )

        require(
            segmentFraction in 0.0..1.0
        )

        started =
            true

        progressFraction =
            (
                shapeSegmentIndex.toDouble() +
                    segmentFraction
            ) /
            geometry.lastIndex.toDouble()

        return makeSnapshot()
    }

    fun advanceDemo():
        NavigationUiSnapshot {
        if (!started) {
            started =
                true
        }

        progressFraction =
            (
                progressFraction +
                    0.25
            ).coerceAtMost(
                1.0
            )

        return makeSnapshot()
    }

    private fun makeSnapshot():
        NavigationUiSnapshot {

        val progress =
            progressFraction

        val segmentCount =
            geometry.lastIndex

        val scaledShapePosition =
            progress *
                segmentCount.toDouble()

        val shapeSegmentIndex =
            if (progress >= 1.0) {
                segmentCount - 1
            } else {
                scaledShapePosition
                    .toInt()
                    .coerceAtMost(
                        segmentCount - 1
                    )
            }

        val segmentFraction =
            if (progress >= 1.0) {
                1.0
            } else {
                scaledShapePosition -
                    shapeSegmentIndex.toDouble()
            }

        val arrived =
            progress >= 1.0

        val state =
            when {
                arrived ->
                    NavigationSessionState.Arrived

                started ->
                    NavigationSessionState.Navigating

                else ->
                    NavigationSessionState.Preview
            }

        val maneuverIndex =
            when {
                arrived ->
                    maneuvers.lastIndex

                progress < 0.25 ->
                    0

                progress < 0.50 ->
                    1

                progress < 0.75 ->
                    2

                else ->
                    3
            }

        val currentManeuver =
            maneuvers[maneuverIndex]

        val nextManeuver =
            maneuvers.getOrNull(
                maneuverIndex + 1
            )

        val remainingDistance =
            (
                routeDistanceM *
                    (1.0 - progress)
            ).coerceAtLeast(0.0)

        val remainingDuration =
            (
                routeDurationS *
                    (1.0 - progress)
            ).coerceAtLeast(0.0)

        return NavigationUiSnapshot(
            sessionId =
                "demo-session-1",

            state =
                state,

            routeId =
                "demo-liechtenstein-route",

            routeDistanceM =
                routeDistanceM,

            routeDurationS =
                routeDurationS,

            geometry =
                geometry,

            shapeSegmentIndex =
                shapeSegmentIndex,

            segmentFraction =
                segmentFraction,

            progressFraction =
                progress,

            remainingDistanceM =
                remainingDistance,

            remainingDurationS =
                remainingDuration,

            currentManeuver =
                currentManeuver,

            nextManeuver =
                nextManeuver,

            distanceToCurrentManeuverEndM =
                if (arrived) {
                    0.0
                } else {
                    currentManeuver.distanceM *
                        (1.0 - progress)
                },

            arrived =
                arrived,

            rerouteRequested =
                false,

            routeRecomputed =
                false,

            routingEngineInvoked =
                false,

            candidateSelectionInvoked =
                false,

            costEngineInvoked =
                false,

            productionRouteMutationAllowed =
                false,
        )
    }
}
