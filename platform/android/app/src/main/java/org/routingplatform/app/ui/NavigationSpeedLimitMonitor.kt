package org.routingplatform.app.ui

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import org.routingplatform.app.navigation.NavigationSessionState
import org.routingplatform.app.navigation.NavigationSpeedLimitDataStatus
import org.routingplatform.app.navigation.NavigationUiSnapshot

internal enum class NavigationSpeedLimitWarningKind {
    Approaching500m,
    Approaching150m,
    OverLimit,
}

internal data class NavigationSpeedLimitWarning(
    val routeId: String,
    val beginShapeIndex: Int,
    val limitKmh: Int,
    val kind: NavigationSpeedLimitWarningKind,
    val distanceM: Double? = null,
) {
    val deliveryId: String
        get() = routeId + ":" + beginShapeIndex + ":" + limitKmh + ":" + kind.name
}

internal data class NavigationSpeedLimitPresentation(
    val speedKmh: Int? = null,
    val limitKmh: Int? = null,
    val unlimited: Boolean = false,
    val overLimit: Boolean = false,
    val upcomingLimitKmh: Int? = null,
    val distanceToUpcomingLimitM: Int? = null,
    val dataStatus: NavigationSpeedLimitDataStatus =
        NavigationSpeedLimitDataStatus.Unavailable,
    val label: String = "Tempolimitdaten nicht verfügbar",
)

internal data class NavigationSpeedLimitMonitorUpdate(
    val presentation: NavigationSpeedLimitPresentation,
    val warning: NavigationSpeedLimitWarning? = null,
)

/**
 * Uses only Valhalla edge speed_limit values and accepted, fresh GNSS speed.
 * Missing map tags stay unknown; urban/rural defaults are never guessed.
 */
internal class NavigationSpeedLimitMonitor {
    private var activeRouteId: String? = null
    private var cumulativeDistanceM: DoubleArray = doubleArrayOf()
    private var overLimitSegmentStart: Int? = null
    private var overLimitSampleCount = 0
    private var overLimitActive = false
    private var lastOverLimitAlertNanos: Long? = null

    fun update(
        snapshot: NavigationUiSnapshot,
        speedMps: Double?,
        speedAccuracyMps: Double?,
        speedSampleElapsedRealtimeNanos: Long?,
        speedPositionTrusted: Boolean,
        nowElapsedRealtimeNanos: Long,
        speedingThresholdKmh: Int = 5,
    ): NavigationSpeedLimitMonitorUpdate {
        if (snapshot.routeId != activeRouteId) {
            activeRouteId = snapshot.routeId
            cumulativeDistanceM = cumulativeDistances(snapshot)
            overLimitSegmentStart = null
            overLimitSampleCount = 0
            overLimitActive = false
            lastOverLimitAlertNanos = null
        }

        val shapePosition =
            snapshot.shapeSegmentIndex.toDouble() + snapshot.segmentFraction
        val sortedSegments = snapshot.speedLimitSegments.sortedBy { it.beginShapeIndex }
        val currentSegment = sortedSegments
            .filter { segment ->
                shapePosition >= segment.beginShapeIndex &&
                    shapePosition <= segment.endShapeIndex
            }
            .minByOrNull { it.endShapeIndex - it.beginShapeIndex }
        val currentLimit = currentSegment
            ?.speedLimitKmh
            ?.roundToInt()
            ?.takeIf { it > 0 }

        require(speedingThresholdKmh in setOf(0, 5, 10, 20))

        val sampleIsFresh =
            speedSampleElapsedRealtimeNanos
                ?.let { sampleTime ->
                    nowElapsedRealtimeNanos >= sampleTime &&
                        nowElapsedRealtimeNanos - sampleTime <= MAX_SPEED_SAMPLE_AGE_NANOS
                } == true
        val currentSpeedKmh =
            if (
                speedPositionTrusted &&
                sampleIsFresh &&
                speedMps != null &&
                speedMps.isFinite() &&
                speedMps >= 0.0
            ) {
                (speedMps * METERS_PER_SECOND_TO_KMH).roundToInt()
            } else {
                null
            }

        val upcoming = sortedSegments
            .asSequence()
            .filter { it.beginShapeIndex.toDouble() > shapePosition + SHAPE_INDEX_EPSILON }
            .filter { it.speedLimitKmh != null }
            .filter { segment ->
                val upcomingLimit = segment.speedLimitKmh!!.roundToInt()
                currentLimit == null || upcomingLimit < currentLimit
            }
            .minByOrNull { it.beginShapeIndex }
        val distanceToUpcoming =
            upcoming?.let {
                distanceFromCurrentToShapeIndex(
                    snapshot = snapshot,
                    targetShapeIndex = it.beginShapeIndex,
                )
            }

        var warning: NavigationSpeedLimitWarning? = null
        var overLimit = false
        if (
            snapshot.state == NavigationSessionState.Navigating &&
            currentSegment != null &&
            currentLimit != null &&
            !currentSegment.unlimited &&
            currentSpeedKmh != null
        ) {
            if (overLimitSegmentStart != currentSegment.beginShapeIndex) {
                overLimitSegmentStart = currentSegment.beginShapeIndex
                overLimitSampleCount = 0
                overLimitActive = false
            }
            if (currentSpeedKmh > currentLimit + speedingThresholdKmh) {
                overLimitSampleCount += 1
            } else {
                overLimitSampleCount = 0
                overLimitActive = false
            }
            overLimit = overLimitSampleCount >= REQUIRED_OVER_LIMIT_SAMPLES
            if (overLimit && !overLimitActive) {
                val cooldownElapsed =
                    lastOverLimitAlertNanos
                        ?.let { nowElapsedRealtimeNanos - it >= OVER_LIMIT_REPEAT_COOLDOWN_NANOS }
                        ?: true
                if (cooldownElapsed) {
                    warning = NavigationSpeedLimitWarning(
                        routeId = snapshot.routeId,
                        beginShapeIndex = currentSegment.beginShapeIndex,
                        limitKmh = currentLimit,
                        kind = NavigationSpeedLimitWarningKind.OverLimit,
                    )
                    lastOverLimitAlertNanos = nowElapsedRealtimeNanos
                }
                overLimitActive = true
            }
        } else {
            overLimitSegmentStart = currentSegment?.beginShapeIndex
            overLimitSampleCount = 0
            overLimitActive = false
        }



        val label =
            when {
                currentLimit != null && currentSegment?.unlimited != true ->
                    currentLimit.toString() + " km/h"
                currentSegment?.unlimited == true ->
                    "Unbegrenzt laut Kartendaten"
                snapshot.speedLimitDataStatus == NavigationSpeedLimitDataStatus.Unavailable ->
                    "Tempolimitdaten nicht verfügbar"
                else ->
                    "Tempolimit unbekannt"
            }

        return NavigationSpeedLimitMonitorUpdate(
            presentation = NavigationSpeedLimitPresentation(
                speedKmh = currentSpeedKmh,
                limitKmh = currentLimit,
                unlimited = currentSegment?.unlimited == true,
                overLimit = overLimit,
                upcomingLimitKmh = upcoming?.speedLimitKmh?.roundToInt(),
                distanceToUpcomingLimitM = distanceToUpcoming?.roundToInt(),
                dataStatus = snapshot.speedLimitDataStatus,
                label = label,
            ),
            warning = warning,
        )
    }

    private fun cumulativeDistances(snapshot: NavigationUiSnapshot): DoubleArray {
        val cumulative = DoubleArray(snapshot.geometry.size)
        for (index in 1 until snapshot.geometry.size) {
            cumulative[index] =
                cumulative[index - 1] +
                    distanceMeters(
                        snapshot.geometry[index - 1].latitude,
                        snapshot.geometry[index - 1].longitude,
                        snapshot.geometry[index].latitude,
                        snapshot.geometry[index].longitude,
                    )
        }
        return cumulative
    }

    private fun distanceFromCurrentToShapeIndex(
        snapshot: NavigationUiSnapshot,
        targetShapeIndex: Int,
    ): Double {
        val target = targetShapeIndex.coerceIn(0, snapshot.geometry.lastIndex)
        val currentIndex = snapshot.shapeSegmentIndex
        val currentFraction = snapshot.segmentFraction
        val currentSegmentLength =
            cumulativeDistanceM.getOrElse(currentIndex + 1) { 0.0 } -
                cumulativeDistanceM.getOrElse(currentIndex) { 0.0 }
        val currentDistance =
            cumulativeDistanceM.getOrElse(currentIndex) { 0.0 } +
                currentSegmentLength * currentFraction
        return (cumulativeDistanceM.getOrElse(target) { currentDistance } - currentDistance)
            .coerceAtLeast(0.0)
    }

    private fun distanceMeters(
        latitude1: Double,
        longitude1: Double,
        latitude2: Double,
        longitude2: Double,
    ): Double {
        val lat1 = Math.toRadians(latitude1)
        val lat2 = Math.toRadians(latitude2)
        val deltaLat = lat2 - lat1
        val deltaLon = Math.toRadians(longitude2 - longitude1)
        val sinLat = sin(deltaLat / 2.0)
        val sinLon = sin(deltaLon / 2.0)
        val a =
            sinLat * sinLat +
                cos(lat1) * cos(lat2) * sinLon * sinLon
        return 2.0 * EARTH_RADIUS_M * asin(sqrt(a.coerceIn(0.0, 1.0)))
    }
}

private const val EARTH_RADIUS_M = 6_371_000.0
private const val METERS_PER_SECOND_TO_KMH = 3.6
private const val SHAPE_INDEX_EPSILON = 0.000_001
private const val MAX_SPEED_SAMPLE_AGE_NANOS = 2_000_000_000L
private const val REQUIRED_OVER_LIMIT_SAMPLES = 2
private const val OVER_LIMIT_REPEAT_COOLDOWN_NANOS = 30_000_000_000L
