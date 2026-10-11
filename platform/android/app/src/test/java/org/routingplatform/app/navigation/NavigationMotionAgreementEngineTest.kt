package org.routingplatform.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationMotionAgreementEngineTest {

    @Test
    fun doesNotTrustSingleHeadingSample() {
        val engine =
            NavigationMotionAgreementEngine()

        val result =
            evaluateEastwardStep(
                engine =
                    engine,

                step =
                    0,

                deviceBearingDegrees =
                    90.0,
            )

        assertEquals(
            NavigationMotionAgreement.Unknown,
            result.agreement,
        )

        assertFalse(
            result.calibrated
        )
    }

    @Test
    fun repeatedConsistentMotionCalibratesAndBecomesConsistent() {
        val engine =
            NavigationMotionAgreementEngine()

        var result:
            NavigationMotionAgreementResult? =
            null

        repeat(
            5
        ) {
            result =
                evaluateEastwardStep(
                    engine =
                        engine,

                    step =
                        it,

                    deviceBearingDegrees =
                        90.0,
                )
        }

        val finalResult =
            checkNotNull(
                result
            )

        assertTrue(
            finalResult.calibrated
        )

        assertEquals(
            NavigationMotionAgreement.Consistent,
            finalResult.agreement,
        )

        assertTrue(
            checkNotNull(
                finalResult.headingDeltaDegrees
            ) <
                1.0
        )
    }

    @Test
    fun phoneMayBeMountedNinetyDegreesOffVehicleAxis() {
        val engine =
            NavigationMotionAgreementEngine()

        var result:
            NavigationMotionAgreementResult? =
            null

        repeat(
            5
        ) {
            result =
                evaluateEastwardStep(
                    engine =
                        engine,

                    step =
                        it,

                    deviceBearingDegrees =
                        0.0,
                )
        }

        val finalResult =
            checkNotNull(
                result
            )

        assertTrue(
            finalResult.calibrated
        )

        assertEquals(
            NavigationMotionAgreement.Consistent,
            finalResult.agreement,
        )

        assertEquals(
            90.0,
            checkNotNull(
                finalResult
                    .correctedDeviceBearingDegrees
            ),
            1.0,
        )
    }

    @Test
    fun sideJumpAfterCalibrationIsConflicting() {
        val engine =
            calibratedEastwardEngine()

        val previous =
            estimate(
                latitude =
                    0.0,

                longitude =
                    0.0006,

                timestamp =
                    10_000_000_000L,
            )

        val current =
            estimate(
                latitude =
                    0.0001,

                longitude =
                    0.0006,

                timestamp =
                    11_000_000_000L,
            )

        val result =
            engine.evaluate(
                previousEstimate =
                    previous,

                currentEstimate =
                    current,

                sensors =
                    sensors(
                        bearingDegrees =
                            90.0,

                        timestamp =
                            current
                                .elapsedRealtimeNanos,
                    ),
            )

        assertEquals(
            NavigationMotionAgreement.Conflicting,
            result.agreement,
        )

        assertTrue(
            checkNotNull(
                result.headingDeltaDegrees
            ) >
                65.0
        )
    }

    @Test
    fun rapidDeviceRotationProducesUnknownInsteadOfConflict() {
        val engine =
            calibratedEastwardEngine()

        val previous =
            estimate(
                latitude =
                    0.0,

                longitude =
                    0.0006,

                timestamp =
                    10_000_000_000L,
            )

        val current =
            estimate(
                latitude =
                    0.0001,

                longitude =
                    0.0006,

                timestamp =
                    11_000_000_000L,
            )

        val result =
            engine.evaluate(
                previousEstimate =
                    previous,

                currentEstimate =
                    current,

                sensors =
                    sensors(
                        bearingDegrees =
                            90.0,

                        timestamp =
                            current
                                .elapsedRealtimeNanos,

                        gyroZ =
                            1.0,
                    ),
            )

        assertEquals(
            NavigationMotionAgreement.Unknown,
            result.agreement,
        )

        assertNull(
            result.headingDeltaDegrees
        )
    }

    @Test
    fun staleBearingCannotDriveMotionAgreement() {
        val engine =
            calibratedEastwardEngine()

        val previous =
            estimate(
                latitude =
                    0.0,

                longitude =
                    0.0006,

                timestamp =
                    10_000_000_000L,
            )

        val current =
            estimate(
                latitude =
                    0.0,

                longitude =
                    0.0007,

                timestamp =
                    11_000_000_000L,
            )

        val result =
            engine.evaluate(
                previousEstimate =
                    previous,

                currentEstimate =
                    current,

                sensors =
                    sensors(
                        bearingDegrees =
                            90.0,

                        timestamp =
                            9_000_000_000L,
                    ),
            )

        assertEquals(
            NavigationMotionAgreement.Unknown,
            result.agreement,
        )
    }

    @Test
    fun almostStationaryPositionDoesNotCalibrateHeading() {
        val engine =
            NavigationMotionAgreementEngine()

        val previous =
            estimate(
                latitude =
                    0.0,

                longitude =
                    0.0,

                timestamp =
                    1_000_000_000L,
            )

        val current =
            estimate(
                latitude =
                    0.0,

                longitude =
                    0.000001,

                timestamp =
                    2_000_000_000L,
            )

        val result =
            engine.evaluate(
                previousEstimate =
                    previous,

                currentEstimate =
                    current,

                sensors =
                    sensors(
                        bearingDegrees =
                            90.0,

                        timestamp =
                            current
                                .elapsedRealtimeNanos,
                    ),
            )

        assertEquals(
            NavigationMotionAgreement.Unknown,
            result.agreement,
        )

        assertFalse(
            engine.calibrated
        )
    }

    @Test
    fun resetRemovesOrientationCalibration() {
        val engine =
            calibratedEastwardEngine()

        assertTrue(
            engine.calibrated
        )

        engine.reset()

        assertFalse(
            engine.calibrated
        )

        val result =
            evaluateEastwardStep(
                engine =
                    engine,

                step =
                    20,

                deviceBearingDegrees =
                    90.0,
            )

        assertEquals(
            NavigationMotionAgreement.Unknown,
            result.agreement,
        )

        assertFalse(
            result.calibrated
        )
    }

    private fun calibratedEastwardEngine():
        NavigationMotionAgreementEngine {

        val engine =
            NavigationMotionAgreementEngine()

        repeat(
            5
        ) {
            evaluateEastwardStep(
                engine =
                    engine,

                step =
                    it,

                deviceBearingDegrees =
                    90.0,
            )
        }

        check(
            engine.calibrated
        )

        return engine
    }

    private fun evaluateEastwardStep(
        engine: NavigationMotionAgreementEngine,
        step: Int,
        deviceBearingDegrees: Double,
    ): NavigationMotionAgreementResult {

        val previousTimestamp =
            (
                step.toLong() +
                    1L
            ) *
                1_000_000_000L

        val currentTimestamp =
            previousTimestamp +
                1_000_000_000L

        val previous =
            estimate(
                latitude =
                    0.0,

                longitude =
                    step *
                        0.0001,

                timestamp =
                    previousTimestamp,
            )

        val current =
            estimate(
                latitude =
                    0.0,

                longitude =
                    (
                        step +
                            1
                    ) *
                        0.0001,

                timestamp =
                    currentTimestamp,
            )

        return engine.evaluate(
            previousEstimate =
                previous,

            currentEstimate =
                current,

            sensors =
                sensors(
                    bearingDegrees =
                        deviceBearingDegrees,

                    timestamp =
                        currentTimestamp,
                ),
        )
    }

    private fun sensors(
        bearingDegrees: Double,
        timestamp: Long,
        gyroZ: Double = 0.0,
    ) =
        NavigationSensorEvidenceSnapshot(
            gnss =
                null,

            acceleration =
                null,

            gyroscope =
                NavigationVector3Sample(
                    x =
                        0.0,

                    y =
                        0.0,

                    z =
                        gyroZ,

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

    private fun estimate(
        latitude: Double,
        longitude: Double,
        timestamp: Long,
    ) =
        NavigationPositionEstimate(
            position =
                RoutePoint(
                    latitude =
                        latitude,

                    longitude =
                        longitude,
                ),

            horizontalVelocityMps =
                null,

            bearingDegrees =
                null,

            covariance =
                PositionCovariance2D(
                    eastVarianceM2 =
                        25.0,

                    northVarianceM2 =
                        25.0,
                ),

            confidence =
                NavigationPositionConfidence.Medium,

            elapsedRealtimeNanos =
                timestamp,
        )
}