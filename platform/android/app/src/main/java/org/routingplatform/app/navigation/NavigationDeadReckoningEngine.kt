package org.routingplatform.app.navigation

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

data class NavigationDeadReckoningPolicy(
    val maximumPredictionSeconds:
        Double =
        10.0,

    val uncertaintyGrowthMPerSecond:
        Double =
        4.0,

    val minimumUncertaintyM:
        Double =
        5.0,
) {
    init {
        require(
            maximumPredictionSeconds.isFinite() &&
                maximumPredictionSeconds > 0.0
        )

        require(
            uncertaintyGrowthMPerSecond.isFinite() &&
                uncertaintyGrowthMPerSecond > 0.0
        )

        require(
            minimumUncertaintyM.isFinite() &&
                minimumUncertaintyM > 0.0
        )
    }
}

class NavigationDeadReckoningEngine(
    private val policy:
        NavigationDeadReckoningPolicy =
        NavigationDeadReckoningPolicy(),
) {
    fun predict(
        from:
            NavigationPositionEstimate,

        targetElapsedRealtimeNanos:
            Long,

        travelBearingDegrees:
            Double?,
    ): NavigationPositionEstimate? {

        if (
            targetElapsedRealtimeNanos <=
                from.elapsedRealtimeNanos
        ) {
            return null
        }

        val elapsedSeconds =
            (
                targetElapsedRealtimeNanos -
                    from.elapsedRealtimeNanos
            ).toDouble() /
                NANOS_PER_SECOND

        if (
            elapsedSeconds >
                policy.maximumPredictionSeconds
        ) {
            return null
        }

        val speedMps =
            from.horizontalVelocityMps
                ?: return null

        if (
            !speedMps.isFinite() ||
            speedMps < 0.0
        ) {
            return null
        }

        val bearing =
            travelBearingDegrees
                ?: from.bearingDegrees
                ?: return null

        val bearingRadians =
            bearing *
                DEGREES_TO_RADIANS

        val distanceM =
            speedMps *
                elapsedSeconds

        val northM =
            cos(
                bearingRadians
            ) *
                distanceM

        val eastM =
            sin(
                bearingRadians
            ) *
                distanceM

        val latitudeRadians =
            from.position.latitude *
                DEGREES_TO_RADIANS

        val latitudeDeltaDegrees =
            (
                northM /
                    EARTH_RADIUS_M
            ) *
                RADIANS_TO_DEGREES

        val longitudeScale =
            EARTH_RADIUS_M *
                max(
                    MINIMUM_LONGITUDE_COSINE,
                    kotlin.math.abs(
                        cos(
                            latitudeRadians
                        )
                    ),
                )

        val longitudeDeltaDegrees =
            (
                eastM /
                    longitudeScale
            ) *
                RADIANS_TO_DEGREES

        val previousSigma =
            from
                .covariance
                .conservativeHorizontalSigmaM

        val sigmaM =
            max(
                policy.minimumUncertaintyM,
                previousSigma +
                    policy
                        .uncertaintyGrowthMPerSecond *
                    elapsedSeconds,
            )

        return NavigationPositionEstimate(
            position =
                RoutePoint(
                    latitude =
                        (
                            from.position.latitude +
                                latitudeDeltaDegrees
                        ).coerceIn(
                            -90.0,
                            90.0,
                        ),

                    longitude =
                        normalizeLongitude(
                            from.position.longitude +
                                longitudeDeltaDegrees
                        ),
                ),

            horizontalVelocityMps =
                speedMps,

            bearingDegrees =
                normalizeBearing(
                    bearing
                ),

            covariance =
                PositionCovariance2D(
                    eastVarianceM2 =
                        sigmaM *
                            sigmaM,

                    northVarianceM2 =
                        sigmaM *
                            sigmaM,
                ),

            confidence =
                NavigationPositionConfidence.Low,

            elapsedRealtimeNanos =
                targetElapsedRealtimeNanos,
        )
    }
}

private fun normalizeBearing(
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

private fun normalizeLongitude(
    longitude: Double,
): Double {

    var result =
        longitude

    while (
        result >
            180.0
    ) {
        result -=
            360.0
    }

    while (
        result <
            -180.0
    ) {
        result +=
            360.0
    }

    return result
}

private const val EARTH_RADIUS_M =
    6371008.8

private const val DEGREES_TO_RADIANS =
    PI / 180.0

private const val RADIANS_TO_DEGREES =
    180.0 / PI

private const val NANOS_PER_SECOND =
    1_000_000_000.0

private const val MINIMUM_LONGITUDE_COSINE =
    1.0e-6