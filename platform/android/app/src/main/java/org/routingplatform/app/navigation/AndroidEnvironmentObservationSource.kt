package org.routingplatform.app.navigation

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult as BluetoothScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.location.GnssStatus
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.security.SecureRandom

class AndroidEnvironmentObservationSource(
    context: Context,
) {
    private val appContext =
        context.applicationContext

    private val locationManager =
        appContext.getSystemService(
            Context.LOCATION_SERVICE
        ) as LocationManager

    private val wifiManager =
        appContext.getSystemService(
            Context.WIFI_SERVICE
        ) as WifiManager

    private val bluetoothManager =
        appContext.getSystemService(
            Context.BLUETOOTH_SERVICE
        ) as BluetoothManager

    private val mainHandler =
        Handler(
            Looper.getMainLooper()
        )

    private val privacyHasher =
        NavigationPrivacyIdentifierHasher(
            newPrivacyKey()
        )

    private val lock =
        Any()

    private var gnssStarted =
        false

    private var bleStarted =
        false

    private var latestSatellites:
        List<NavigationObservedSatellite> =
        emptyList()

    private val latestWifiById =
        linkedMapOf<
            NavigationPrivacySafeRadioId,
            NavigationRadioObservation
        >()

    private val latestBleById =
        linkedMapOf<
            NavigationPrivacySafeRadioId,
            NavigationRadioObservation
        >()

    private val latestWifiRttById =
        linkedMapOf<
            NavigationPrivacySafeRadioId,
            NavigationRadioObservation
        >()

    private val gnssCallback =
        object :
            GnssStatus.Callback() {

            override fun onSatelliteStatusChanged(
                status: GnssStatus,
            ) {
                val timestamp =
                    SystemClock
                        .elapsedRealtimeNanos()

                val observations =
                    ArrayList<
                        NavigationObservedSatellite
                    >(
                        status.satelliteCount
                    )

                for (
                    index in
                        0 until
                            status.satelliteCount
                ) {
                    val cn0 =
                        status
                            .getCn0DbHz(
                                index
                            )
                            .toDouble()

                    val azimuth =
                        normalizeDegrees(
                            status
                                .getAzimuthDegrees(
                                    index
                                )
                                .toDouble()
                        )

                    val elevation =
                        status
                            .getElevationDegrees(
                                index
                            )
                            .toDouble()
                            .coerceIn(
                                -90.0,
                                90.0,
                            )

                    val carrierFrequencyHz =
                        if (
                            status.hasCarrierFrequencyHz(
                                index
                            )
                        ) {
                            status
                                .getCarrierFrequencyHz(
                                    index
                                )
                                .toDouble()
                                .takeIf {
                                    it.isFinite() &&
                                        it > 0.0
                                }
                        } else {
                            null
                        }

                    if (
                        !cn0.isFinite() ||
                        cn0 < 0.0
                    ) {
                        continue
                    }

                    observations.add(
                        NavigationObservedSatellite(
                            key =
                                NavigationSatelliteKey(
                                    constellation =
                                        mapAndroidConstellation(
                                            status
                                                .getConstellationType(
                                                    index
                                                )
                                        ),

                                    svid =
                                        status
                                            .getSvid(
                                                index
                                            ),
                                ),

                            cn0DbHz =
                                cn0,

                            azimuthDegrees =
                                azimuth,

                            elevationDegrees =
                                elevation,

                            usedInFix =
                                status.usedInFix(
                                    index
                                ),

                            carrierFrequencyHz =
                                carrierFrequencyHz,

                            hasAlmanacData =
                                status.hasAlmanacData(
                                    index
                                ),

                            hasEphemerisData =
                                status.hasEphemerisData(
                                    index
                                ),

                            elapsedRealtimeNanos =
                                timestamp,
                        )
                    )
                }

                synchronized(
                    lock
                ) {
                    latestSatellites =
                        observations
                }
            }
        }

    private val bleCallback =
        object :
            ScanCallback() {

            override fun onScanResult(
                callbackType: Int,
                result: BluetoothScanResult,
            ) {
                captureBleResult(
                    result
                )
            }

            override fun onBatchScanResults(
                results:
                    MutableList<
                        BluetoothScanResult
                    >,
            ) {
                results.forEach {
                    captureBleResult(
                        it
                    )
                }
            }

            override fun onScanFailed(
                errorCode: Int,
            ) {
                Unit
            }
        }

    @SuppressLint("MissingPermission")
    fun startGnssSatelliteObservation():
        Boolean {

        stopGnssSatelliteObservation()

        if (
            !hasFineLocationPermission(
                appContext
            )
        ) {
            return false
        }

        gnssStarted =
            try {
                locationManager
                    .registerGnssStatusCallback(
                        gnssCallback,
                        mainHandler,
                    )
            } catch (
                _: SecurityException
            ) {
                false
            } catch (
                _: IllegalArgumentException
            ) {
                false
            }

        return gnssStarted
    }

    fun stopGnssSatelliteObservation() {
        if (
            gnssStarted
        ) {
            runCatching {
                locationManager
                    .unregisterGnssStatusCallback(
                        gnssCallback
                    )
            }
        }

        gnssStarted =
            false
    }

    /*
     * Passive consumption of the platform's current Wi-Fi scan
     * results. We deliberately do not force high-rate startScan()
     * calls here because Android throttles active scans.
     */
    @SuppressLint("MissingPermission")
    fun captureWifiScanResults():
        Int {

        if (
            !hasFineLocationPermission(
                appContext
            )
        ) {
            return 0
        }

        val scanResults =
            try {
                wifiManager.scanResults
            } catch (
                _: SecurityException
            ) {
                return 0
            }

        val now =
            SystemClock
                .elapsedRealtimeNanos()

        val mapped =
            scanResults.mapNotNull {
                    result ->

                val bssid =
                    result.BSSID
                        ?.takeIf {
                            it.isNotBlank()
                        }
                        ?: return@mapNotNull null

                val sourceId =
                    privacyHasher.token(
                        namespace =
                            "wifi",

                        rawIdentifier =
                            bssid.lowercase(),
                    )

                val timestamp =
                    result.timestamp
                        .takeIf {
                            it > 0L
                        }
                        ?.let {
                            it *
                                1_000L
                        }
                        ?: now

                NavigationRadioObservation(
                    technology =
                        NavigationRadioTechnology.WifiScan,

                    sourceId =
                        sourceId,

                    rssiDbm =
                        result.level,

                    frequencyMhz =
                        result.frequency
                            .takeIf {
                                it > 0
                            },

                    distanceM =
                        null,

                    distanceStdDevM =
                        null,

                    elapsedRealtimeNanos =
                        timestamp,
                )
            }

        synchronized(
            lock
        ) {
            latestWifiById.clear()

            mapped.forEach {
                latestWifiById[
                    it.sourceId
                ] =
                    it
            }
        }

        return mapped.size
    }

    @SuppressLint("MissingPermission")
    fun startBluetoothLeObservation():
        Boolean {

        stopBluetoothLeObservation()

        if (
            !hasBluetoothLocationPermissions(
                appContext
            )
        ) {
            return false
        }

        val scanner =
            try {
                bluetoothManager
                    .adapter
                    ?.bluetoothLeScanner
            } catch (
                _: SecurityException
            ) {
                null
            }
                ?: return false

        val settings =
            ScanSettings
                .Builder()
                .setScanMode(
                    ScanSettings
                        .SCAN_MODE_LOW_LATENCY
                )
                .build()

        return try {
            scanner.startScan(
                null,
                settings,
                bleCallback,
            )

            bleStarted =
                true

            true
        } catch (
            _: SecurityException
        ) {
            false
        } catch (
            _: IllegalStateException
        ) {
            false
        }
    }

    @SuppressLint("MissingPermission")
    fun stopBluetoothLeObservation() {
        if (
            bleStarted
        ) {
            runCatching {
                bluetoothManager
                    .adapter
                    ?.bluetoothLeScanner
                    ?.stopScan(
                        bleCallback
                    )
            }
        }

        bleStarted =
            false
    }

    @SuppressLint("NewApi")
    fun requestWifiRttObservation(
        onUpdated:
            (Int) -> Unit = {},
    ): Boolean {

        if (
            Build.VERSION.SDK_INT <
                Build.VERSION_CODES.P
        ) {
            return false
        }

        val source =
            AndroidWifiRttObservationSource(
                context =
                    appContext,

                privacyHasher =
                    privacyHasher,
            )

        return source.request {
                observations ->

            synchronized(
                lock
            ) {
                latestWifiRttById.clear()

                observations.forEach {
                    latestWifiRttById[
                        it.sourceId
                    ] =
                        it
                }
            }

            onUpdated(
                observations.size
            )
        }
    }

    fun snapshot(
        knowledge:
            NavigationSatelliteKnowledgeView,
    ): NavigationEnvironmentEvidenceSnapshot {

        val now =
            SystemClock
                .elapsedRealtimeNanos()

        val satellites:
            List<NavigationObservedSatellite>

        val radios:
            List<NavigationRadioObservation>

        synchronized(
            lock
        ) {
            satellites =
                latestSatellites
                    .filter {
                        isRecent(
                            now =
                                now,

                            timestamp =
                                it.elapsedRealtimeNanos,

                            maximumAgeNanos =
                                SATELLITE_MAX_AGE_NANOS,
                        )
                    }
                    .toList()

            radios =
                (
                    latestWifiById.values +
                        latestBleById.values +
                        latestWifiRttById.values
                )
                    .filter {
                        isRecent(
                            now =
                                now,

                            timestamp =
                                it.elapsedRealtimeNanos,

                            maximumAgeNanos =
                                RADIO_MAX_AGE_NANOS,
                        )
                    }
                    .toList()
        }

        return NavigationEnvironmentEvidenceSnapshot(
            observedSatellites =
                satellites,

            radioObservations =
                radios,

            satelliteCatalogVersion =
                knowledge.catalogVersion(),

            unknownSatelliteKeys =
                unknownSatelliteKeys(
                    observedSatellites =
                        satellites,

                    knowledge =
                        knowledge,
                ),

            capturedAtElapsedRealtimeNanos =
                now,
        )
    }

    fun clear() {
        synchronized(
            lock
        ) {
            latestSatellites =
                emptyList()

            latestWifiById.clear()
            latestBleById.clear()
            latestWifiRttById.clear()
        }
    }

    fun stopAll() {
        stopGnssSatelliteObservation()
        stopBluetoothLeObservation()
    }

    private fun captureBleResult(
        result:
            BluetoothScanResult,
    ) {
        val address =
            runCatching {
                result
                    .device
                    .address
            }.getOrNull()
                ?.takeIf {
                    it.isNotBlank()
                }
                ?: return

        val sourceId =
            privacyHasher.token(
                namespace =
                    "ble",

                rawIdentifier =
                    address.lowercase(),
            )

        val timestamp =
            result.timestampNanos
                .takeIf {
                    it > 0L
                }
                ?: SystemClock
                    .elapsedRealtimeNanos()

        val observation =
            NavigationRadioObservation(
                technology =
                    NavigationRadioTechnology.BluetoothLe,

                sourceId =
                    sourceId,

                rssiDbm =
                    result.rssi,

                frequencyMhz =
                    null,

                distanceM =
                    null,

                distanceStdDevM =
                    null,

                elapsedRealtimeNanos =
                    timestamp,
            )

        synchronized(
            lock
        ) {
            latestBleById[
                sourceId
            ] =
                observation

            while (
                latestBleById.size >
                    MAX_BLE_IDENTIFIERS
            ) {
                val oldestKey =
                    latestBleById
                        .entries
                        .minByOrNull {
                            it.value
                                .elapsedRealtimeNanos
                        }
                        ?.key
                        ?: break

                latestBleById.remove(
                    oldestKey
                )
            }
        }
    }
}

private fun hasFineLocationPermission(
    context: Context,
): Boolean =
    context.checkSelfPermission(
        Manifest.permission.ACCESS_FINE_LOCATION
    ) ==
        PackageManager.PERMISSION_GRANTED

private fun hasBluetoothLocationPermissions(
    context: Context,
): Boolean {

    if (
        !hasFineLocationPermission(
            context
        )
    ) {
        return false
    }

    if (
        Build.VERSION.SDK_INT <
            Build.VERSION_CODES.S
    ) {
        return true
    }

    return context.checkSelfPermission(
        Manifest.permission.BLUETOOTH_SCAN
    ) ==
        PackageManager.PERMISSION_GRANTED
}

private fun mapAndroidConstellation(
    androidConstellation: Int,
): NavigationGnssConstellation =
    when (
        androidConstellation
    ) {
        /*
         * Values correspond to GnssStatus CONSTELLATION_*.
         * Numeric mapping keeps IRNSS safe on API 26-28 too.
         */
        1 ->
            NavigationGnssConstellation.Gps

        2 ->
            NavigationGnssConstellation.Sbas

        3 ->
            NavigationGnssConstellation.Glonass

        4 ->
            NavigationGnssConstellation.Qzss

        5 ->
            NavigationGnssConstellation.Beidou

        6 ->
            NavigationGnssConstellation.Galileo

        7 ->
            NavigationGnssConstellation.Irnss

        else ->
            NavigationGnssConstellation.Unknown
    }

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

private fun isRecent(
    now: Long,
    timestamp: Long,
    maximumAgeNanos: Long,
): Boolean {

    if (
        timestamp >
            now
    ) {
        return false
    }

    return now -
        timestamp <=
            maximumAgeNanos
}

private fun newPrivacyKey():
    ByteArray =
    ByteArray(
        32
    ).also {
        SecureRandom()
            .nextBytes(
                it
            )
    }

private const val MAX_BLE_IDENTIFIERS =
    512

private const val SATELLITE_MAX_AGE_NANOS =
    15_000_000_000L

private const val RADIO_MAX_AGE_NANOS =
    60_000_000_000L