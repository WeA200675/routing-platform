package org.routingplatform.app.navigation

data class RouteProgressGeometry(
    val traveledPoints: List<RoutePoint>,
    val remainingPoints: List<RoutePoint>,
    val currentPosition: RoutePoint,
)

fun splitRouteProgressGeometry(
    points: List<RoutePoint>,
    shapeSegmentIndex: Int,
    segmentFraction: Double,
): RouteProgressGeometry {

    require(points.size >= 2) {
        "Route geometry requires at least two points."
    }

    require(
        shapeSegmentIndex in
            0 until points.lastIndex
    ) {
        "shapeSegmentIndex must reference a route segment."
    }

    require(
        segmentFraction in 0.0..1.0
    ) {
        "segmentFraction must be in [0, 1]."
    }

    val from =
        points[
            shapeSegmentIndex
        ]

    val to =
        points[
            shapeSegmentIndex + 1
        ]

    val currentPosition =
        RoutePoint(
            latitude =
                from.latitude +
                    (
                        to.latitude -
                            from.latitude
                    ) *
                    segmentFraction,

            longitude =
                from.longitude +
                    (
                        to.longitude -
                            from.longitude
                    ) *
                    segmentFraction,
        )

    val traveled =
        buildList {
            addAll(
                points.take(
                    shapeSegmentIndex + 1
                )
            )

            add(
                currentPosition
            )
        }

    val remaining =
        buildList {
            add(
                currentPosition
            )

            addAll(
                points.drop(
                    shapeSegmentIndex + 1
                )
            )
        }

    return RouteProgressGeometry(
        traveledPoints =
            traveled,

        remainingPoints =
            remaining,

        currentPosition =
            currentPosition,
    )
}