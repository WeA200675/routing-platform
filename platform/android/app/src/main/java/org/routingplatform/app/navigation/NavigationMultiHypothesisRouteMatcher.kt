package org.routingplatform.app.navigation

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sqrt

data class NavigationRouteHypothesis(
    val anchor: RouteProgressAnchor,
    val projectedPosition: RoutePoint,
    val distanceToRouteM: Double,
    val routeBearingDegrees: Double,
    val headingDeltaDegrees: Double?,
    val alongRouteDistanceM: Double,
    val score: Double,
)

data class NavigationRouteMatchResult(
    val hypotheses: List<NavigationRouteHypothesis>,
) {
    val best:
        NavigationRouteHypothesis?
        get() =
            hypotheses.firstOrNull()

    val second:
        NavigationRouteHypothesis?
        get() =
            hypotheses.getOrNull(
                1
            )

    val ambiguityMargin: Double?
        get() {
            val first =
                best
                    ?: return null

            val secondBest =
                second
                    ?: return null

            return secondBest.score -
                first.score
        }
}

class NavigationMultiHypothesisRouteMatcher(
    private val maximumHypotheses: Int = 4,
    private val headingWeight: Double = 1.5,
    private val continuityWeight: Double = 0.002,
    private val backwardPenalty: Double = 20.0,
) {
    init {
        require(
            maximumHypotheses >= 2
        )

        require(
            headingWeight >= 0.0 &&
                headingWeight.isFinite()
        )

        require(
            continuityWeight >= 0.0 &&
                continuityWeight.isFinite()
        )

        require(
            backwardPenalty >= 0.0 &&
                backwardPenalty.isFinite()
        )
    }

    fun match(
        route: List<RoutePoint>,
        estimate: NavigationPositionEstimate,
        previousProgress: RouteProgressAnchor? = null,
    ): NavigationRouteMatchResult {

        require(
            route.size >= 2
        )

        val routeDistances =
            cumulativeRouteDistances(
                route
            )

        val previousAlongRouteM =
            previousProgress?.let {
                alongRouteDistanceMeters(
                    route =
                        route,

                    cumulativeDistances =
                        routeDistances,

                    anchor =
                        it,
                )
            }

        val uncertaintyM =
            max(
                MINIMUM_POSITION_SIGMA_M,
                estimate
                    .covariance
                    .conservativeHorizontalSigmaM,
            )

        val hypotheses =
            (0 until route.lastIndex)
                .map {
                        segmentIndex ->

                    val projection =
                        projectToSegment(
                            start =
                                route[segmentIndex],

                            end =
                                route[
                                    segmentIndex +
                                        1
                                ],

                            position =
                                estimate.position,
                        )

                    val anchor =
                        RouteProgressAnchor(
                            shapeSegmentIndex =
                                segmentIndex,

                            segmentFraction =
                                projection.fraction,
                        )

                    val alongRouteM =
                        routeDistances[
                            segmentIndex
                        ] +
                            projection.segmentLengthM *
                                projection.fraction

                    val headingDelta =
                        estimate
                            .bearingDegrees
                            ?.let {
                                angularDistanceDegrees(
                                    it,
                                    projection.bearingDegrees,
                                )
                            }

                    val distanceScore =
                        projection.distanceM /
                            uncertaintyM

                    val headingScore =
                        headingDelta
                            ?.let {
                                (
                                    it /
                                        45.0
                                ) *
                                    headingWeight
                            }
                            ?: 0.0

                    val continuityDelta =
                        previousAlongRouteM
                            ?.let {
                                alongRouteM -
                                    it
                            }

                    val continuityScore =
                        continuityDelta
                            ?.let {
                                abs(
                                    it
                                ) *
                                    continuityWeight
                            }
                            ?: 0.0

                    val backwardsScore =
                        if (
                            continuityDelta != null &&
                            continuityDelta <
                                -BACKWARD_TOLERANCE_M
                        ) {
                            backwardPenalty
                        } else {
                            0.0
                        }

                    NavigationRouteHypothesis(
                        anchor =
                            anchor,

                        projectedPosition =
                            projection.projectedPosition,

                        distanceToRouteM =
                            projection.distanceM,

                        routeBearingDegrees =
                            projection.bearingDegrees,

                        headingDeltaDegrees =
                            headingDelta,

                        alongRouteDistanceM =
                            alongRouteM,

                        score =
                            distanceScore +
                                headingScore +
                                continuityScore +
                                backwardsScore,
                    )
                }
                .sortedWith(
                    compareBy<
                        NavigationRouteHypothesis
                    > {
                        it.score
                    }.thenBy {
                        it.alongRouteDistanceM
                    }
                )
                .take(
                    maximumHypotheses
                )

        return NavigationRouteMatchResult(
            hypotheses =
                hypotheses
        )
    }
}

fun routeLengthMeters(
    route: List<RoutePoint>,
): Double {
    require(
        route.size >= 2
    )

    return cumulativeRouteDistances(
        route
    ).last()
}

fun alongRouteDistanceMeters(
    route: List<RoutePoint>,
    anchor: RouteProgressAnchor,
): Double =
    alongRouteDistanceMeters(
        route =
            route,

        cumulativeDistances =
            cumulativeRouteDistances(
                route
            ),

        anchor =
            anchor,
    )

private fun alongRouteDistanceMeters(
    route: List<RoutePoint>,
    cumulativeDistances: DoubleArray,
    anchor: RouteProgressAnchor,
): Double {

    require(
        anchor.shapeSegmentIndex in
            0 until route.lastIndex
    )

    val segmentLength =
        distanceMeters(
            route[
                anchor.shapeSegmentIndex
            ],
            route[
                anchor.shapeSegmentIndex +
                    1
            ],
        )

    return cumulativeDistances[
        anchor.shapeSegmentIndex
    ] +
        segmentLength *
            anchor.segmentFraction
}

private data class SegmentProjection(
    val fraction: Double,
    val projectedPosition: RoutePoint,
    val distanceM: Double,
    val segmentLengthM: Double,
    val bearingDegrees: Double,
)

private fun projectToSegment(
    start: RoutePoint,
    end: RoutePoint,
    position: RoutePoint,
): SegmentProjection {

    val referenceLatitudeRadians =
        (
            (
                start.latitude +
                    end.latitude +
                    position.latitude
            ) /
                3.0
        ) *
            DEGREES_TO_RADIANS

    fun eastMeters(
        longitude: Double,
    ): Double =
        longitude *
            DEGREES_TO_RADIANS *
            EARTH_RADIUS_M *
            cos(
                referenceLatitudeRadians
            )

    fun northMeters(
        latitude: Double,
    ): Double =
        latitude *
            DEGREES_TO_RADIANS *
            EARTH_RADIUS_M

    val startX =
        eastMeters(
            start.longitude
        )

    val startY =
        northMeters(
            start.latitude
        )

    val endX =
        eastMeters(
            end.longitude
        )

    val endY =
        northMeters(
            end.latitude
        )

    val positionX =
        eastMeters(
            position.longitude
        )

    val positionY =
        northMeters(
            position.latitude
        )

    val dx =
        endX -
            startX

    val dy =
        endY -
            startY

    val lengthSquared =
        dx *
            dx +
            dy *
                dy

    val fraction =
        if (
            lengthSquared <=
                SEGMENT_EPSILON
        ) {
            0.0
        } else {
            (
                (
                    positionX -
                        startX
                ) *
                    dx +
                    (
                        positionY -
                            startY
                    ) *
                        dy
            )
                .div(
                    lengthSquared
                )
                .coerceIn(
                    0.0,
                    1.0,
                )
        }

    val projectedX =
        startX +
            fraction *
                dx

    val projectedY =
        startY +
            fraction *
                dy

    val distanceX =
        positionX -
            projectedX

    val distanceY =
        positionY -
            projectedY

    val distanceM =
        sqrt(
            distanceX *
                distanceX +
                distanceY *
                    distanceY
        )

    val segmentLength =
        sqrt(
            lengthSquared
        )

    val projected =
        RoutePoint(
            latitude =
                start.latitude +
                    (
                        end.latitude -
                            start.latitude
                    ) *
                        fraction,

            longitude =
                start.longitude +
                    (
                        end.longitude -
                            start.longitude
                    ) *
                        fraction,
        )

    val bearing =
        normalizeDegrees(
            atan2(
                dx,
                dy,
            ) *
                RADIANS_TO_DEGREES
        )

    return SegmentProjection(
        fraction =
            fraction,

        projectedPosition =
            projected,

        distanceM =
            distanceM,

        segmentLengthM =
            segmentLength,

        bearingDegrees =
            bearing,
    )
}

private fun cumulativeRouteDistances(
    route: List<RoutePoint>,
): DoubleArray {

    val result =
        DoubleArray(
            route.size
        )

    for (
        index in
            1 until route.size
    ) {
        result[index] =
            result[
                index -
                    1
            ] +
                distanceMeters(
                    route[
                        index -
                            1
                    ],
                    route[
                        index
                    ],
                )
    }

    return result
}

private fun distanceMeters(
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
            DEGREES_TO_RADIANS

    val east =
        (
            second.longitude -
                first.longitude
        ) *
            DEGREES_TO_RADIANS *
            EARTH_RADIUS_M *
            cos(
                meanLatitudeRadians
            )

    val north =
        (
            second.latitude -
                first.latitude
        ) *
            DEGREES_TO_RADIANS *
            EARTH_RADIUS_M

    return sqrt(
        east *
            east +
            north *
                north
    )
}

private fun angularDistanceDegrees(
    first: Double,
    second: Double,
): Double {

    val difference =
        normalizeDegrees(
            first -
                second
        )

    return if (
        difference >
            180.0
    ) {
        360.0 -
            difference
    } else {
        difference
    }
}

private fun normalizeDegrees(
    degrees: Double,
): Double =
    (
        (
            degrees %
                360.0
        ) +
            360.0
    ) %
        360.0

private const val EARTH_RADIUS_M =
    6371008.8

private const val DEGREES_TO_RADIANS =
    PI / 180.0

private const val RADIANS_TO_DEGREES =
    180.0 / PI

private const val MINIMUM_POSITION_SIGMA_M =
    3.0

private const val BACKWARD_TOLERANCE_M =
    3.0

private const val SEGMENT_EPSILON =
    1.0e-9