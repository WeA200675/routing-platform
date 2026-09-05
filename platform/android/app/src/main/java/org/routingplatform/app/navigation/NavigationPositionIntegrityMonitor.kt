package org.routingplatform.app.navigation

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sqrt

enum class PositionIntegrityIssue {
    StaleTimestamp,
    PoorHorizontalAccuracy,
    ImplausibleMotion,
}

data class NavigationPositionIntegrityResult(
    val accepted: Boolean,
    val estimate: NavigationPositionEstimate?,
    val issues: Set<PositionIntegrityIssue>,
    val impliedSpeedMps: Double?,
) {
    init {
        require(
            accepted ==
                (estimate != null)
        ) {
            "Accepted integrity results must contain an estimate."
        }

        require(
            impliedSpeedMps == null ||
                (
                    impliedSpeedMps.isFinite() &&
                        impliedSpeedMps >= 0.0
                )
        )
    }
}

class NavigationPositionIntegrityMonitor(
    private val mediumConfidenceAccuracyM:
        Double =
        DEFAULT_MEDIUM_CONFIDENCE_ACCURACY_M,

    private val maximumAcceptedAccuracyM:
        Double =
        DEFAULT_MAXIMUM_ACCEPTED_ACCURACY_M,

    private val unknownAccuracySigmaM:
        Double =
        DEFAULT_UNKNOWN_ACCURACY_SIGMA_M,

    private val maximumImpliedSpeedMps:
        Double =
        DEFAULT_MAXIMUM_IMPLIED_SPEED_MPS,
) {
    init {
        require(
            mediumConfidenceAccuracyM.isFinite() &&
                mediumConfidenceAccuracyM > 0.0
        )

        require(
            maximumAcceptedAccuracyM.isFinite() &&
                maximumAcceptedAccuracyM >
                    mediumConfidenceAccuracyM
        )

        require(
            unknownAccuracySigmaM.isFinite() &&
                unknownAccuracySigmaM > 0.0
        )

        require(
            maximumImpliedSpeedMps.isFinite() &&
                maximumImpliedSpeedMps > 0.0
        )
    }

    private var previousAcceptedSample:
        NavigationLocationSample? =
        null

    fun inspect(
        sample: NavigationLocationSample,
    ): NavigationPositionIntegrityResult {

        val previous =
            previousAcceptedSample

        if (
            previous != null &&
            sample.elapsedRealtimeNanos <=
                previous.elapsedRealtimeNanos
        ) {
            return rejected(
                PositionIntegrityIssue
                    .StaleTimestamp
            )
        }

        val accuracyM =
            sample.horizontalAccuracyM

        if (
            accuracyM != null &&
            accuracyM >
                maximumAcceptedAccuracyM
        ) {
            return rejected(
                PositionIntegrityIssue
                    .PoorHorizontalAccuracy
            )
        }

        val impliedSpeedMps =
            if (previous != null) {
                val elapsedSeconds =
                    (
                        sample.elapsedRealtimeNanos -
                            previous.elapsedRealtimeNanos
                    ).toDouble() /
                        NANOS_PER_SECOND

                val distanceM =
                    distanceMeters(
                        previous.position,
                        sample.position,
                    )

                if (elapsedSeconds > 0.0) {
                    distanceM /
                        elapsedSeconds
                } else {
                    null
                }
            } else {
                null
            }

        if (
            impliedSpeedMps != null &&
            impliedSpeedMps >
                maximumImpliedSpeedMps
        ) {
            return NavigationPositionIntegrityResult(
                accepted =
                    false,

                estimate =
                    null,

                issues =
                    setOf(
                        PositionIntegrityIssue
                            .ImplausibleMotion
                    ),

                impliedSpeedMps =
                    impliedSpeedMps,
            )
        }

        val sigmaM =
            when (accuracyM) {
                null ->
                    unknownAccuracySigmaM

                else ->
                    accuracyM.coerceAtLeast(
                        MINIMUM_SIGMA_M
                    )
            }

        val confidence =
            when {
                accuracyM == null ->
                    NavigationPositionConfidence.Low

                accuracyM <=
                    mediumConfidenceAccuracyM ->
                    NavigationPositionConfidence.Medium

                else ->
                    NavigationPositionConfidence.Low
            }

        val estimate =
            NavigationPositionEstimate(
                position =
                    sample.position,

                horizontalVelocityMps =
                    impliedSpeedMps,

                bearingDegrees =
                    null,

                covariance =
                    PositionCovariance2D(
                        eastVarianceM2 =
                            sigmaM *
                                sigmaM,

                        northVarianceM2 =
                            sigmaM *
                                sigmaM,
                    ),

                confidence =
                    confidence,

                elapsedRealtimeNanos =
                    sample.elapsedRealtimeNanos,
            )

        previousAcceptedSample =
            sample

        return NavigationPositionIntegrityResult(
            accepted =
                true,

            estimate =
                estimate,

            issues =
                emptySet(),

            impliedSpeedMps =
                impliedSpeedMps,
        )
    }

    fun reset() {
        previousAcceptedSample =
            null
    }

    private fun rejected(
        issue: PositionIntegrityIssue,
    ) =
        NavigationPositionIntegrityResult(
            accepted =
                false,

            estimate =
                null,

            issues =
                setOf(
                    issue
                ),

            impliedSpeedMps =
                null,
        )
}

private fun distanceMeters(
    first: RoutePoint,
    second: RoutePoint,
): Double {

    val meanLatitudeRadians =
        (
            (
                first.latitude +
                    second.latitude
            ) /
                2.0
        ) *
            DEGREES_TO_RADIANS

    val x =
        (
            second.longitude -
                first.longitude
        ) *
            DEGREES_TO_RADIANS *
            EARTH_RADIUS_M *
            cos(
                meanLatitudeRadians
            )

    val y =
        (
            second.latitude -
                first.latitude
        ) *
            DEGREES_TO_RADIANS *
            EARTH_RADIUS_M

    return sqrt(
        x *
            x +
            y *
            y
    )
}

private const val DEFAULT_MEDIUM_CONFIDENCE_ACCURACY_M =
    12.0

private const val DEFAULT_MAXIMUM_ACCEPTED_ACCURACY_M =
    75.0

private const val DEFAULT_UNKNOWN_ACCURACY_SIGMA_M =
    50.0

/*
 * Intentionally generous at this raw-integrity layer.
 * Route context, vehicle dynamics and fusion add tighter
 * plausibility constraints later.
 */
private const val DEFAULT_MAXIMUM_IMPLIED_SPEED_MPS =
    100.0

private const val MINIMUM_SIGMA_M =
    2.0

private const val EARTH_RADIUS_M =
    6371008.8

private const val DEGREES_TO_RADIANS =
    PI / 180.0

private const val NANOS_PER_SECOND =
    1_000_000_000.0