package org.routingplatform.app.navigation

import kotlin.math.floor

fun buildNavigationStartRoadContext(
    maneuvers:
        List<NavigationManeuver>,
): NavigationStartRoadContext? {

    val roadName =
        maneuvers
            .asSequence()
            .flatMap {
                it.streetNames
                    .asSequence()
            }
            .firstOrNull {
                it.isNotBlank()
            }
            ?: return null

    /*
     * street_names proves the route road name.
     *
     * It does NOT prove an opposite carriageway destination,
     * route reference classification, or direction label.
     * Those remain null until graph-derived evidence exists.
     */
    return NavigationStartRoadContext(
        routeRoadName =
            roadName,

        routeRoadRef =
            null,

        routeDirectionLabel =
            null,

        oppositeRoadName =
            null,

        oppositeRoadRef =
            null,

        oppositeDirectionLabel =
            null,
    )
}

fun buildNavigationRouteEvents(
    routePointCount: Int,
    maneuvers:
        List<NavigationManeuver>,
): List<NavigationRouteEvent> {

    require(
        routePointCount >= 2
    )

    return maneuvers.mapNotNull {
            maneuver ->

        val beginShapeIndex =
            maneuver.beginShapeIndex
                ?: return@mapNotNull null

        val anchor =
            shapeIndexToProgressAnchor(
                routePointCount =
                    routePointCount,

                shapeIndex =
                    beginShapeIndex,
            )

        NavigationRouteEvent(
            anchor =
                anchor,

            type =
                maneuver.type,

            instruction =
                maneuver.instruction
                    .takeIf {
                        it.isNotBlank()
                    },

            roadName =
                maneuver.streetNames
                    .firstOrNull(),

            roadRef =
                null,

            directionLabel =
                null,
        )
    }
}

fun buildDiagnosticProgressAnchors(
    routePointCount: Int,
    stepCount:
        Int =
        6,
): List<RouteProgressAnchor> {

    require(
        routePointCount >= 2
    )

    require(
        stepCount > 0
    )

    val finalShapePosition =
        (
            routePointCount -
                1
        ).toDouble()

    val finalSegmentIndex =
        routePointCount -
            2

    return (
        1..stepCount
    ).map {
            step ->

        val shapePosition =
            finalShapePosition *
                step.toDouble() /
                stepCount.toDouble()

        if (
            shapePosition >=
                finalShapePosition
        ) {
            RouteProgressAnchor(
                shapeSegmentIndex =
                    finalSegmentIndex,

                segmentFraction =
                    1.0,
            )
        } else {
            val segmentIndex =
                floor(
                    shapePosition
                )
                    .toInt()
                    .coerceIn(
                        0,
                        finalSegmentIndex,
                    )

            RouteProgressAnchor(
                shapeSegmentIndex =
                    segmentIndex,

                segmentFraction =
                    (
                        shapePosition -
                            segmentIndex.toDouble()
                    ).coerceIn(
                        0.0,
                        1.0,
                    ),
            )
        }
    }
}

private fun shapeIndexToProgressAnchor(
    routePointCount: Int,
    shapeIndex: Int,
): RouteProgressAnchor {

    require(
        shapeIndex in
            0 until routePointCount
    )

    val finalPointIndex =
        routePointCount -
            1

    return if (
        shapeIndex >=
            finalPointIndex
    ) {
        RouteProgressAnchor(
            shapeSegmentIndex =
                finalPointIndex -
                    1,

            segmentFraction =
                1.0,
        )
    } else {
        RouteProgressAnchor(
            shapeSegmentIndex =
                shapeIndex,

            segmentFraction =
                0.0,
        )
    }
}