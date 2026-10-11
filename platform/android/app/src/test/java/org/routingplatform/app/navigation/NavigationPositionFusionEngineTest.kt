package org.routingplatform.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationPositionFusionEngineTest {

    @Test
    fun rawGnssStrongQualityRequiresUsefulMeasurementSet() {
        val snapshot =
            strongRawGnss(
                timestamp =
                    1_000L
            )

        assertEquals(
            NavigationRawGnssQuality.Strong,
            snapshot.quality,
        )

        assertTrue(
            snapshot.measurementCount >=
                10
        )

        assertTrue(
            snapshot.constellationCount >=
                2
        )
    }

    @Test
    fun dualFrequencySatelliteIsDetected() {
        val key =
            NavigationSatelliteKey(
                constellation =
                    NavigationGnssConstellation.Galileo,

                svid =
                    12,
            )

        val snapshot =
            NavigationRawGnssSnapshot(
                clock =
                    NavigationRawGnssClockEvidence(
                        biasNanos =
                            null,

                        biasUncertaintyNanos =
                            null,
                    ),

                measurements =
                    listOf(
                        rawMeasurement(
                            key =
                                key,

                            frequencyHz =
                                1_575_420_000.0,

                            timestamp =
                                1L,
                        ),
                        rawMeasurement(
                            key =
                                key,

                            frequencyHz =
                                1_176_450_000.0,

                            timestamp =
                                1L,
                        ),
                    ),

                elapsedRealtimeNanos =
                    1L,
            )

        assertEquals(
            1,
            snapshot.dualFrequencySatelliteCount,
        )
    }

    @Test
    fun deadReckoningMovesEastUsingTrustedTravelBearing() {
        val engine =
            NavigationDeadReckoningEngine()

        val start =
            NavigationPositionEstimate(
                position =
                    RoutePoint(
                        latitude =
                            0.0,

                        longitude =
                            0.0,
                    ),

                horizontalVelocityMps =
                    11.1,

                bearingDegrees =
                    90.0,

                covariance =
                    PositionCovariance2D(
                        eastVarianceM2 =
                            25.0,

                        northVarianceM2 =
                            25.0,
                    ),

                confidence =
                    NavigationPositionConfidence.High,

                elapsedRealtimeNanos =
                    1_000_000_000L,
            )

        val predicted =
            engine.predict(
                from =
                    start,

                targetElapsedRealtimeNanos =
                    2_000_000_000L,

                travelBearingDegrees =
                    90.0,
            )

        assertNotNull(
            predicted
        )

        val result =
            checkNotNull(
                predicted
            )

        assertEquals(
            0.0,
            result.position.latitude,
            0.00001,
        )

        assertTrue(
            result.position.longitude in
                0.00009..0.00011
        )

        assertEquals(
            NavigationPositionConfidence.Low,
            result.confidence,
        )

        assertTrue(
            result
                .covariance
                .conservativeHorizontalSigmaM >
                5.0
        )
    }

    @Test
    fun deadReckoningRefusesPredictionBeyondTimeLimit() {
        val engine =
            NavigationDeadReckoningEngine(
                NavigationDeadReckoningPolicy(
                    maximumPredictionSeconds =
                        5.0
                )
            )

        val start =
            estimate(
                longitude =
                    0.0,

                timestamp =
                    1_000_000_000L,

                velocityMps =
                    10.0,
            )

        val predicted =
            engine.predict(
                from =
                    start,

                targetElapsedRealtimeNanos =
                    7_000_000_000L,

                travelBearingDegrees =
                    90.0,
            )

        assertNull(
            predicted
        )
    }

    @Test
    fun repeatedStrongEvidenceEventuallyReachesHighConfidence() {
        val engine =
            NavigationPositionFusionEngine()

        var finalUpdate:
            NavigationPositionFusionUpdate? =
            null

        repeat(
            12
        ) {
                index ->

            val timestamp =
                (
                    index.toLong() +
                        1L
                ) *
                    1_000_000_000L

            finalUpdate =
                engine.ingest(
                    sample =
                        NavigationLocationSample(
                            position =
                                RoutePoint(
                                    latitude =
                                        0.0,

                                    longitude =
                                        index *
                                            0.0001,
                                ),

                            horizontalAccuracyM =
                                4.0,

                            elapsedRealtimeNanos =
                                timestamp,

                            provider =
                                "gps",
                        ),

                    sensors =
                        sensorSnapshot(
                            timestamp =
                                timestamp,

                            bearingDegrees =
                                90.0,
                        ),

                    environment =
                        strongEnvironment(
                            timestamp =
                                timestamp
                        ),

                    rawGnss =
                        strongRawGnss(
                            timestamp =
                                timestamp
                        ),
                )
        }

        val update =
            checkNotNull(
                finalUpdate
            )

        assertEquals(
            NavigationFusionMode.DirectObservation,
            update.mode,
        )

        assertEquals(
            NavigationPositionConfidence.High,
            update.stabilizedConfidence,
        )

        assertEquals(
            NavigationMotionAgreement.Consistent,
            update.motionAgreement,
        )

        assertEquals(
            NavigationRawGnssQuality.Strong,
            update.rawGnssQuality,
        )

        assertTrue(
            checkNotNull(
                update.estimate
            ).mayDriveNavigationProgress
        )
    }

    @Test
    fun highConfidenceIsCappedWithoutStrongRawGnss() {
        val engine =
            NavigationPositionFusionEngine()

        var update:
            NavigationPositionFusionUpdate? =
            null

        repeat(
            12
        ) {
                index ->

            val timestamp =
                (
                    index.toLong() +
                        1L
                ) *
                    1_000_000_000L

            update =
                engine.ingest(
                    sample =
                        locationSample(
                            index =
                                index,

                            timestamp =
                                timestamp,
                        ),

                    sensors =
                        sensorSnapshot(
                            timestamp =
                                timestamp,

                            bearingDegrees =
                                90.0,
                        ),

                    environment =
                        strongEnvironment(
                            timestamp =
                                timestamp
                        ),

                    rawGnss =
                        null,
                )
        }

        assertEquals(
            NavigationPositionConfidence.Medium,
            checkNotNull(
                update
            ).stabilizedConfidence,
        )
    }

    @Test
    fun repeatedMotionConflictDemotesConfidenceInsteadOfJumpingRoad() {
        val engine =
            NavigationPositionFusionEngine()

        repeat(
            12
        ) {
                index ->

            val timestamp =
                (
                    index.toLong() +
                        1L
                ) *
                    1_000_000_000L

            engine.ingest(
                sample =
                    locationSample(
                        index =
                            index,

                        timestamp =
                            timestamp,
                    ),

                sensors =
                    sensorSnapshot(
                        timestamp =
                            timestamp,

                        bearingDegrees =
                            90.0,
                    ),

                environment =
                    strongEnvironment(
                        timestamp =
                            timestamp
                    ),

                rawGnss =
                    strongRawGnss(
                        timestamp =
                            timestamp
                    ),
            )
        }

        val firstConflictTime =
            13_000_000_000L

        val firstConflict =
            engine.ingest(
                sample =
                    NavigationLocationSample(
                        position =
                            RoutePoint(
                                latitude =
                                    0.0001,

                                longitude =
                                    0.0011,
                            ),

                        horizontalAccuracyM =
                            4.0,

                        elapsedRealtimeNanos =
                            firstConflictTime,

                        provider =
                            "gps",
                    ),

                sensors =
                    sensorSnapshot(
                        timestamp =
                            firstConflictTime,

                        bearingDegrees =
                            90.0,
                    ),

                environment =
                    strongEnvironment(
                        timestamp =
                            firstConflictTime
                    ),

                rawGnss =
                    strongRawGnss(
                        timestamp =
                            firstConflictTime
                    ),
            )

        assertEquals(
            NavigationMotionAgreement.Conflicting,
            firstConflict.motionAgreement,
        )

        val secondConflictTime =
            14_000_000_000L

        val secondConflict =
            engine.ingest(
                sample =
                    NavigationLocationSample(
                        position =
                            RoutePoint(
                                latitude =
                                    0.0002,

                                longitude =
                                    0.0011,
                            ),

                        horizontalAccuracyM =
                            4.0,

                        elapsedRealtimeNanos =
                            secondConflictTime,

                        provider =
                            "gps",
                    ),

                sensors =
                    sensorSnapshot(
                        timestamp =
                            secondConflictTime,

                        bearingDegrees =
                            90.0,
                    ),

                environment =
                    strongEnvironment(
                        timestamp =
                            secondConflictTime
                    ),

                rawGnss =
                    strongRawGnss(
                        timestamp =
                            secondConflictTime
                    ),
            )

        assertEquals(
            NavigationMotionAgreement.Conflicting,
            secondConflict.motionAgreement,
        )

        assertFalse(
            secondConflict.stabilizedConfidence ==
                NavigationPositionConfidence.High
        )
    }

    @Test
    fun implausibleLocationJumpFallsBackToDeadReckoning() {
        val engine =
            NavigationPositionFusionEngine()

        repeat(
            12
        ) {
                index ->

            val timestamp =
                (
                    index.toLong() +
                        1L
                ) *
                    1_000_000_000L

            engine.ingest(
                sample =
                    locationSample(
                        index =
                            index,

                        timestamp =
                            timestamp,
                    ),

                sensors =
                    sensorSnapshot(
                        timestamp =
                            timestamp,

                        bearingDegrees =
                            90.0,
                    ),

                environment =
                    strongEnvironment(
                        timestamp =
                            timestamp
                    ),

                rawGnss =
                    strongRawGnss(
                        timestamp =
                            timestamp
                    ),
            )
        }

        val jumpTimestamp =
            13_000_000_000L

        val update =
            engine.ingest(
                sample =
                    NavigationLocationSample(
                        position =
                            RoutePoint(
                                latitude =
                                    1.0,

                                longitude =
                                    1.0,
                            ),

                        horizontalAccuracyM =
                            3.0,

                        elapsedRealtimeNanos =
                            jumpTimestamp,

                        provider =
                            "gps",
                    ),

                sensors =
                    sensorSnapshot(
                        timestamp =
                            jumpTimestamp,

                        bearingDegrees =
                            90.0,
                    ),

                environment =
                    strongEnvironment(
                        timestamp =
                            jumpTimestamp
                    ),

                rawGnss =
                    strongRawGnss(
                        timestamp =
                            jumpTimestamp
                    ),
            )

        assertEquals(
            NavigationFusionMode.DeadReckoning,
            update.mode,
        )

        assertEquals(
            NavigationPositionConfidence.Low,
            update.stabilizedConfidence,
        )

        assertTrue(
            update.integrityIssues.contains(
                PositionIntegrityIssue.ImplausibleMotion
            )
        )

        val predicted =
            checkNotNull(
                update.estimate
            )

        assertTrue(
            predicted.position.latitude <
                0.01
        )

        assertTrue(
            predicted.position.longitude <
                0.01
        )
    }

    @Test
    fun radioEvidenceIsVisibleButDoesNotDirectlyMoveCoordinate() {
        val engine =
            NavigationPositionFusionEngine()

        val timestamp =
            1_000_000_000L

        val environment =
            strongEnvironment(
                timestamp =
                    timestamp
            ).copy(
                radioObservations =
                    listOf(
                        NavigationRadioObservation(
                            technology =
                                NavigationRadioTechnology.WifiRtt,

                            sourceId =
                                NavigationPrivacySafeRadioId(
                                    "0123456789abcdef0123456789abcdef"
                                ),

                            rssiDbm =
                                -50,

                            frequencyMhz =
                                5_180,

                            distanceM =
                                4.2,

                            distanceStdDevM =
                                0.5,

                            elapsedRealtimeNanos =
                                timestamp,
                        )
                    )
            )

        val update =
            engine.ingest(
                sample =
                    NavigationLocationSample(
                        position =
                            RoutePoint(
                                latitude =
                                    47.14,

                                longitude =
                                    9.52,
                            ),

                        horizontalAccuracyM =
                            5.0,

                        elapsedRealtimeNanos =
                            timestamp,

                        provider =
                            "gps",
                    ),

                sensors =
                    sensorSnapshot(
                        timestamp =
                            timestamp,

                        bearingDegrees =
                            90.0,
                    ),

                environment =
                    environment,

                rawGnss =
                    strongRawGnss(
                        timestamp =
                            timestamp
                    ),
            )

        assertEquals(
            1,
            update.radioObservationCount,
        )

        assertEquals(
            47.14,
            checkNotNull(
                update.estimate
            ).position.latitude,
            0.000001,
        )

        assertEquals(
            9.52,
            checkNotNull(
                update.estimate
            ).position.longitude,
            0.000001,
        )
    }

    @Test
    fun resetRemovesFusionState() {
        val engine =
            NavigationPositionFusionEngine()

        val timestamp =
            1_000_000_000L

        engine.ingest(
            sample =
                NavigationLocationSample(
                    position =
                        RoutePoint(
                            latitude =
                                0.0,

                            longitude =
                                0.0,
                        ),

                    horizontalAccuracyM =
                        5.0,

                    elapsedRealtimeNanos =
                        timestamp,

                    provider =
                        "gps",
                ),

            sensors =
                sensorSnapshot(
                    timestamp =
                        timestamp,

                    bearingDegrees =
                        90.0,
                ),

            environment =
                strongEnvironment(
                    timestamp =
                        timestamp
                ),

            rawGnss =
                strongRawGnss(
                    timestamp =
                        timestamp
                ),
        )

        assertNotNull(
            engine.currentEstimate()
        )

        engine.reset()

        assertNull(
            engine.currentEstimate()
        )
    }

    private fun locationSample(
        index: Int,
        timestamp: Long,
    ) =
        NavigationLocationSample(
            position =
                RoutePoint(
                    latitude =
                        0.0,

                    longitude =
                        index *
                            0.0001,
                ),

            horizontalAccuracyM =
                4.0,

            elapsedRealtimeNanos =
                timestamp,

            provider =
                "gps",
        )

    private fun estimate(
        longitude: Double,
        timestamp: Long,
        velocityMps: Double,
    ) =
        NavigationPositionEstimate(
            position =
                RoutePoint(
                    latitude =
                        0.0,

                    longitude =
                        longitude,
                ),

            horizontalVelocityMps =
                velocityMps,

            bearingDegrees =
                90.0,

            covariance =
                PositionCovariance2D(
                    eastVarianceM2 =
                        25.0,

                    northVarianceM2 =
                        25.0,
                ),

            confidence =
                NavigationPositionConfidence.High,

            elapsedRealtimeNanos =
                timestamp,
        )

    private fun sensorSnapshot(
        timestamp: Long,
        bearingDegrees: Double,
    ) =
        NavigationSensorEvidenceSnapshot(
            gnss =
                null,

            acceleration =
                NavigationVector3Sample(
                    x =
                        0.0,

                    y =
                        0.0,

                    z =
                        9.81,

                    elapsedRealtimeNanos =
                        timestamp,
                ),

            gyroscope =
                NavigationVector3Sample(
                    x =
                        0.0,

                    y =
                        0.0,

                    z =
                        0.0,

                    elapsedRealtimeNanos =
                        timestamp,
                ),

            bearing =
                NavigationBearingSample(
                    bearingDegrees =
                        bearingDegrees,

                    elapsedRealtimeNanos =
                        timestamp,
                ),
        )

    private fun strongEnvironment(
        timestamp: Long,
    ): NavigationEnvironmentEvidenceSnapshot {

        val satellites =
            (1..12).map {
                    index ->

                NavigationObservedSatellite(
                    key =
                        NavigationSatelliteKey(
                            constellation =
                                if (
                                    index <=
                                        6
                                ) {
                                    NavigationGnssConstellation.Gps
                                } else {
                                    NavigationGnssConstellation.Galileo
                                },

                            svid =
                                index,
                        ),

                    cn0DbHz =
                        31.0,

                    azimuthDegrees =
                        (
                            index *
                                25.0
                        ) %
                            360.0,

                    elevationDegrees =
                        45.0,

                    usedInFix =
                        index <=
                            9,

                    carrierFrequencyHz =
                        1_575_420_000.0,

                    hasAlmanacData =
                        true,

                    hasEphemerisData =
                        true,

                    elapsedRealtimeNanos =
                        timestamp,
                )
            }

        return NavigationEnvironmentEvidenceSnapshot(
            observedSatellites =
                satellites,

            radioObservations =
                emptyList(),

            satelliteCatalogVersion =
                "test-v1",

            unknownSatelliteKeys =
                emptySet(),

            capturedAtElapsedRealtimeNanos =
                timestamp,
        )
    }

    private fun strongRawGnss(
        timestamp: Long,
    ): NavigationRawGnssSnapshot {

        val measurements =
            (1..12).map {
                    index ->

                rawMeasurement(
                    key =
                        NavigationSatelliteKey(
                            constellation =
                                if (
                                    index <=
                                        6
                                ) {
                                    NavigationGnssConstellation.Gps
                                } else {
                                    NavigationGnssConstellation.Galileo
                                },

                            svid =
                                index,
                        ),

                    frequencyHz =
                        if (
                            index %
                                2 ==
                                0
                        ) {
                            1_575_420_000.0
                        } else {
                            1_176_450_000.0
                        },

                    timestamp =
                        timestamp,
                )
            }

        return NavigationRawGnssSnapshot(
            clock =
                NavigationRawGnssClockEvidence(
                    biasNanos =
                        0.0,

                    biasUncertaintyNanos =
                        10.0,
                ),

            measurements =
                measurements,

            elapsedRealtimeNanos =
                timestamp,
        )
    }

    private fun rawMeasurement(
        key:
            NavigationSatelliteKey,

        frequencyHz:
            Double,

        timestamp:
            Long,
    ) =
        NavigationRawGnssMeasurement(
            satellite =
                key,

            cn0DbHz =
                32.0,

            pseudorangeRateMps =
                -125.0,

            pseudorangeRateUncertaintyMps =
                0.2,

            carrierFrequencyHz =
                frequencyHz,

            state =
                1,

            elapsedRealtimeNanos =
                timestamp,
        )
}