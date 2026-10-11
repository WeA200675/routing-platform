package org.routingplatform.app.navigation

import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToInt
import kotlin.math.abs

/** Local aggregate only: no route coordinates or destination names are retained. */
data class CompletedDrive(
    val completedAtEpochMillis: Long,
    val distanceMeters: Int,
    val durationSeconds: Int,
    val routeFamily: String,
) {
    init {
        require(completedAtEpochMillis >= 0L)
        require(distanceMeters > 0)
        require(durationSeconds > 0)
        require(routeFamily.isNotBlank() && routeFamily.length <= 80)
    }
}

data class DriveStatistics(
    val driveCount: Int,
    val totalDistanceMeters: Long,
    val totalDurationSeconds: Long,
    val averageDurationSeconds: Int?,
    val averageDistanceMeters: Int?,
    val commonRouteFamily: String?,
) {
    companion object {
        fun from(drives: List<CompletedDrive>): DriveStatistics {
            if (drives.isEmpty()) return DriveStatistics(0, 0L, 0L, null, null, null)
            val family = drives.groupingBy { it.routeFamily }.eachCount()
                .maxByOrNull { it.value }?.key
            return DriveStatistics(
                driveCount = drives.size,
                totalDistanceMeters = drives.sumOf { it.distanceMeters.toLong() },
                totalDurationSeconds = drives.sumOf { it.durationSeconds.toLong() },
                averageDurationSeconds = drives.map { it.durationSeconds }.average().roundToInt(),
                averageDistanceMeters = drives.map { it.distanceMeters }.average().roundToInt(),
                commonRouteFamily = family,
            )
        }
    }
}

/** One coordinate-free route sample, retained locally only when route learning is enabled. */
data class HistoricalRouteObservation(
    val routeKey: String,
    val startedAtEpochMillis: Long,
    val baselineDurationSeconds: Int,
    val observedDurationSeconds: Int,
) {
    init {
        require(routeKey.matches(Regex("[A-Za-z0-9._:-]{1,120}")))
        require(startedAtEpochMillis >= 0L)
        require(baselineDurationSeconds > 0)
        require(observedDurationSeconds > 0)
    }

    val delaySeconds: Int
        get() = (observedDurationSeconds - baselineDurationSeconds).coerceIn(0, 7_200)
}

data class HistoricalTrafficEstimate(
    val meanDelaySeconds: Int,
    val sampleCount: Int,
    val minDelaySeconds: Int,
    val maxDelaySeconds: Int,
) {
    init {
        require(meanDelaySeconds >= 0)
        require(sampleCount >= 3)
        require(minDelaySeconds >= 0 && maxDelaySeconds >= minDelaySeconds)
    }
}

/**
 * Groups the same trip by local weekday and departure hour (±1 hour).
 * Reports a plain arithmetic mean across all matching observations.
 */
object HistoricalTrafficAverager {
    const val MINIMUM_SAMPLES = 3
    const val SIMILAR_HOUR_WINDOW = 1

    fun estimateDelay(
        observations: List<HistoricalRouteObservation>,
        routeKey: String,
        targetDepartureEpochMillis: Long,
        zoneId: ZoneId,
    ): HistoricalTrafficEstimate? {
        require(routeKey.matches(Regex("[A-Za-z0-9._:-]{1,120}")))
        require(targetDepartureEpochMillis >= 0L)
        val target = Instant.ofEpochMilli(targetDepartureEpochMillis).atZone(zoneId)
        val values = observations.asSequence()
            .filter { it.routeKey == routeKey }
            .filter {
                val local = Instant.ofEpochMilli(it.startedAtEpochMillis).atZone(zoneId)
                local.dayOfWeek == target.dayOfWeek &&
                    minOf(abs(local.hour - target.hour), 24 - abs(local.hour - target.hour)) <= SIMILAR_HOUR_WINDOW
            }
            .map { it.delaySeconds }
            .toList()
        if (values.size < MINIMUM_SAMPLES) return null
        return HistoricalTrafficEstimate(
            meanDelaySeconds = values.average().roundToInt(),
            sampleCount = values.size,
            minDelaySeconds = values.minOrNull()!!,
            maxDelaySeconds = values.maxOrNull()!!,
        )
    }
}

enum class DepartureEstimateKind {
    LiveTraffic,
    HistoricalMean,
    BaselineOnly,
}

data class DepartureRecommendation(
    val recommendedDepartureEpochMillis: Long,
    val routeDurationSeconds: Int,
    val trafficDelaySeconds: Int?,
    val safetyBufferSeconds: Int,
    val trafficSource: TrafficDataProvider?,
    val estimateKind: DepartureEstimateKind,
    val historicalSampleCount: Int = 0,
) {
    init {
        require(recommendedDepartureEpochMillis >= 0L)
        require(routeDurationSeconds > 0)
        require(trafficDelaySeconds == null || trafficDelaySeconds >= 0)
        require(safetyBufferSeconds in 0..7_200)
        require(historicalSampleCount >= 0)
        require(estimateKind != DepartureEstimateKind.LiveTraffic ||
            (trafficDelaySeconds != null && trafficSource != null))
        require(estimateKind != DepartureEstimateKind.HistoricalMean ||
            (trafficDelaySeconds != null && trafficSource == null && historicalSampleCount >= 3))
        require(estimateKind != DepartureEstimateKind.BaselineOnly ||
            (trafficDelaySeconds == null && trafficSource == null && historicalSampleCount == 0))
    }
}

object DepartureRecommendationPolicy {
    /** Computes locally for a user-specified arrival time; no calendar access. Live data wins over history. */
    fun recommend(
        arrivalEpochMillis: Long,
        routeDurationSeconds: Int,
        safetyBufferSeconds: Int,
        trafficReport: TrafficCongestionReport?,
        nowEpochMillis: Long,
        historicalDelaySeconds: Int? = null,
        historicalSampleCount: Int = 0,
        freshnessPolicy: TrafficReportFreshnessPolicy = TrafficReportFreshnessPolicy(),
    ): DepartureRecommendation {
        require(arrivalEpochMillis > nowEpochMillis)
        require(routeDurationSeconds > 0)
        require(safetyBufferSeconds in 0..7_200)
        require(historicalDelaySeconds == null || historicalDelaySeconds >= 0)
        require(historicalSampleCount >= 0)
        val freshReport = trafficReport?.takeIf {
            it.estimatedDelaySeconds != null &&
                freshnessPolicy.evaluate(it, nowEpochMillis) is TrafficReportStatus.Current
        }
        val delay = freshReport?.estimatedDelaySeconds
            ?: historicalDelaySeconds?.takeIf { historicalSampleCount >= HistoricalTrafficAverager.MINIMUM_SAMPLES }
        val kind = when {
            freshReport != null -> DepartureEstimateKind.LiveTraffic
            delay != null -> DepartureEstimateKind.HistoricalMean
            else -> DepartureEstimateKind.BaselineOnly
        }
        val totalMillis = (routeDurationSeconds + (delay ?: 0) + safetyBufferSeconds) * 1_000L
        return DepartureRecommendation(
            recommendedDepartureEpochMillis = (arrivalEpochMillis - totalMillis).coerceAtLeast(nowEpochMillis),
            routeDurationSeconds = routeDurationSeconds,
            trafficDelaySeconds = delay,
            safetyBufferSeconds = safetyBufferSeconds,
            trafficSource = freshReport?.provider,
            estimateKind = kind,
            historicalSampleCount = if (kind == DepartureEstimateKind.HistoricalMean) historicalSampleCount else 0,
        )
    }

    fun localWeekdayHour(completedAtEpochMillis: Long, zoneId: ZoneId): Int {
        require(completedAtEpochMillis >= 0L)
        return Instant.ofEpochMilli(completedAtEpochMillis).atZone(zoneId).hour
    }
}
