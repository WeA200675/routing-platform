package org.routingplatform.app.navigation

import kotlin.math.cos
import kotlin.math.pow

/** Keeps only intermediate stops that are still ahead on the installed route. */
internal object NavigationViaPointProgressPolicy {
    fun remaining(
        request: NavigationRouteRequest,
        snapshot: NavigationUiSnapshot,
    ): List<RoutePoint> {
        if (request.viaPoints.isEmpty()) return emptyList()
        if (snapshot.geometry.size < 2) return request.viaPoints
        val currentIndex = snapshot.shapeSegmentIndex.coerceIn(0, snapshot.geometry.lastIndex)
        return request.viaPoints.filter { via ->
            val nearest = snapshot.geometry.indices.minByOrNull { index ->
                squaredDistanceMeters(via, snapshot.geometry[index])
            } ?: return@filter true
            nearest >= currentIndex
        }
    }

    private fun squaredDistanceMeters(a: RoutePoint, b: RoutePoint): Double {
        val latScale = 111_320.0
        val lonScale = latScale * cos(Math.toRadians((a.latitude + b.latitude) / 2.0)).coerceAtLeast(0.1)
        return ((a.latitude - b.latitude) * latScale).pow(2) +
            ((a.longitude - b.longitude) * lonScale).pow(2)
    }
}
