package org.routingplatform.app.navigation

fun remainingRouteDistanceKm(
    geometry: List<RoutePoint>,
    shapeSegmentIndex: Int,
    segmentFraction: Double,
): Double {
    if (geometry.size < 2) return 0.0
    val index = shapeSegmentIndex.coerceIn(0, geometry.size - 2)
    val fraction = segmentFraction.coerceIn(0.0, 1.0)
    var meters = distanceMeters(
        geometry[index],
        geometry[index + 1],
    ) * (1.0 - fraction)
    for (i in index + 1 until geometry.lastIndex) {
        meters += distanceMeters(geometry[i], geometry[i + 1])
    }
    return meters / 1_000.0
}

private fun distanceMeters(a: RoutePoint, b: RoutePoint): Double {
    val earth = 6_371_000.0
    val dLat = Math.toRadians(b.latitude - a.latitude)
    val dLon = Math.toRadians(b.longitude - a.longitude)
    val h = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
        kotlin.math.cos(Math.toRadians(a.latitude)) *
        kotlin.math.cos(Math.toRadians(b.latitude)) *
        kotlin.math.sin(dLon / 2) * kotlin.math.sin(dLon / 2)
    return earth * 2.0 * kotlin.math.atan2(kotlin.math.sqrt(h), kotlin.math.sqrt(1.0 - h))
}
