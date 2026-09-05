package org.routingplatform.app.navigation

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

enum class NavigationPositionConfidence {
    High,
    Medium,
    Low,
    Lost,
}

data class PositionCovariance2D(
    val eastVarianceM2: Double,
    val northVarianceM2: Double,
    val eastNorthCovarianceM2: Double = 0.0,
) {
    init {
        require(
            eastVarianceM2.isFinite() &&
                eastVarianceM2 >= 0.0
        ) {
            "eastVarianceM2 must be finite and non-negative."
        }

        require(
            northVarianceM2.isFinite() &&
                northVarianceM2 >= 0.0
        ) {
            "northVarianceM2 must be finite and non-negative."
        }

        require(
            eastNorthCovarianceM2.isFinite()
        ) {
            "eastNorthCovarianceM2 must be finite."
        }

        val covarianceLimit =
            sqrt(
                eastVarianceM2 *
                    northVarianceM2
            )

        require(
            abs(
                eastNorthCovarianceM2
            ) <=
                covarianceLimit +
                    COVARIANCE_EPSILON
        ) {
            "Covariance matrix must be positive semidefinite."
        }
    }

    val conservativeHorizontalSigmaM: Double
        get() =
            sqrt(
                max(
                    eastVarianceM2,
                    northVarianceM2,
                )
            )
}

data class NavigationPositionEstimate(
    val position: RoutePoint,
    val horizontalVelocityMps: Double?,
    val bearingDegrees: Double?,
    val covariance: PositionCovariance2D,
    val confidence: NavigationPositionConfidence,
    val elapsedRealtimeNanos: Long,
) {
    init {
        require(
            position.latitude.isFinite() &&
                position.latitude in -90.0..90.0
        ) {
            "Position latitude is invalid."
        }

        require(
            position.longitude.isFinite() &&
                position.longitude in -180.0..180.0
        ) {
            "Position longitude is invalid."
        }

        require(
            horizontalVelocityMps == null ||
                (
                    horizontalVelocityMps.isFinite() &&
                        horizontalVelocityMps >= 0.0
                )
        ) {
            "horizontalVelocityMps must be non-negative and finite."
        }

        require(
            bearingDegrees == null ||
                (
                    bearingDegrees.isFinite() &&
                        bearingDegrees >= 0.0 &&
                        bearingDegrees < 360.0
                )
        ) {
            "bearingDegrees must be in [0, 360)."
        }

        require(
            elapsedRealtimeNanos >= 0L
        ) {
            "elapsedRealtimeNanos must not be negative."
        }
    }

    val mayDriveNavigationProgress: Boolean
        get() =
            confidence ==
                NavigationPositionConfidence.High ||
                confidence ==
                    NavigationPositionConfidence.Medium
}

interface NavigationPositionEstimator {
    fun observe(
        sample: NavigationLocationSample,
    ): NavigationPositionEstimate?

    fun reset()
}

private const val COVARIANCE_EPSILON =
    1.0e-9