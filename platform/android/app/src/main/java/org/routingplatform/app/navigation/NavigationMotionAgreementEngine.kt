package org.routingplatform.app.navigation

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class NavigationMotionAgreementResult(
    val agreement: NavigationMotionAgreement,
    val movementBearingDegrees: Double?,
    val correctedDeviceBearingDegrees: Double?,
    val headingDeltaDegrees: Double?,
    val calibrated: Boolean,
)

class NavigationMotionAgreementEngine(
    private val calibrationSamplesRequired: Int = 5,
    private val calibrationToleranceDegrees: Double = 20.0,
    private val consistentHeadingDeltaDegrees: Double = 25.0,
    private val conflictingHeadingDeltaDegrees: Double = 65.0,
    private val minimumMovementDistanceM: Double = 3.0,
    private val minimumSpeedMps: Double = 2.0,
    private val maximumBearingAgeNanos: Long =
        750_000_000L,
    private val maximumGyroscopeAgeNanos: Long =
        500_000_000L,
    private val maximumRotationRateRadPerSecond: Double =
        0.6,
) {
    init {
        require(
            calibrationSamplesRequired >= 3
        ) {
            "At least three calibration samples are required."
        }

        require(
            calibrationToleranceDegrees.isFinite() &&
                calibrationToleranceDegrees > 0.0 &&
                calibrationToleranceDegrees < 90.0
        )

        require(
            consistentHeadingDeltaDegrees.isFinite() &&
                consistentHeadingDeltaDegrees > 0.0
        )

        require(
            conflictingHeadingDeltaDegrees.isFinite() &&
                conflictingHeadingDeltaDegrees >
                    consistentHeadingDeltaDegrees &&
                conflictingHeadingDeltaDegrees <= 180.0
        )

        require(
            minimumMovementDistanceM.isFinite() &&
                minimumMovementDistanceM > 0.0
        )

        require(
            minimumSpeedMps.isFinite() &&
                minimumSpeedMps >= 0.0
        )

        require(
            maximumBearingAgeNanos > 0L
        )

        require(
            maximumGyroscopeAgeNanos > 0L
        )

        require(
            maximumRotationRateRadPerSecond.isFinite() &&
                maximumRotationRateRadPerSecond > 0.0
        )
    }

    private val calibrationOffsets =
        mutableListOf<Double>()

    private var deviceToTravelOffsetDegrees:
        Double? =
        null

    val calibrated: Boolean
        get() =
            deviceToTravelOffsetDegrees !=
                null

    fun evaluate(
        previousEstimate: NavigationPositionEstimate,
        currentEstimate: NavigationPositionEstimate,
        sensors: NavigationSensorEvidenceSnapshot,
    ): NavigationMotionAgreementResult {

        if (
            currentEstimate.elapsedRealtimeNanos <=
                previousEstimate.elapsedRealtimeNanos
        ) {
            return unknown()
        }

        val elapsedSeconds =
            (
                currentEstimate.elapsedRealtimeNanos -
                    previousEstimate.elapsedRealtimeNanos
            ).toDouble() /
                NANOS_PER_SECOND

        if (
            !elapsedSeconds.isFinite() ||
            elapsedSeconds <= 0.0
        ) {
            return unknown()
        }

        val motion =
            calculateMotion(
                from =
                    previousEstimate.position,

                to =
                    currentEstimate.position,
            )

        if (
            motion.distanceM <
                minimumMovementDistanceM
        ) {
            return unknown(
                movementBearingDegrees =
                    motion.bearingDegrees
            )
        }

        val impliedSpeedMps =
            motion.distanceM /
                elapsedSeconds

        if (
            impliedSpeedMps <
                minimumSpeedMps
        ) {
            return unknown(
                movementBearingDegrees =
                    motion.bearingDegrees
            )
        }

        val bearingSample =
            sensors.bearing
                ?: return unknown(
                    movementBearingDegrees =
                        motion.bearingDegrees
                )

        val bearingAgeNanos =
            currentEstimate.elapsedRealtimeNanos -
                bearingSample.elapsedRealtimeNanos

        if (
            bearingAgeNanos < 0L ||
            bearingAgeNanos >
                maximumBearingAgeNanos
        ) {
            return unknown(
                movementBearingDegrees =
                    motion.bearingDegrees
            )
        }

        if (
            isRapidRotation(
                currentEstimate =
                    currentEstimate,

                gyroscope =
                    sensors.gyroscope,
            )
        ) {
            return unknown(
                movementBearingDegrees =
                    motion.bearingDegrees
            )
        }

        val rawOffsetDegrees =
            normalizeSignedDegrees(
                motion.bearingDegrees -
                    bearingSample.bearingDegrees
            )

        if (
            deviceToTravelOffsetDegrees ==
                null
        ) {
            observeCalibrationOffset(
                rawOffsetDegrees
            )
        }

        val offset =
            deviceToTravelOffsetDegrees
                ?: return unknown(
                    movementBearingDegrees =
                        motion.bearingDegrees
                )

        val correctedBearing =
            normalizeDegrees(
                bearingSample.bearingDegrees +
                    offset
            )

        val headingDelta =
            angularDistanceDegrees(
                motion.bearingDegrees,
                correctedBearing,
            )

        val agreement =
            when {
                headingDelta <=
                    consistentHeadingDeltaDegrees ->
                    NavigationMotionAgreement.Consistent

                headingDelta >=
                    conflictingHeadingDeltaDegrees ->
                    NavigationMotionAgreement.Conflicting

                else ->
                    NavigationMotionAgreement.Unknown
            }

        return NavigationMotionAgreementResult(
            agreement =
                agreement,

            movementBearingDegrees =
                motion.bearingDegrees,

            correctedDeviceBearingDegrees =
                correctedBearing,

            headingDeltaDegrees =
                headingDelta,

            calibrated =
                true,
        )
    }

    fun reset() {
        calibrationOffsets.clear()

        deviceToTravelOffsetDegrees =
            null
    }

    private fun observeCalibrationOffset(
        offsetDegrees: Double,
    ) {
        if (
            calibrationOffsets.isEmpty()
        ) {
            calibrationOffsets.add(
                offsetDegrees
            )

            return
        }

        val currentMean =
            circularMeanDegrees(
                calibrationOffsets
            )

        val difference =
            angularDistanceDegrees(
                offsetDegrees,
                currentMean,
            )

        if (
            difference >
                calibrationToleranceDegrees
        ) {
            calibrationOffsets.clear()

            calibrationOffsets.add(
                offsetDegrees
            )

            return
        }

        calibrationOffsets.add(
            offsetDegrees
        )

        if (
            calibrationOffsets.size >=
                calibrationSamplesRequired
        ) {
            deviceToTravelOffsetDegrees =
                circularMeanDegrees(
                    calibrationOffsets
                )
        }
    }

    private fun isRapidRotation(
        currentEstimate: NavigationPositionEstimate,
        gyroscope: NavigationVector3Sample?,
    ): Boolean {

        if (gyroscope == null) {
            return false
        }

        val ageNanos =
            currentEstimate.elapsedRealtimeNanos -
                gyroscope.elapsedRealtimeNanos

        if (
            ageNanos < 0L ||
            ageNanos >
                maximumGyroscopeAgeNanos
        ) {
            return false
        }

        val magnitude =
            sqrt(
                gyroscope.x *
                    gyroscope.x +
                    gyroscope.y *
                        gyroscope.y +
                    gyroscope.z *
                        gyroscope.z
            )

        return magnitude >
            maximumRotationRateRadPerSecond
    }

    private fun unknown(
        movementBearingDegrees: Double? = null,
    ) =
        NavigationMotionAgreementResult(
            agreement =
                NavigationMotionAgreement.Unknown,

            movementBearingDegrees =
                movementBearingDegrees,

            correctedDeviceBearingDegrees =
                null,

            headingDeltaDegrees =
                null,

            calibrated =
                calibrated,
        )
}

private data class MotionVector(
    val distanceM: Double,
    val bearingDegrees: Double,
)

private fun calculateMotion(
    from: RoutePoint,
    to: RoutePoint,
): MotionVector {

    val meanLatitudeRadians =
        (
            (
                from.latitude +
                    to.latitude
            ) /
                2.0
        ) *
            DEGREES_TO_RADIANS

    val eastM =
        (
            to.longitude -
                from.longitude
        ) *
            DEGREES_TO_RADIANS *
            EARTH_RADIUS_M *
            cos(
                meanLatitudeRadians
            )

    val northM =
        (
            to.latitude -
                from.latitude
        ) *
            DEGREES_TO_RADIANS *
            EARTH_RADIUS_M

    val distanceM =
        sqrt(
            eastM *
                eastM +
                northM *
                    northM
        )

    val bearing =
        if (
            distanceM <=
                DISTANCE_EPSILON_M
        ) {
            0.0
        } else {
            normalizeDegrees(
                atan2(
                    eastM,
                    northM,
                ) *
                    RADIANS_TO_DEGREES
            )
        }

    return MotionVector(
        distanceM =
            distanceM,

        bearingDegrees =
            bearing,
    )
}

private fun circularMeanDegrees(
    values: List<Double>,
): Double {

    require(
        values.isNotEmpty()
    )

    var x =
        0.0

    var y =
        0.0

    values.forEach {
            degrees ->

        val radians =
            degrees *
                DEGREES_TO_RADIANS

        x +=
            cos(
                radians
            )

        y +=
            sin(
                radians
            )
    }

    return normalizeSignedDegrees(
        atan2(
            y,
            x,
        ) *
            RADIANS_TO_DEGREES
    )
}

private fun angularDistanceDegrees(
    first: Double,
    second: Double,
): Double =
    abs(
        normalizeSignedDegrees(
            first -
                second
        )
    )

private fun normalizeDegrees(
    degrees: Double,
): Double =
    (
        (
            degrees %
                360.0
        ) +
            360.0
    ) %
        360.0

private fun normalizeSignedDegrees(
    degrees: Double,
): Double {

    val normalized =
        normalizeDegrees(
            degrees
        )

    return if (
        normalized >=
            180.0
    ) {
        normalized -
            360.0
    } else {
        normalized
    }
}

private const val EARTH_RADIUS_M =
    6371008.8

private const val DEGREES_TO_RADIANS =
    PI / 180.0

private const val RADIANS_TO_DEGREES =
    180.0 / PI

private const val NANOS_PER_SECOND =
    1_000_000_000.0

private const val DISTANCE_EPSILON_M =
    1.0e-6