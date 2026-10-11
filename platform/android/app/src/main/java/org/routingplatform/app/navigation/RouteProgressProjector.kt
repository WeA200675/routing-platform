package org.routingplatform.app.navigation

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sqrt

data class RouteProgressAnchor(
    val shapeSegmentIndex: Int,
    val segmentFraction: Double,
) {
    init {
        require(shapeSegmentIndex >= 0) {
            "shapeSegmentIndex must not be negative."
        }

        require(
            segmentFraction in 0.0..1.0
        ) {
            "segmentFraction must be in [0, 1]."
        }
    }

    val shapePosition: Double
        get() =
            shapeSegmentIndex.toDouble() +
                segmentFraction
}

data class RouteProjection(
    val shapeSegmentIndex: Int,
    val segmentFraction: Double,
    val projectedPosition: RoutePoint,
    val distanceToRouteM: Double,
) {
    init {
        require(shapeSegmentIndex >= 0)

        require(
            segmentFraction in 0.0..1.0
        )

        require(
            distanceToRouteM.isFinite() &&
                distanceToRouteM >= 0.0
        )
    }

    val shapePosition: Double
        get() =
            shapeSegmentIndex.toDouble() +
                segmentFraction

    val progressAnchor: RouteProgressAnchor
        get() =
            RouteProgressAnchor(
                shapeSegmentIndex =
                    shapeSegmentIndex,

                segmentFraction =
                    segmentFraction,
            )
}

class RouteProgressProjector(
    private val maxDistanceToRouteM: Double,
) {
    init {
        require(
            maxDistanceToRouteM.isFinite() &&
                maxDistanceToRouteM > 0.0
        ) {
            "maxDistanceToRouteM must be positive and finite."
        }
    }

    var lastAcceptedProjection:
        RouteProjection? =
        null
        private set

    fun project(
        points: List<RoutePoint>,
        position: RoutePoint,
    ): RouteProjection? {

        val candidate =
            projectPointToRoute(
                points =
                    points,

                position =
                    position,

                minimumProgress =
                    lastAcceptedProjection
                        ?.progressAnchor,
            )

        if (
            candidate.distanceToRouteM >
                maxDistanceToRouteM
        ) {
            return null
        }

        lastAcceptedProjection =
            candidate

        return candidate
    }

    fun reset() {
        lastAcceptedProjection =
            null
    }
}

fun projectPointToRoute(
    points: List<RoutePoint>,
    position: RoutePoint,
    minimumProgress: RouteProgressAnchor? = null,
): RouteProjection {

    require(points.size >= 2) {
        "Route geometry requires at least two points."
    }

    validateRoutePoint(
        position,
        "position",
    )

    points.forEachIndexed {
            index,
            point ->

        validateRoutePoint(
            point,
            "points[$index]",
        )
    }

    if (minimumProgress != null) {
        require(
            minimumProgress.shapeSegmentIndex in
                0 until points.lastIndex
        ) {
            "minimumProgress must reference a route segment."
        }
    }

    val firstSegmentIndex =
        minimumProgress
            ?.shapeSegmentIndex
            ?: 0

    var best:
        RouteProjection? =
        null

    for (
        segmentIndex in
            firstSegmentIndex until points.lastIndex
    ) {
        val minimumFraction =
            if (
                minimumProgress != null &&
                segmentIndex ==
                    minimumProgress
                        .shapeSegmentIndex
            ) {
                minimumProgress
                    .segmentFraction
            } else {
                0.0
            }

        val candidate =
            projectToSegment(
                points =
                    points,

                position =
                    position,

                segmentIndex =
                    segmentIndex,

                minimumFraction =
                    minimumFraction,
            )

        val currentBest =
            best

        if (
            currentBest == null ||
            candidate.distanceToRouteM <
                currentBest.distanceToRouteM -
                    DISTANCE_TIE_EPSILON_M ||
            (
                abs(
                    candidate.distanceToRouteM -
                        currentBest.distanceToRouteM
                ) <=
                    DISTANCE_TIE_EPSILON_M &&
                    candidate.shapePosition <
                        currentBest.shapePosition
            )
        ) {
            best =
                candidate
        }
    }

    return checkNotNull(best) {
        "No route segment available for projection."
    }
}

private fun projectToSegment(
    points: List<RoutePoint>,
    position: RoutePoint,
    segmentIndex: Int,
    minimumFraction: Double,
): RouteProjection {

    val from =
        points[
            segmentIndex
        ]

    val to =
        points[
            segmentIndex + 1
        ]

    val referenceLatitudeRad =
        (
            (
                from.latitude +
                    to.latitude +
                    position.latitude
            ) /
                3.0
        ) *
            DEGREES_TO_RADIANS

    val longitudeScale =
        cos(
            referenceLatitudeRad
        )

    val fromX =
        (
            from.longitude -
                position.longitude
        ) *
            DEGREES_TO_RADIANS *
            EARTH_RADIUS_M *
            longitudeScale

    val fromY =
        (
            from.latitude -
                position.latitude
        ) *
            DEGREES_TO_RADIANS *
            EARTH_RADIUS_M

    val toX =
        (
            to.longitude -
                position.longitude
        ) *
            DEGREES_TO_RADIANS *
            EARTH_RADIUS_M *
            longitudeScale

    val toY =
        (
            to.latitude -
                position.latitude
        ) *
            DEGREES_TO_RADIANS *
            EARTH_RADIUS_M

    val segmentX =
        toX -
            fromX

    val segmentY =
        toY -
            fromY

    val segmentLengthSquared =
        segmentX *
            segmentX +
            segmentY *
            segmentY

    val rawFraction =
        if (
            segmentLengthSquared >
                SEGMENT_LENGTH_EPSILON_SQUARED
        ) {
            -(
                fromX *
                    segmentX +
                    fromY *
                    segmentY
            ) /
                segmentLengthSquared
        } else {
            0.0
        }

    val fraction =
        rawFraction.coerceIn(
            minimumFraction,
            1.0,
        )

    val projectedX =
        fromX +
            segmentX *
            fraction

    val projectedY =
        fromY +
            segmentY *
            fraction

    val distanceM =
        sqrt(
            projectedX *
                projectedX +
                projectedY *
                projectedY
        )

    var normalizedSegmentIndex =
        segmentIndex

    var normalizedFraction =
        fraction

    if (
        normalizedFraction >=
            1.0 -
                FRACTION_EPSILON &&
        segmentIndex <
            points.lastIndex - 1
    ) {
        normalizedSegmentIndex =
            segmentIndex + 1

        normalizedFraction =
            0.0
    }

    val projectedPosition =
        if (
            normalizedSegmentIndex !=
                segmentIndex
        ) {
            to
        } else {
            RoutePoint(
                latitude =
                    from.latitude +
                        (
                            to.latitude -
                                from.latitude
                        ) *
                        normalizedFraction,

                longitude =
                    from.longitude +
                        (
                            to.longitude -
                                from.longitude
                        ) *
                        normalizedFraction,
            )
        }

    return RouteProjection(
        shapeSegmentIndex =
            normalizedSegmentIndex,

        segmentFraction =
            normalizedFraction,

        projectedPosition =
            projectedPosition,

        distanceToRouteM =
            distanceM,
    )
}

private fun validateRoutePoint(
    point: RoutePoint,
    label: String,
) {
    require(
        point.latitude.isFinite() &&
            point.latitude in -90.0..90.0
    ) {
        "$label latitude is invalid."
    }

    require(
        point.longitude.isFinite() &&
            point.longitude in -180.0..180.0
    ) {
        "$label longitude is invalid."
    }
}

private const val EARTH_RADIUS_M =
    6371008.8

private const val DEGREES_TO_RADIANS =
    PI / 180.0

private const val FRACTION_EPSILON =
    1.0e-12

private const val DISTANCE_TIE_EPSILON_M =
    1.0e-6

private const val SEGMENT_LENGTH_EPSILON_SQUARED =
    1.0e-12