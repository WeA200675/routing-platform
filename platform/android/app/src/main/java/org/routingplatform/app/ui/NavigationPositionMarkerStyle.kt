package org.routingplatform.app.ui

import kotlin.math.cos
import kotlin.math.sin

enum class NavigationPositionMarkerStyle {
    ARROW,
    CAR,
    DOT;

    companion object {
        fun parse(value: String?): NavigationPositionMarkerStyle =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: ARROW
    }
}

internal fun positionMarkerGeoJson(
    presentation: NavigationObservedPositionPresentation,
    style: NavigationPositionMarkerStyle,
    bearingDegrees: Double?,
): String {
    val point = presentation.position
    if (style == NavigationPositionMarkerStyle.DOT || bearingDegrees == null || !bearingDegrees.isFinite()) {
        return "{\"type\":\"FeatureCollection\",\"features\":[{\"type\":\"Feature\",\"properties\":{},\"geometry\":{\"type\":\"Point\",\"coordinates\":[${point.longitude},${point.latitude}]}}]}"
    }
    val angle = Math.toRadians(bearingDegrees)
    val size = if (style == NavigationPositionMarkerStyle.CAR) 0.00012 else 0.00016
    val half = size * if (style == NavigationPositionMarkerStyle.CAR) 0.58 else 0.55
    val base = listOf(0.0 to size, half to -half, -half to -half)
    val coords = base.map { (x, y) ->
        val rx = x * cos(angle) - y * sin(angle)
        val ry = x * sin(angle) + y * cos(angle)
        "[${point.longitude + rx},${point.latitude + ry}]"
    }.joinToString(",")
    val first = "[${point.longitude},${point.latitude + size}]"
    return """{"type":"FeatureCollection","features":[{"type":"Feature","properties":{},"geometry":{"type":"Polygon","coordinates":[[$coords,$first]]}}]}"""
}
