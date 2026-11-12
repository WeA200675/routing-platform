package org.routingplatform.app.navigation

import kotlin.math.cos
import kotlin.math.sin

/**
 * Short prediction horizon used only for visual stabilization and brief GNSS
 * outages. Predicted points never replace measured positions for routing.
 */
internal data class NavigationPredictedPoint(
    val point: RoutePoint,
    val horizonIndex: Int,
    val secondsAhead: Double,
    val confidence: Double,
)

internal object NavigationPredictionHorizon {
    fun predict(
        estimate: NavigationPositionEstimate,
        count: Int = 10,
        stepSeconds: Double = 0.25,
    ): List<NavigationPredictedPoint> {
        require(count in 1..10)
        require(stepSeconds in 0.05..2.0)
        val speed = estimate.horizontalVelocityMps ?: return emptyList()
        val bearing = estimate.bearingDegrees ?: return emptyList()
        if (speed <= 0.2 || estimate.confidence == NavigationPositionConfidence.Lost) return emptyList()
        val sigma = estimate.covariance.conservativeHorizontalSigmaM
        return (1..count).map { index ->
            val seconds = index * stepSeconds
            val distance = speed * seconds
            val latitude = estimate.position.latitude +
                Math.toDegrees(distance * cos(Math.toRadians(bearing)) / EARTH_RADIUS_M)
            val longitude = estimate.position.longitude +
                Math.toDegrees(distance * sin(Math.toRadians(bearing)) /
                    (EARTH_RADIUS_M * cos(Math.toRadians(estimate.position.latitude)).coerceAtLeast(0.1)))
            NavigationPredictedPoint(
                point = RoutePoint(latitude, longitude),
                horizonIndex = index,
                secondsAhead = seconds,
                confidence = (1.0 - sigma / 80.0 - seconds / 8.0).coerceIn(0.05, 0.98),
            )
        }
    }

    private const val EARTH_RADIUS_M = 6_371_000.0
}
