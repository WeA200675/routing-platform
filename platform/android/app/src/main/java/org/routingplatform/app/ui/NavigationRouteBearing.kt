package org.routingplatform.app.ui

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import org.routingplatform.app.navigation.RoutePoint

internal object NavigationRouteBearing {
    fun at(points: List<RoutePoint>, shapeSegmentIndex: Int): Double? {
        if (shapeSegmentIndex !in 0 until points.lastIndex) return null
        val from = points[shapeSegmentIndex]
        val to = points[shapeSegmentIndex + 1]
        val lat1 = Math.toRadians(from.latitude)
        val lat2 = Math.toRadians(to.latitude)
        val deltaLon = Math.toRadians(to.longitude - from.longitude)
        val y = sin(deltaLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(deltaLon)
        if (!x.isFinite() || !y.isFinite() || (x == 0.0 && y == 0.0)) return null
        val degrees = Math.toDegrees(atan2(y, x)) % FULL_TURN_DEGREES
        return if (degrees < 0.0) degrees + FULL_TURN_DEGREES else degrees
    }
}

private const val FULL_TURN_DEGREES = 360.0
