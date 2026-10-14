package org.routingplatform.app.navigation

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sqrt

data class VerifiedNavigationWifiRttAnchor(
    val sourceId: NavigationPrivacySafeRadioId,
    val position: RoutePoint,
    val uncertaintyM: Double,
    val confirmationCount: Int,
) {
    init {
        require(uncertaintyM.isFinite() && uncertaintyM in 0.1..5.0)
        require(confirmationCount >= REQUIRED_WIFI_RTT_ANCHOR_CONFIRMATIONS)
    }
}

data class NavigationWifiRttPositionEstimate(
    val position: RoutePoint,
    val horizontalAccuracyM: Double,
    val elapsedRealtimeNanos: Long,
    val usedAnchorCount: Int,
    val residualRmsM: Double,
)

class NavigationWifiRttAnchorVerifier(
    private val maximumGpsAccuracyM: Double = 4.0,
    private val maximumAnchorUncertaintyM: Double = 5.0,
    private val maximumRttStdDevM: Double = 2.0,
    private val maximumRangeM: Double = 100.0,
    private val maximumObservationAgeNanos: Long = 5_000_000_000L,
    private val minimumConfirmationSpacingNanos: Long = 750_000_000L,
    private val maximumHistoryAgeNanos: Long = 30_000_000_000L,
) {
    private data class Candidate(
        val position: RoutePoint,
        val uncertaintyM: Double,
        var confirmations: Int,
        var lastConfirmationNanos: Long?,
    )

    private val candidates = linkedMapOf<NavigationPrivacySafeRadioId, Candidate>()

    init {
        require(maximumGpsAccuracyM > 0.0)
        require(maximumAnchorUncertaintyM > 0.0)
        require(maximumRttStdDevM > 0.0)
        require(maximumRangeM > 0.0)
        require(maximumObservationAgeNanos > 0L)
        require(minimumConfirmationSpacingNanos > 0L)
        require(maximumHistoryAgeNanos > 0L)
    }

    fun observe(
        observations: List<NavigationRadioObservation>,
        gpsPosition: RoutePoint,
        gpsAccuracyM: Double?,
        gpsElapsedRealtimeNanos: Long,
    ): List<VerifiedNavigationWifiRttAnchor> {
        candidates.entries.removeAll { (_, candidate) ->
            candidate.lastConfirmationNanos?.let {
                gpsElapsedRealtimeNanos - it > maximumHistoryAgeNanos
            } ?: false
        }

        if (
            gpsAccuracyM == null ||
            !gpsAccuracyM.isFinite() ||
            gpsAccuracyM !in 0.1..maximumGpsAccuracyM
        ) {
            return verifiedAnchors()
        }

        observations.forEach { observation ->
            if (observation.technology != NavigationRadioTechnology.WifiRtt) return@forEach

            val anchor = observation.anchorPosition ?: return@forEach
            val anchorUncertainty = observation.anchorPositionUncertaintyM ?: return@forEach
            val measuredRange = observation.distanceM ?: return@forEach
            val rangeUncertainty = observation.distanceStdDevM ?: return@forEach
            val ageNanos = gpsElapsedRealtimeNanos - observation.elapsedRealtimeNanos

            if (
                ageNanos < 0L ||
                ageNanos > maximumObservationAgeNanos ||
                !anchorUncertainty.isFinite() ||
                anchorUncertainty !in 0.1..maximumAnchorUncertaintyM ||
                !measuredRange.isFinite() ||
                measuredRange !in 0.0..maximumRangeM ||
                !rangeUncertainty.isFinite() ||
                rangeUncertainty !in 0.0..maximumRttStdDevM
            ) {
                return@forEach
            }

            val geometricRange = distanceMeters(gpsPosition, anchor)
            val toleranceM =
                max(
                    MINIMUM_GNSS_RTT_CROSSCHECK_TOLERANCE_M,
                    gpsAccuracyM + anchorUncertainty + rangeUncertainty * 2.0,
                )
            val previous = candidates[observation.sourceId]
            val sameAnchor =
                previous != null &&
                    distanceMeters(previous.position, anchor) <=
                        MAXIMUM_ANCHOR_COORDINATE_DRIFT_M &&
                    abs(previous.uncertaintyM - anchorUncertainty) <=
                        MAXIMUM_ANCHOR_UNCERTAINTY_DRIFT_M

            if (abs(geometricRange - measuredRange) > toleranceM) {
                if (previous != null) {
                    previous.confirmations = 0
                    previous.lastConfirmationNanos = null
                }
                return@forEach
            }

            val candidate =
                if (sameAnchor) {
                    checkNotNull(previous)
                } else {
                    Candidate(
                        position = anchor,
                        uncertaintyM = anchorUncertainty,
                        confirmations = 0,
                        lastConfirmationNanos = null,
                    ).also { candidates[observation.sourceId] = it }
                }

            val previousConfirmation = candidate.lastConfirmationNanos
            if (
                previousConfirmation != null &&
                (
                    observation.elapsedRealtimeNanos <= previousConfirmation ||
                    observation.elapsedRealtimeNanos - previousConfirmation <
                        minimumConfirmationSpacingNanos
                )
            ) {
                return@forEach
            }

            candidate.confirmations += 1
            candidate.lastConfirmationNanos = observation.elapsedRealtimeNanos
        }

        return verifiedAnchors()
    }

    fun verifiedAnchors(
        nowElapsedRealtimeNanos: Long? = null,
    ): List<VerifiedNavigationWifiRttAnchor> {
        if (nowElapsedRealtimeNanos != null) {
            candidates.entries.removeAll { (_, candidate) ->
                candidate.lastConfirmationNanos?.let {
                    nowElapsedRealtimeNanos - it > maximumHistoryAgeNanos
                } ?: false
            }
        }

        return candidates.mapNotNull { (sourceId, candidate) ->
            if (candidate.confirmations < REQUIRED_WIFI_RTT_ANCHOR_CONFIRMATIONS) {
                null
            } else {
                VerifiedNavigationWifiRttAnchor(
                    sourceId = sourceId,
                    position = candidate.position,
                    uncertaintyM = candidate.uncertaintyM,
                    confirmationCount = candidate.confirmations,
                )
            }
        }
    }

    fun reset() {
        candidates.clear()
    }
}

class NavigationWifiRttPositionEstimator(
    private val maximumObservationAgeNanos: Long = 3_000_000_000L,
    private val maximumResidualRmsM: Double = 6.0,
    private val maximumHorizontalAccuracyM: Double = 25.0,
    private val maximumGeometryCondition: Double = 50.0,
) {
    fun estimate(
        observations: List<NavigationRadioObservation>,
        verifiedAnchors: List<VerifiedNavigationWifiRttAnchor>,
        nowElapsedRealtimeNanos: Long,
    ): NavigationWifiRttPositionEstimate? {
        val verifiedById = verifiedAnchors.associateBy { it.sourceId }
        val ranges =
            observations
                .asSequence()
                .filter { it.technology == NavigationRadioTechnology.WifiRtt }
                .filter { observation ->
                    val age = nowElapsedRealtimeNanos - observation.elapsedRealtimeNanos
                    age in 0L..maximumObservationAgeNanos
                }
                .mapNotNull { observation ->
                    val anchor = verifiedById[observation.sourceId] ?: return@mapNotNull null
                    val range = observation.distanceM ?: return@mapNotNull null
                    val rangeStdDev = observation.distanceStdDevM ?: return@mapNotNull null
                    val reportedPosition = observation.anchorPosition ?: return@mapNotNull null
                    if (
                        !range.isFinite() ||
                        range !in 0.0..100.0 ||
                        !rangeStdDev.isFinite() ||
                        rangeStdDev !in 0.0..2.0 ||
                        distanceMeters(anchor.position, reportedPosition) >
                            MAXIMUM_ANCHOR_COORDINATE_DRIFT_M
                    ) {
                        return@mapNotNull null
                    }
                    RttRange(anchor, range, rangeStdDev, observation.elapsedRealtimeNanos)
                }
                .groupBy { it.anchor.sourceId }
                .values
                .mapNotNull { it.maxByOrNull { range -> range.elapsedRealtimeNanos } }
                .toList()

        if (ranges.size < MINIMUM_WIFI_RTT_POSITION_ANCHORS) return null

        val origin = ranges.first().anchor.position
        val localRanges =
            ranges.map { range ->
                val (east, north) = toLocalMeters(origin, range.anchor.position)
                LocalRange(
                    eastM = east,
                    northM = north,
                    distanceM = range.distanceM,
                    stdDevM = sqrt(
                        range.stdDevM * range.stdDevM +
                            range.anchor.uncertaintyM * range.anchor.uncertaintyM,
                    ).coerceAtLeast(MINIMUM_RANGE_SIGMA_M),
                )
            }

        val first = localRanges.first()
        var normalXX = 0.0
        var normalXY = 0.0
        var normalYY = 0.0
        var rhsX = 0.0
        var rhsY = 0.0

        localRanges.drop(1).forEach { range ->
            val a = 2.0 * (range.eastM - first.eastM)
            val b = 2.0 * (range.northM - first.northM)
            val c =
                range.eastM * range.eastM +
                    range.northM * range.northM -
                    first.eastM * first.eastM -
                    first.northM * first.northM +
                    first.distanceM * first.distanceM -
                    range.distanceM * range.distanceM
            val variance =
                max(
                    MINIMUM_RANGE_VARIANCE_M2,
                    range.stdDevM * range.stdDevM +
                        first.stdDevM * first.stdDevM,
                )
            val weight = 1.0 / variance
            normalXX += weight * a * a
            normalXY += weight * a * b
            normalYY += weight * b * b
            rhsX += weight * a * c
            rhsY += weight * b * c
        }

        val determinant = normalXX * normalYY - normalXY * normalXY
        val trace = normalXX + normalYY
        if (!determinant.isFinite() || determinant <= MINIMUM_NORMAL_DETERMINANT) return null

        val discriminant =
            sqrt(
                ((normalXX - normalYY) * (normalXX - normalYY) +
                    4.0 * normalXY * normalXY).coerceAtLeast(0.0),
            )
        val minimumEigenvalue = (trace - discriminant) / 2.0
        val maximumEigenvalue = (trace + discriminant) / 2.0
        if (
            minimumEigenvalue <= 0.0 ||
            maximumEigenvalue / minimumEigenvalue > maximumGeometryCondition
        ) {
            return null
        }

        val east =
            (rhsX * normalYY - rhsY * normalXY) / determinant
        val north =
            (rhsY * normalXX - rhsX * normalXY) / determinant
        if (!east.isFinite() || !north.isFinite()) return null

        val position = fromLocalMeters(origin, east, north)
        val residuals =
            localRanges.map { range ->
                abs(
                    distanceMeters(
                        position,
                        fromLocalMeters(origin, range.eastM, range.northM),
                    ) - range.distanceM,
                )
            }
        val residualRms =
            sqrt(residuals.sumOf { it * it } / residuals.size)
        if (!residualRms.isFinite() || residualRms > maximumResidualRmsM) return null

        val informationXX = localRanges.sumOf { range ->
            val dx = east - range.eastM
            val dy = north - range.northM
            val distance = sqrt(dx * dx + dy * dy).coerceAtLeast(0.1)
            (dx / distance) * (dx / distance) /
                (range.stdDevM * range.stdDevM)
        }
        val informationXY = localRanges.sumOf { range ->
            val dx = east - range.eastM
            val dy = north - range.northM
            val distance = sqrt(dx * dx + dy * dy).coerceAtLeast(0.1)
            (dx / distance) * (dy / distance) /
                (range.stdDevM * range.stdDevM)
        }
        val informationYY = localRanges.sumOf { range ->
            val dx = east - range.eastM
            val dy = north - range.northM
            val distance = sqrt(dx * dx + dy * dy).coerceAtLeast(0.1)
            (dy / distance) * (dy / distance) /
                (range.stdDevM * range.stdDevM)
        }
        val informationDeterminant =
            informationXX * informationYY - informationXY * informationXY
        if (informationDeterminant <= MINIMUM_NORMAL_DETERMINANT) return null

        val largestCovarianceEigenvalue =
            (
                informationXX + informationYY +
                    sqrt(
                        ((informationXX - informationYY) *
                            (informationXX - informationYY) +
                            4.0 * informationXY * informationXY).coerceAtLeast(0.0),
                    )
                ) / (2.0 * informationDeterminant)
        val uncertaintyFloor =
            ranges.maxOf { it.anchor.uncertaintyM }
        val horizontalAccuracy =
            max(
                uncertaintyFloor,
                sqrt(largestCovarianceEigenvalue) + residualRms,
            )
        if (
            !horizontalAccuracy.isFinite() ||
            horizontalAccuracy > maximumHorizontalAccuracyM
        ) {
            return null
        }

        return NavigationWifiRttPositionEstimate(
            position = position,
            horizontalAccuracyM = horizontalAccuracy,
            elapsedRealtimeNanos = ranges.maxOf(RttRange::elapsedRealtimeNanos),
            usedAnchorCount = ranges.size,
            residualRmsM = residualRms,
        )
    }

    private data class RttRange(
        val anchor: VerifiedNavigationWifiRttAnchor,
        val distanceM: Double,
        val stdDevM: Double,
        val elapsedRealtimeNanos: Long,
    )

    private data class LocalRange(
        val eastM: Double,
        val northM: Double,
        val distanceM: Double,
        val stdDevM: Double,
    )
}

private fun toLocalMeters(origin: RoutePoint, point: RoutePoint): Pair<Double, Double> {
    val meanLatitude = (origin.latitude + point.latitude) / 2.0 * DEGREES_TO_RADIANS
    val east =
        (point.longitude - origin.longitude) *
            DEGREES_TO_RADIANS *
            EARTH_RADIUS_M *
            cos(meanLatitude)
    val north =
        (point.latitude - origin.latitude) *
            DEGREES_TO_RADIANS *
            EARTH_RADIUS_M
    return east to north
}

private fun fromLocalMeters(origin: RoutePoint, eastM: Double, northM: Double): RoutePoint =
    RoutePoint(
        latitude =
            origin.latitude +
                northM / EARTH_RADIUS_M / DEGREES_TO_RADIANS,
        longitude =
            origin.longitude +
                eastM /
                (EARTH_RADIUS_M * cos(origin.latitude * DEGREES_TO_RADIANS)) /
                DEGREES_TO_RADIANS,
    )

private fun distanceMeters(first: RoutePoint, second: RoutePoint): Double {
    val (east, north) = toLocalMeters(first, second)
    return sqrt(east * east + north * north)
}

private const val REQUIRED_WIFI_RTT_ANCHOR_CONFIRMATIONS = 4
private const val MINIMUM_WIFI_RTT_POSITION_ANCHORS = 3
private const val MINIMUM_GNSS_RTT_CROSSCHECK_TOLERANCE_M = 3.0
private const val MAXIMUM_ANCHOR_COORDINATE_DRIFT_M = 1.0
private const val MAXIMUM_ANCHOR_UNCERTAINTY_DRIFT_M = 0.5
private const val MINIMUM_RANGE_SIGMA_M = 0.5
private const val MINIMUM_RANGE_VARIANCE_M2 = 0.25
private const val MINIMUM_NORMAL_DETERMINANT = 1.0e-9
private const val EARTH_RADIUS_M = 6_371_008.8
private const val DEGREES_TO_RADIANS = Math.PI / 180.0
