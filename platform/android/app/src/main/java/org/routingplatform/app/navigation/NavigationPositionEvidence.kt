package org.routingplatform.app.navigation

enum class NavigationGnssQuality {
    Strong,
    Usable,
    Weak,
}

data class NavigationGnssEvidence(
    val visibleSatelliteCount: Int,
    val usedInFixSatelliteCount: Int,
    val meanCn0DbHz: Double?,
    val maxCn0DbHz: Double?,
) {
    init {
        require(
            visibleSatelliteCount >= 0
        ) {
            "visibleSatelliteCount must not be negative."
        }

        require(
            usedInFixSatelliteCount >= 0
        ) {
            "usedInFixSatelliteCount must not be negative."
        }

        require(
            usedInFixSatelliteCount <=
                visibleSatelliteCount
        ) {
            "usedInFixSatelliteCount must not exceed visible satellites."
        }

        require(
            meanCn0DbHz == null ||
                (
                    meanCn0DbHz.isFinite() &&
                        meanCn0DbHz >= 0.0
                )
        ) {
            "meanCn0DbHz must be non-negative and finite."
        }

        require(
            maxCn0DbHz == null ||
                (
                    maxCn0DbHz.isFinite() &&
                        maxCn0DbHz >= 0.0
                )
        ) {
            "maxCn0DbHz must be non-negative and finite."
        }

        require(
            meanCn0DbHz == null ||
                maxCn0DbHz == null ||
                meanCn0DbHz <=
                    maxCn0DbHz
        ) {
            "Mean C/N0 must not exceed maximum C/N0."
        }
    }

    val quality: NavigationGnssQuality
        get() =
            when {
                usedInFixSatelliteCount >=
                    STRONG_MIN_USED_SATELLITES &&
                    meanCn0DbHz != null &&
                    meanCn0DbHz >=
                        STRONG_MIN_MEAN_CN0_DB_HZ ->
                    NavigationGnssQuality.Strong

                usedInFixSatelliteCount >=
                    USABLE_MIN_USED_SATELLITES &&
                    meanCn0DbHz != null &&
                    meanCn0DbHz >=
                        USABLE_MIN_MEAN_CN0_DB_HZ ->
                    NavigationGnssQuality.Usable

                else ->
                    NavigationGnssQuality.Weak
            }
}

data class NavigationImuEvidence(
    val accelerationMagnitudeMps2: Double?,
    val yawRateDegreesPerSecond: Double?,
    val bearingDegrees: Double?,
) {
    init {
        require(
            accelerationMagnitudeMps2 == null ||
                (
                    accelerationMagnitudeMps2.isFinite() &&
                        accelerationMagnitudeMps2 >= 0.0
                )
        ) {
            "Acceleration magnitude must be non-negative and finite."
        }

        require(
            yawRateDegreesPerSecond == null ||
                yawRateDegreesPerSecond.isFinite()
        ) {
            "Yaw rate must be finite."
        }

        require(
            bearingDegrees == null ||
                (
                    bearingDegrees.isFinite() &&
                        bearingDegrees >= 0.0 &&
                        bearingDegrees < 360.0
                )
        ) {
            "Bearing must be in [0, 360)."
        }
    }
}

enum class NavigationMotionAgreement {
    Consistent,
    Unknown,
    Conflicting,
}

data class NavigationPositionObservation(
    val elapsedRealtimeNanos: Long,
    val estimate: NavigationPositionEstimate?,
    val gnssEvidence: NavigationGnssEvidence?,
    val imuEvidence: NavigationImuEvidence?,
    val motionAgreement: NavigationMotionAgreement,
) {
    init {
        require(
            elapsedRealtimeNanos >= 0L
        ) {
            "Observation timestamp must not be negative."
        }

        require(
            estimate == null ||
                estimate.elapsedRealtimeNanos ==
                    elapsedRealtimeNanos
        ) {
            "Estimate and observation timestamps must match."
        }
    }
}

private const val STRONG_MIN_USED_SATELLITES =
    8

private const val STRONG_MIN_MEAN_CN0_DB_HZ =
    28.0

private const val USABLE_MIN_USED_SATELLITES =
    4

private const val USABLE_MIN_MEAN_CN0_DB_HZ =
    18.0