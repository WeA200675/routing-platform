package org.routingplatform.app.navigation

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import java.io.File

enum class NavigationRuntimePipelineStatus {
    Stopped,
    Starting,
    Running,
    NativeProgressUpdated,
    SafetyHold,
    NativeUpdateFailed,
}

data class NavigationRuntimeTelemetry(
    val pipelineStatus:
        NavigationRuntimePipelineStatus,

    val automaticProgressActive:
        Boolean,

    val confidence:
        NavigationPositionConfidence,

    val fusionMode:
        NavigationFusionMode?,

    val safetyStatus:
        NavigationRouteProgressSafetyStatus?,

    val rawGnssQuality:
        NavigationRawGnssQuality?,

    val radioObservationCount:
        Int,

    val unknownSatelliteCount:
        Int,

    val acceptedProgress:
        RouteProgressAnchor?,

    val lastLocationAccuracyM:
        Double?,
) {
    companion object {
        fun stopped(
            acceptedProgress:
                RouteProgressAnchor? =
                null,
        ) =
            NavigationRuntimeTelemetry(
                pipelineStatus =
                    NavigationRuntimePipelineStatus.Stopped,

                automaticProgressActive =
                    false,

                confidence =
                    NavigationPositionConfidence.Lost,

                fusionMode =
                    null,

                safetyStatus =
                    null,

                rawGnssQuality =
                    null,

                radioObservationCount =
                    0,

                unknownSatelliteCount =
                    0,

                acceptedProgress =
                    acceptedProgress,

                lastLocationAccuracyM =
                    null,
            )
    }
}

class AndroidNavigationRuntimeController(
    context: Context,
    bridge: NavigationCoreBridge,
) {
    private val appContext =
        context.applicationContext

    private val locationSource =
        AndroidLocationSource(
            appContext
        )

    private val sensorSource =
        AndroidNavigationSensorSource(
            appContext
        )

    private val rawGnssSource =
        AndroidRawGnssObservationSource(
            appContext
        )

    private val environmentSource =
        AndroidEnvironmentObservationSource(
            appContext
        )

    private val satelliteKnowledge =
        NavigationSatelliteKnowledgeRepository(
            store =
                FileNavigationSatelliteCatalogStore(
                    File(
                        appContext.filesDir,
                        "navigation/satellite-catalog.bin",
                    )
                )
        )

    private val coordinator =
        NavigationProgressCoordinator(
            bridge =
                bridge
        )

    @Volatile
    private var latestEnvironment =
        NavigationEnvironmentEvidenceSnapshot(
            observedSatellites =
                emptyList(),

            radioObservations =
                emptyList(),

            satelliteCatalogVersion =
                satelliteKnowledge
                    .catalogVersion(),

            unknownSatelliteKeys =
                emptySet(),

            capturedAtElapsedRealtimeNanos =
                0L,
        )

    private val aiView =
        NavigationEnvironmentAiReadOnlyAdapter(
            snapshotProvider = {
                latestEnvironment
            },

            satelliteKnowledge =
                satelliteKnowledge,
        )

    @Volatile
    private var active =
        false

    private var route:
        List<RoutePoint> =
        emptyList()

    private var telemetryListener:
        (
            (
                NavigationRuntimeTelemetry
            ) -> Unit
        )? =
        null

    private var snapshotListener:
        (
            (
                NavigationUiSnapshot
            ) -> Unit
        )? =
        null

    private var lastWifiCaptureNanos:
        Long? =
        null

    private var lastWifiRttRequestNanos:
        Long? =
        null

    fun start(
        route:
            List<RoutePoint>,

        initialProgress:
            RouteProgressAnchor,

        onSnapshot:
            (
                NavigationUiSnapshot
            ) -> Unit,

        onTelemetry:
            (
                NavigationRuntimeTelemetry
            ) -> Unit,
    ): Boolean {

        require(
            route.size >= 2
        )

        if (
            !hasPreciseNavigationLocationPermission(
                appContext
            )
        ) {
            // Defense in depth: approximate location must never
            // activate automatic native navigation progress.
            return false
        }

        stopInternal(
            notify =
                false
        )

        this.route =
            route.toList()

        telemetryListener =
            onTelemetry

        snapshotListener =
            onSnapshot

        coordinator.start(
            initialProgress
        )

        sensorSource.clear()
        rawGnssSource.clear()
        environmentSource.clear()

        lastWifiCaptureNanos =
            null

        lastWifiRttRequestNanos =
            null

        onTelemetry(
            NavigationRuntimeTelemetry(
                pipelineStatus =
                    NavigationRuntimePipelineStatus.Starting,

                automaticProgressActive =
                    false,

                confidence =
                    NavigationPositionConfidence.Lost,

                fusionMode =
                    null,

                safetyStatus =
                    null,

                rawGnssQuality =
                    null,

                radioObservationCount =
                    0,

                unknownSatelliteCount =
                    0,

                acceptedProgress =
                    initialProgress,

                lastLocationAccuracyM =
                    null,
            )
        )

        /*
         * Every auxiliary source is best-effort.
         *
         * Location is the only source required to start the
         * automatic progress pipeline. Missing BLE/RTT/raw-GNSS
         * therefore lowers evidence quality but does not crash
         * navigation.
         */
        sensorSource.start()

        rawGnssSource.start()

        environmentSource
            .startGnssSatelliteObservation()

        environmentSource
            .captureWifiScanResults()

        environmentSource
            .startBluetoothLeObservation()

        requestWifiRttBestEffort(
            SystemClock
                .elapsedRealtimeNanos()
        )

        active =
            true

        val locationStarted =
            locationSource.start {
                    sample ->

                processLocation(
                    sample
                )
            }

        if (
            !locationStarted
        ) {
            active =
                false

            stopSources()

            onTelemetry(
                NavigationRuntimeTelemetry.stopped(
                    acceptedProgress =
                        initialProgress
                )
            )

            return false
        }

        onTelemetry(
            NavigationRuntimeTelemetry(
                pipelineStatus =
                    NavigationRuntimePipelineStatus.Running,

                automaticProgressActive =
                    true,

                confidence =
                    NavigationPositionConfidence.Lost,

                fusionMode =
                    null,

                safetyStatus =
                    null,

                rawGnssQuality =
                    null,

                radioObservationCount =
                    0,

                unknownSatelliteCount =
                    0,

                acceptedProgress =
                    initialProgress,

                lastLocationAccuracyM =
                    null,
            )
        )

        return true
    }

    fun stop() {
        stopInternal(
            notify =
                true
        )
    }

    fun reset() {
        stopInternal(
            notify =
                false
        )

        coordinator.reset()

        sensorSource.clear()
        rawGnssSource.clear()
        environmentSource.clear()

        route =
            emptyList()

        latestEnvironment =
            NavigationEnvironmentEvidenceSnapshot(
                observedSatellites =
                    emptyList(),

                radioObservations =
                    emptyList(),

                satelliteCatalogVersion =
                    satelliteKnowledge
                        .catalogVersion(),

                unknownSatelliteKeys =
                    emptySet(),

                capturedAtElapsedRealtimeNanos =
                    0L,
            )

        lastWifiCaptureNanos =
            null

        lastWifiRttRequestNanos =
            null
    }

    fun aiReadOnlyView():
        NavigationEnvironmentAiReadOnlyView =
        aiView

    private fun processLocation(
        sample:
            NavigationLocationSample,
    ) {
        if (
            !active ||
            route.size <
                2
        ) {
            return
        }

        captureRadioEvidenceBestEffort(
            sample.elapsedRealtimeNanos
        )

        val environment =
            environmentSource.snapshot(
                satelliteKnowledge
            )

        latestEnvironment =
            environment

        val sensorSnapshot =
            sensorSnapshotAt(
                sample.elapsedRealtimeNanos
            )

        val rawGnss =
            recentRawGnss(
                locationTimestampNanos =
                    sample.elapsedRealtimeNanos
            )

        val result =
            coordinator.ingest(
                route =
                    route,

                sample =
                    sample,

                sensors =
                    sensorSnapshot,

                environment =
                    environment,

                rawGnss =
                    rawGnss,
            )

        if (
            !active
        ) {
            return
        }

        result
            .nativeSnapshot
            ?.let {
                snapshotListener
                    ?.invoke(
                        it
                    )
            }

        val pipelineStatus =
            when {
                result.nativeFailureMessage !=
                    null ->
                    NavigationRuntimePipelineStatus.NativeUpdateFailed

                result.nativeForwarded ->
                    NavigationRuntimePipelineStatus.NativeProgressUpdated

                result.safetyDecision.status ==
                    NavigationRouteProgressSafetyStatus.Accepted ->
                    NavigationRuntimePipelineStatus.Running

                else ->
                    NavigationRuntimePipelineStatus.SafetyHold
            }

        telemetryListener
            ?.invoke(
                NavigationRuntimeTelemetry(
                    pipelineStatus =
                        pipelineStatus,

                    automaticProgressActive =
                        true,

                    confidence =
                        result
                            .fusionUpdate
                            .stabilizedConfidence,

                    fusionMode =
                        result
                            .fusionUpdate
                            .mode,

                    safetyStatus =
                        result
                            .safetyDecision
                            .status,

                    rawGnssQuality =
                        result
                            .fusionUpdate
                            .rawGnssQuality,

                    radioObservationCount =
                        result
                            .fusionUpdate
                            .radioObservationCount,

                    unknownSatelliteCount =
                        result
                            .fusionUpdate
                            .unknownSatelliteCount,

                    acceptedProgress =
                        coordinator
                            .currentNativeProgress(),

                    lastLocationAccuracyM =
                        sample
                            .horizontalAccuracyM,
                )
            )
    }

    private fun sensorSnapshotAt(
        timestampNanos:
            Long,
    ): NavigationSensorEvidenceSnapshot {

        val acceleration =
            sensorSource
                .accelerationHistory()
                .lastOrNull {
                    isSensorSampleUsable(
                        sampleTimestampNanos =
                            it.elapsedRealtimeNanos,

                        targetTimestampNanos =
                            timestampNanos,

                        maximumAgeNanos =
                            ACCELERATION_MAX_AGE_NANOS,
                    )
                }

        val gyroscope =
            sensorSource
                .gyroscopeHistory()
                .lastOrNull {
                    isSensorSampleUsable(
                        sampleTimestampNanos =
                            it.elapsedRealtimeNanos,

                        targetTimestampNanos =
                            timestampNanos,

                        maximumAgeNanos =
                            GYROSCOPE_MAX_AGE_NANOS,
                    )
                }

        val bearing =
            sensorSource
                .bearingHistory()
                .lastOrNull {
                    isSensorSampleUsable(
                        sampleTimestampNanos =
                            it.elapsedRealtimeNanos,

                        targetTimestampNanos =
                            timestampNanos,

                        maximumAgeNanos =
                            BEARING_MAX_AGE_NANOS,
                    )
                }

        val gnss =
            sensorSource
                .gnssHistory()
                .lastOrNull {
                    isSensorSampleUsable(
                        sampleTimestampNanos =
                            it.elapsedRealtimeNanos,

                        targetTimestampNanos =
                            timestampNanos,

                        maximumAgeNanos =
                            GNSS_STATUS_MAX_AGE_NANOS,
                    )
                }

        return NavigationSensorEvidenceSnapshot(
            gnss =
                gnss,

            acceleration =
                acceleration,

            gyroscope =
                gyroscope,

            bearing =
                bearing,
        )
    }

    private fun recentRawGnss(
        locationTimestampNanos:
            Long,
    ): NavigationRawGnssSnapshot? {

        val raw =
            rawGnssSource.snapshot()
                ?: return null

        val difference =
            absoluteDifference(
                raw.elapsedRealtimeNanos,
                locationTimestampNanos,
            )

        return raw.takeIf {
            difference <=
                RAW_GNSS_MAX_TIME_DIFFERENCE_NANOS
        }
    }

    private fun captureRadioEvidenceBestEffort(
        timestampNanos:
            Long,
    ) {
        val previousWifi =
            lastWifiCaptureNanos

        if (
            previousWifi ==
                null ||
            timestampNanos -
                previousWifi >=
                    WIFI_CAPTURE_INTERVAL_NANOS
        ) {
            environmentSource
                .captureWifiScanResults()

            lastWifiCaptureNanos =
                timestampNanos
        }

        val previousRtt =
            lastWifiRttRequestNanos

        if (
            previousRtt ==
                null ||
            timestampNanos -
                previousRtt >=
                    WIFI_RTT_INTERVAL_NANOS
        ) {
            requestWifiRttBestEffort(
                timestampNanos
            )
        }
    }

    private fun requestWifiRttBestEffort(
        timestampNanos:
            Long,
    ) {
        /*
         * Record the request time even when the device/AP does
         * not support RTT. Otherwise a 1 Hz location stream would
         * continuously retry an unavailable feature.
         */
        lastWifiRttRequestNanos =
            timestampNanos

        environmentSource
            .requestWifiRttObservation()
    }

    private fun stopInternal(
        notify:
            Boolean,
    ) {
        val previousProgress =
            coordinator
                .currentNativeProgress()

        active =
            false

        stopSources()

        if (
            notify
        ) {
            telemetryListener
                ?.invoke(
                    NavigationRuntimeTelemetry.stopped(
                        acceptedProgress =
                            previousProgress
                    )
                )
        }

        telemetryListener =
            null

        snapshotListener =
            null
    }

    private fun stopSources() {
        locationSource.stop()
        sensorSource.stop()
        rawGnssSource.stop()
        environmentSource.stopAll()
    }
}

fun hasPreciseNavigationLocationPermission(
    context: Context,
): Boolean =
    context.checkSelfPermission(
        Manifest.permission.ACCESS_FINE_LOCATION
    ) ==
        PackageManager.PERMISSION_GRANTED

fun navigationRuntimePermissionsToRequest(
    context: Context,
): Array<String> {

    val permissions =
        mutableListOf<String>()

    /*
     * Android 12+ expects coarse and fine location to be
     * requested together when precise location is desired.
     */
    if (
        context.checkSelfPermission(
            Manifest.permission.ACCESS_FINE_LOCATION
        ) !=
        PackageManager.PERMISSION_GRANTED
    ) {
        permissions.add(
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        permissions.add(
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }

    if (
        Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.S &&
        context.checkSelfPermission(
            Manifest.permission.BLUETOOTH_SCAN
        ) !=
        PackageManager.PERMISSION_GRANTED
    ) {
        permissions.add(
            Manifest.permission.BLUETOOTH_SCAN
        )
    }

    if (
        Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.TIRAMISU &&
        context.checkSelfPermission(
            Manifest.permission.NEARBY_WIFI_DEVICES
        ) !=
        PackageManager.PERMISSION_GRANTED
    ) {
        permissions.add(
            Manifest.permission.NEARBY_WIFI_DEVICES
        )
    }

    return permissions
        .distinct()
        .toTypedArray()
}

private fun isSensorSampleUsable(
    sampleTimestampNanos:
        Long,

    targetTimestampNanos:
        Long,

    maximumAgeNanos:
        Long,
): Boolean {

    if (
        sampleTimestampNanos >
            targetTimestampNanos
    ) {
        /*
         * Do not use a sensor sample from the future relative
         * to the GNSS/location fix.
         */
        return false
    }

    return targetTimestampNanos -
        sampleTimestampNanos <=
            maximumAgeNanos
}

private fun absoluteDifference(
    first:
        Long,

    second:
        Long,
): Long =
    if (
        first >=
            second
    ) {
        first -
            second
    } else {
        second -
            first
    }

private const val ACCELERATION_MAX_AGE_NANOS =
    500_000_000L

private const val GYROSCOPE_MAX_AGE_NANOS =
    500_000_000L

private const val BEARING_MAX_AGE_NANOS =
    750_000_000L

private const val GNSS_STATUS_MAX_AGE_NANOS =
    2_000_000_000L

private const val RAW_GNSS_MAX_TIME_DIFFERENCE_NANOS =
    2_000_000_000L

private const val WIFI_CAPTURE_INTERVAL_NANOS =
    5_000_000_000L

private const val WIFI_RTT_INTERVAL_NANOS =
    10_000_000_000L