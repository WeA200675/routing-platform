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

    /*
     * Android can report multiple carrier measurements for one satellite.
     * Count distinct, code-locked satellites in a recent snapshot so extra
     * bands cannot inflate the evidence strength.
     */
    private val usableMeasurements: List<NavigationRawGnssMeasurement>
        get() =
            measurements.filter {
                it.state and CODE_LOCK_STATE != 0 &&
                    it.elapsedRealtimeNanos <= elapsedRealtimeNanos &&
                    elapsedRealtimeNanos - it.elapsedRealtimeNanos <=
                        MAX_MEASUREMENT_AGE_NANOS
            }

    val usableSatelliteCount: Int
        get() =
            usableMeasurements
                .map { it.satellite }
                .distinct()
                .size

    val measurementCount: Int
        get() =
            usableMeasurements.size

    val constellationCount: Int
        get() =
            usableMeasurements
                .map { it.satellite.constellation }
                .filter { it != NavigationGnssConstellation.Unknown }
                .distinct()
                .size

    val meanCn0DbHz: Double?
        get() =
            usableMeasurements
                .groupBy { it.satellite }
                .values
                .map { satelliteMeasurements ->
                    satelliteMeasurements.maxOf { it.cn0DbHz }
                }
                .takeIf { it.isNotEmpty() }
                ?.average()

    val dualFrequencySatelliteCount: Int
        get() =
            measurements
                .filter {
                    it in usableMeasurements &&
                        it.carrierFrequencyHz != null
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
                usableSatelliteCount >=
                    STRONG_MIN_SATELLITES &&
                    meanCn0 >=
                        STRONG_MIN_MEAN_CN0_DB_HZ &&
                    constellationCount >=
                        STRONG_MIN_CONSTELLATIONS ->
                    NavigationRawGnssQuality.Strong

                usableSatelliteCount >=
                    USABLE_MIN_SATELLITES &&
                    meanCn0 >=
                        USABLE_MIN_MEAN_CN0_DB_HZ ->
                    NavigationRawGnssQuality.Usable

                else ->
                    NavigationRawGnssQuality.Weak
            }
        }
}

private const val STRONG_MIN_SATELLITES =
    8

private const val STRONG_MIN_MEAN_CN0_DB_HZ =
    28.0

private const val STRONG_MIN_CONSTELLATIONS =
    2

private const val USABLE_MIN_SATELLITES =
    4

private const val USABLE_MIN_MEAN_CN0_DB_HZ =
    18.0

/* GnssMeasurement.STATE_CODE_LOCK is Android's bit 0. */
private const val CODE_LOCK_STATE =
    1

private const val MAX_MEASUREMENT_AGE_NANOS =
    2_000_000_000L

private const val FREQUENCY_BUCKET_HZ =
    100_000.0

private const val MINIMUM_DUAL_FREQUENCY_SEPARATION_HZ =
    5_000_000.0