package org.routingplatform.app.navigation

import kotlin.math.PI
import kotlin.math.sqrt

enum class NavigationFusionMode {
    DirectObservation,
    DeadReckoning,
    Rejected,
    Lost,
}

data class NavigationPositionFusionUpdate(
    val estimate:
        NavigationPositionEstimate?,

    val mode:
        NavigationFusionMode,

    val stabilizedConfidence:
        NavigationPositionConfidence,

    val motionAgreement:
        NavigationMotionAgreement,

    val integrityIssues:
        Set<PositionIntegrityIssue>,

    val rawGnssQuality:
        NavigationRawGnssQuality?,

    val radioObservationCount:
        Int,

    val unknownSatelliteCount:
        Int,
)

class NavigationPositionFusionEngine(
    private val integrityMonitor:
        NavigationPositionIntegrityMonitor =
        NavigationPositionIntegrityMonitor(),

    private val motionEngine:
        NavigationMotionAgreementEngine =
        NavigationMotionAgreementEngine(),

    private val confidenceHysteresis:
        NavigationConfidenceHysteresis =
        NavigationConfidenceHysteresis(),

    private val deadReckoning:
        NavigationDeadReckoningEngine =
        NavigationDeadReckoningEngine(),
) {
    private var previousAcceptedRawEstimate:
        NavigationPositionEstimate? =
        null

    private var lastFusedEstimate:
        NavigationPositionEstimate? =
        null

    private var lastTrustedTravelBearingDegrees:
        Double? =
        null

    fun ingest(
        sample:
            NavigationLocationSample,

        sensors:
            NavigationSensorEvidenceSnapshot,

        environment:
            NavigationEnvironmentEvidenceSnapshot?,

        rawGnss:
            NavigationRawGnssSnapshot?,
    ): NavigationPositionFusionUpdate {

        val integrity =
            integrityMonitor.inspect(
                sample
            )

        if (
            !integrity.accepted
        ) {
            observeMissingEvidence(
                timestamp =
                    sample.elapsedRealtimeNanos
            )

            val predicted =
                lastFusedEstimate
                    ?.let {
                        deadReckoning.predict(
                            from =
                                it,

                            targetElapsedRealtimeNanos =
                                sample.elapsedRealtimeNanos,

                            travelBearingDegrees =
                                lastTrustedTravelBearingDegrees,
                        )
                    }

            if (
                predicted != null
            ) {
                lastFusedEstimate =
                    predicted

                return NavigationPositionFusionUpdate(
                    estimate =
                        predicted,

                    mode =
                        NavigationFusionMode.DeadReckoning,

                    stabilizedConfidence =
                        NavigationPositionConfidence.Low,

                    motionAgreement =
                        NavigationMotionAgreement.Unknown,

                    integrityIssues =
                        integrity.issues,

                    rawGnssQuality =
                        rawGnss?.quality,

                    radioObservationCount =
                        environment
                            ?.radioObservations
                            ?.size
                            ?: 0,

                    unknownSatelliteCount =
                        environment
                            ?.unknownSatelliteKeys
                            ?.size
                            ?: 0,
                )
            }

            return NavigationPositionFusionUpdate(
                estimate =
                    null,

                mode =
                    NavigationFusionMode.Rejected,

                stabilizedConfidence =
                    confidenceHysteresis.confidence,

                motionAgreement =
                    NavigationMotionAgreement.Unknown,

                integrityIssues =
                    integrity.issues,

                rawGnssQuality =
                    rawGnss?.quality,

                radioObservationCount =
                    environment
                        ?.radioObservations
                        ?.size
                        ?: 0,

                unknownSatelliteCount =
                    environment
                        ?.unknownSatelliteKeys
                        ?.size
                        ?: 0,
            )
        }

        val rawEstimate =
            checkNotNull(
                integrity.estimate
            )

        val previousRaw =
            previousAcceptedRawEstimate

        val motionResult =
            if (
                previousRaw !=
                    null
            ) {
                motionEngine.evaluate(
                    previousEstimate =
                        previousRaw,

                    currentEstimate =
                        rawEstimate,

                    sensors =
                        sensors,
                )
            } else {
                NavigationMotionAgreementResult(
                    agreement =
                        NavigationMotionAgreement.Unknown,

                    movementBearingDegrees =
                        null,

                    correctedDeviceBearingDegrees =
                        null,

                    headingDeltaDegrees =
                        null,

                    calibrated =
                        motionEngine.calibrated,
                )
            }

        if (
            motionResult.agreement ==
                NavigationMotionAgreement.Consistent &&
            motionResult.correctedDeviceBearingDegrees !=
                null
        ) {
            lastTrustedTravelBearingDegrees =
                motionResult
                    .correctedDeviceBearingDegrees
        }

        val gnssEvidence =
            buildGnssEvidence(
                environment
            )

        val imuEvidence =
            buildImuEvidence(
                sensors
            )

        val observation =
            NavigationPositionObservation(
                elapsedRealtimeNanos =
                    rawEstimate.elapsedRealtimeNanos,

                estimate =
                    rawEstimate,

                gnssEvidence =
                    gnssEvidence,

                imuEvidence =
                    imuEvidence,

                motionAgreement =
                    motionResult.agreement,
            )

        val hysteresisConfidence =
            confidenceHysteresis.observe(
                observation
            )

        val confidence =
            applyRawGnssSafetyCap(
                confidence =
                    hysteresisConfidence,

                rawGnss =
                    rawGnss,
            )

        val fusedEstimate =
            rawEstimate.copy(
                bearingDegrees =
                    lastTrustedTravelBearingDegrees,

                confidence =
                    confidence,
            )

        previousAcceptedRawEstimate =
            rawEstimate

        lastFusedEstimate =
            fusedEstimate

        return NavigationPositionFusionUpdate(
            estimate =
                fusedEstimate,

            mode =
                NavigationFusionMode.DirectObservation,

            stabilizedConfidence =
                confidence,

            motionAgreement =
                motionResult.agreement,

            integrityIssues =
                integrity.issues,

            rawGnssQuality =
                rawGnss?.quality,

            radioObservationCount =
                environment
                    ?.radioObservations
                    ?.size
                    ?: 0,

            unknownSatelliteCount =
                environment
                    ?.unknownSatelliteKeys
                    ?.size
                    ?: 0,
        )
    }

    /*
     * High-rate callers may use this between GNSS/location fixes.
     *
     * It intentionally does NOT feed the sample-count hysteresis.
     * Otherwise a 50 Hz prediction loop would turn three missing
     * GNSS ticks into "Lost" within milliseconds.
     */
    fun predict(
        targetElapsedRealtimeNanos:
            Long,
    ): NavigationPositionFusionUpdate {

        val last =
            lastFusedEstimate
                ?: return lostUpdate()

        val predicted =
            deadReckoning.predict(
                from =
                    last,

                targetElapsedRealtimeNanos =
                    targetElapsedRealtimeNanos,

                travelBearingDegrees =
                    lastTrustedTravelBearingDegrees,
            )
                ?: return lostUpdate()

        lastFusedEstimate =
            predicted

        return NavigationPositionFusionUpdate(
            estimate =
                predicted,

            mode =
                NavigationFusionMode.DeadReckoning,

            stabilizedConfidence =
                NavigationPositionConfidence.Low,

            motionAgreement =
                NavigationMotionAgreement.Unknown,

            integrityIssues =
                emptySet(),

            rawGnssQuality =
                null,

            radioObservationCount =
                0,

            unknownSatelliteCount =
                0,
        )
    }

    fun currentEstimate():
        NavigationPositionEstimate? =
        lastFusedEstimate

    fun reset() {
        integrityMonitor.reset()
        motionEngine.reset()
        confidenceHysteresis.reset()

        previousAcceptedRawEstimate =
            null

        lastFusedEstimate =
            null

        lastTrustedTravelBearingDegrees =
            null
    }

    private fun observeMissingEvidence(
        timestamp: Long,
    ) {
        confidenceHysteresis.observe(
            NavigationPositionObservation(
                elapsedRealtimeNanos =
                    timestamp,

                estimate =
                    null,

                gnssEvidence =
                    null,

                imuEvidence =
                    null,

                motionAgreement =
                    NavigationMotionAgreement.Unknown,
            )
        )
    }

    private fun lostUpdate() =
        NavigationPositionFusionUpdate(
            estimate =
                null,

            mode =
                NavigationFusionMode.Lost,

            stabilizedConfidence =
                NavigationPositionConfidence.Lost,

            motionAgreement =
                NavigationMotionAgreement.Unknown,

            integrityIssues =
                emptySet(),

            rawGnssQuality =
                null,

            radioObservationCount =
                0,

            unknownSatelliteCount =
                0,
        )
}

private fun buildGnssEvidence(
    environment:
        NavigationEnvironmentEvidenceSnapshot?,
): NavigationGnssEvidence? {

    val satellites =
        environment
            ?.observedSatellites
            .orEmpty()

    if (
        satellites.isEmpty()
    ) {
        return null
    }

    val used =
        satellites.filter {
            it.usedInFix
        }

    val cn0Source =
        if (
            used.isNotEmpty()
        ) {
            used
        } else {
            satellites
        }

    val meanCn0 =
        cn0Source
            .map {
                it.cn0DbHz
            }
            .average()

    val maximumCn0 =
        satellites
            .maxOfOrNull {
                it.cn0DbHz
            }

    return NavigationGnssEvidence(
        visibleSatelliteCount =
            satellites.size,

        usedInFixSatelliteCount =
            used.size,

        meanCn0DbHz =
            meanCn0,

        maxCn0DbHz =
            maximumCn0,
    )
}

private fun buildImuEvidence(
    sensors:
        NavigationSensorEvidenceSnapshot,
): NavigationImuEvidence? {

    val acceleration =
        sensors.acceleration

    val gyroscope =
        sensors.gyroscope

    val bearing =
        sensors.bearing

    if (
        acceleration == null &&
        gyroscope == null &&
        bearing == null
    ) {
        return null
    }

    val accelerationMagnitude =
        acceleration?.let {
            sqrt(
                it.x *
                    it.x +
                    it.y *
                        it.y +
                    it.z *
                        it.z
            )
        }

    val yawRateDegreesPerSecond =
        gyroscope?.let {
            it.z *
                RADIANS_TO_DEGREES
        }

    return NavigationImuEvidence(
        accelerationMagnitudeMps2 =
            accelerationMagnitude,

        yawRateDegreesPerSecond =
            yawRateDegreesPerSecond,

        bearingDegrees =
            bearing
                ?.bearingDegrees,
    )
}

private fun applyRawGnssSafetyCap(
    confidence:
        NavigationPositionConfidence,

    rawGnss:
        NavigationRawGnssSnapshot?,
): NavigationPositionConfidence {

    if (
        confidence !=
            NavigationPositionConfidence.High
    ) {
        return confidence
    }

    return if (
        rawGnss?.quality ==
            NavigationRawGnssQuality.Strong
    ) {
        NavigationPositionConfidence.High
    } else {
        NavigationPositionConfidence.Medium
    }
}

private const val RADIANS_TO_DEGREES =
    180.0 / PI