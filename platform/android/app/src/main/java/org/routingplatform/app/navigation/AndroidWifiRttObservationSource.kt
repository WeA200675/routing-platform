package org.routingplatform.app.navigation

import android.Manifest
import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.net.wifi.rtt.RangingRequest
import android.net.wifi.rtt.RangingResult
import android.net.wifi.rtt.RangingResultCallback
import android.net.wifi.rtt.WifiRttManager
import android.net.wifi.rtt.ResponderLocation
import android.os.Build
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sqrt

@TargetApi(
    Build.VERSION_CODES.P
)
class AndroidWifiRttObservationSource(
    context: Context,
    private val privacyHasher:
        NavigationPrivacyIdentifierHasher,
) {
    private val appContext =
        context.applicationContext

    private val wifiManager =
        appContext.getSystemService(
            Context.WIFI_SERVICE
        ) as WifiManager

    private val rttManager =
        appContext.getSystemService(
            Context.WIFI_RTT_RANGING_SERVICE
        ) as?
            WifiRttManager

    @SuppressLint("MissingPermission")
    fun request(
        onResult:
            (
                List<
                    NavigationRadioObservation
                >
            ) -> Unit,
    ): Boolean {

        if (
            !hasRequiredPermissions()
        ) {
            return false
        }

        val manager =
            rttManager
                ?: return false

        if (
            !manager.isAvailable
        ) {
            return false
        }

        val scanResults =
            try {
                wifiManager.scanResults
            } catch (
                _: SecurityException
            ) {
                return false
            }

        val responders =
            scanResults
                .filter {
                    it.is80211mcResponder
                }
                .take(
                    RangingRequest
                        .getMaxPeers()
                )

        if (
            responders.isEmpty()
        ) {
            return false
        }

        val builder =
            RangingRequest.Builder()

        responders.forEach {
            builder.addAccessPoint(
                it
            )
        }

        return try {
            manager.startRanging(
                builder.build(),
                appContext.mainExecutor,
                object :
                    RangingResultCallback() {

                    override fun onRangingFailure(
                        code: Int,
                    ) {
                        onResult(
                            emptyList()
                        )
                    }

                    override fun onRangingResults(
                        results:
                            MutableList<
                                RangingResult
                            >,
                    ) {
                        val observations =
                            results.mapNotNull {
                                    result ->

                                if (
                                    result.status !=
                                        RangingResult.STATUS_SUCCESS
                                ) {
                                    return@mapNotNull null
                                }

                                val mac =
                                    result.macAddress
                                        ?.toString()
                                        ?.takeIf {
                                            it.isNotBlank()
                                        }
                                        ?: return@mapNotNull null

                                val sourceId =
                                    privacyHasher.token(
                                        namespace =
                                            "wifi-rtt",

                                        rawIdentifier =
                                            mac.lowercase(),
                                    )

                                val distanceM =
                                    (
                                        result.distanceMm
                                            .toDouble() /
                                            1000.0
                                    ).coerceAtLeast(
                                        0.0
                                    )

                                val standardDeviationM =
                                    runCatching {
                                        result
                                            .distanceStdDevMm
                                            .toDouble() /
                                            1000.0
                                    }.getOrNull()
                                        ?.takeIf { it.isFinite() && it >= 0.0 }

                                val anchor =
                                    responderAnchor(result)

                                NavigationRadioObservation(
                                    technology =
                                        NavigationRadioTechnology.WifiRtt,

                                    sourceId =
                                        sourceId,

                                    rssiDbm =
                                        result.rssi,

                                    frequencyMhz =
                                        null,

                                    distanceM =
                                        distanceM,

                                    distanceStdDevM =
                                        standardDeviationM,

                                    elapsedRealtimeNanos =
                                        result
                                            .rangingTimestampMillis *
                                            1_000_000L,

                                    anchorPosition =
                                        anchor?.first,

                                    anchorPositionUncertaintyM =
                                        anchor?.second,
                                )
                            }

                        onResult(
                            observations
                        )
                    }
                },
            )

            true
        } catch (
            _: SecurityException
        ) {
            false
        } catch (
            _: IllegalArgumentException
        ) {
            false
        } catch (
            _: IllegalStateException
        ) {
            false
        }
    }

    private fun hasRequiredPermissions():
        Boolean {

        if (
            appContext.checkSelfPermission(
                Manifest.permission.ACCESS_FINE_LOCATION
            ) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }

        if (
            Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.TIRAMISU &&
            appContext.checkSelfPermission(
                Manifest.permission.NEARBY_WIFI_DEVICES
            ) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }

        return true
    }
}

/*
 * Android exposes the AP-supplied location starting at API 35.
 * This value is explicitly unverified, so it is only a candidate anchor.
 */
private fun responderAnchor(
    result: RangingResult,
): Pair<RoutePoint, Double>? {
    if (Build.VERSION.SDK_INT < 35) return null

    return runCatching {
        val responder =
            result.getUnverifiedResponderLocation()
                ?: return null

        if (
            !responder.isLciSubelementValid() ||
            responder.getDatum() != ResponderLocation.DATUM_WGS84
        ) {
            return null
        }

        if (
            !responder.isZaxisSubelementValid() ||
            responder.getExpectedToMove() != ResponderLocation.LOCATION_FIXED
        ) {
            return null
        }

        val latitude = responder.getLatitude()
        val longitude = responder.getLongitude()
        val latitudeUncertaintyDegrees = responder.getLatitudeUncertainty()
        val longitudeUncertaintyDegrees = responder.getLongitudeUncertainty()

        if (
            !latitude.isFinite() ||
            latitude !in -90.0..90.0 ||
            !longitude.isFinite() ||
            longitude !in -180.0..180.0 ||
            !latitudeUncertaintyDegrees.isFinite() ||
            latitudeUncertaintyDegrees <= 0.0 ||
            !longitudeUncertaintyDegrees.isFinite() ||
            longitudeUncertaintyDegrees <= 0.0
        ) {
            return null
        }

        val latitudeUncertaintyM =
            latitudeUncertaintyDegrees * METERS_PER_DEGREE
        val longitudeUncertaintyM =
            longitudeUncertaintyDegrees *
                METERS_PER_DEGREE *
                cos(latitude * PI / 180.0)
        val uncertaintyM =
            sqrt(
                latitudeUncertaintyM * latitudeUncertaintyM +
                    longitudeUncertaintyM * longitudeUncertaintyM
            )

        if (!uncertaintyM.isFinite() || uncertaintyM !in 0.1..5.0) {
            return null
        }

        RoutePoint(latitude, longitude) to uncertaintyM
    }.getOrNull()
}

private const val METERS_PER_DEGREE =
    111_320.0
