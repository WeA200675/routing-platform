package org.routingplatform.app.navigation

enum class NavigationRawGnssQuality {
    Strong,
    Usable,
    Weak,
}

data class NavigationRawGnssClockEvidence(
    val biasNanos: Double?,
    val biasUncertaintyNanos: Double?,
) {
    init {
        require(
            biasNanos == null ||
                biasNanos.isFinite()
        )

        require(
            biasUncertaintyNanos == null ||
                (
                    biasUncertaintyNanos.isFinite() &&
                        biasUncertaintyNanos >= 0.0
                )
        )
    }
}

data class NavigationRawGnssMeasurement(
    val satellite:
        NavigationSatelliteKey,

    val cn0DbHz:
        Double,

    val pseudorangeRateMps:
        Double,

    val pseudorangeRateUncertaintyMps:
        Double?,

    val carrierFrequencyHz:
        Double?,

    val state:
        Int,

    val elapsedRealtimeNanos:
        Long,
) {
    init {
        require(
            cn0DbHz.isFinite() &&
                cn0DbHz >= 0.0
        )

        require(
            pseudorangeRateMps.isFinite()
        )

        require(
            pseudorangeRateUncertaintyMps == null ||
                (
                    pseudorangeRateUncertaintyMps.isFinite() &&
                        pseudorangeRateUncertaintyMps >= 0.0
                )
        )

        require(
            carrierFrequencyHz == null ||
                (
                    carrierFrequencyHz.isFinite() &&
                        carrierFrequencyHz > 0.0
                )
        )

        require(
            elapsedRealtimeNanos >= 0L
        )
    }
}

data class NavigationRawGnssSnapshot(
    val clock:
        NavigationRawGnssClockEvidence,

    val measurements:
        List<NavigationRawGnssMeasurement>,

    val elapsedRealtimeNanos:
        Long,
) {
    init {
        require(
            elapsedRealtimeNanos >= 0L
        )
    }

    val measurementCount: Int
        get() =
            measurements.size

    val constellationCount: Int
        get() =
            measurements
                .map {
                    it.satellite.constellation
                }
                .distinct()
                .size

    val meanCn0DbHz: Double?
        get() =
            measurements
                .map {
                    it.cn0DbHz
                }
                .takeIf {
                    it.isNotEmpty()
                }
                ?.average()

    val dualFrequencySatelliteCount: Int
        get() =
            measurements
                .filter {
                    it.carrierFrequencyHz !=
                        null
                }
                .groupBy {
                    it.satellite
                }
                .count {
                        entry ->

                    val frequencies =
                        entry.value
                            .mapNotNull {
                                it.carrierFrequencyHz
                            }
                            .distinctBy {
                                (
                                    it /
                                        FREQUENCY_BUCKET_HZ
                                ).toLong()
                            }

                    if (
                        frequencies.size <
                            2
                    ) {
                        false
                    } else {
                        val minimum =
                            frequencies.minOrNull()
                                ?: 0.0

                        val maximum =
                            frequencies.maxOrNull()
                                ?: 0.0

                        maximum -
                            minimum >=
                                MINIMUM_DUAL_FREQUENCY_SEPARATION_HZ
                    }
                }

    val quality: NavigationRawGnssQuality
        get() {
            val meanCn0 =
                meanCn0DbHz
                    ?: return NavigationRawGnssQuality.Weak

            return when {
                measurementCount >=
                    STRONG_MIN_MEASUREMENTS &&
                    meanCn0 >=
                        STRONG_MIN_MEAN_CN0_DB_HZ &&
                    constellationCount >=
                        STRONG_MIN_CONSTELLATIONS ->
                    NavigationRawGnssQuality.Strong

                measurementCount >=
                    USABLE_MIN_MEASUREMENTS &&
                    meanCn0 >=
                        USABLE_MIN_MEAN_CN0_DB_HZ ->
                    NavigationRawGnssQuality.Usable

                else ->
                    NavigationRawGnssQuality.Weak
            }
        }
}

private const val STRONG_MIN_MEASUREMENTS =
    10

private const val STRONG_MIN_MEAN_CN0_DB_HZ =
    28.0

private const val STRONG_MIN_CONSTELLATIONS =
    2

private const val USABLE_MIN_MEASUREMENTS =
    5

private const val USABLE_MIN_MEAN_CN0_DB_HZ =
    18.0

private const val FREQUENCY_BUCKET_HZ =
    100_000.0

private const val MINIMUM_DUAL_FREQUENCY_SEPARATION_HZ =
    5_000_000.0