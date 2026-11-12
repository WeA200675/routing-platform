package org.routingplatform.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationPredictionHorizonTest {
    @Test
    fun predictsBoundedTenPointHorizonWithoutChangingMeasuredPosition() {
        val estimate = NavigationPositionEstimate(
            position = RoutePoint(48.0, 11.0),
            horizontalVelocityMps = 20.0,
            bearingDegrees = 90.0,
            covariance = PositionCovariance2D(4.0, 4.0),
            confidence = NavigationPositionConfidence.High,
            elapsedRealtimeNanos = 1L,
        )
        val prediction = NavigationPredictionHorizon.predict(estimate)
        assertEquals(10, prediction.size)
        assertEquals(48.0, estimate.position.latitude, 0.0)
        assertTrue(prediction.last().point.longitude > estimate.position.longitude)
        assertTrue(prediction.zipWithNext().all { it.first.confidence >= it.second.confidence })
    }
}
