package org.routingplatform.app.navigation

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import android.os.SystemClock

fun hasNavigationLocationPermission(
    context: Context,
): Boolean =
    context.checkSelfPermission(
        Manifest.permission.ACCESS_FINE_LOCATION
    ) ==
        PackageManager.PERMISSION_GRANTED ||
        context.checkSelfPermission(
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) ==
        PackageManager.PERMISSION_GRANTED

class AndroidLocationSource(
    context: Context,
) :
    NavigationLocationSource,
    LocationListener {

    private val appContext =
        context.applicationContext

    private val locationManager =
        appContext.getSystemService(
            Context.LOCATION_SERVICE
        ) as LocationManager

    private var onLocation:
        ((NavigationLocationSample) -> Unit)? =
        null

    @SuppressLint("MissingPermission")
    override fun start(
        onLocation:
            (NavigationLocationSample) -> Unit,
    ): Boolean {

        stop()

        if (
            !hasNavigationLocationPermission(
                appContext
            )
        ) {
            return false
        }

        val enabledProviders =
            runCatching {
                locationManager
                    .getProviders(true)
                    .toSet()
            }.getOrDefault(emptySet())

        val providers =
            selectNavigationProviders(
                apiLevel = Build.VERSION.SDK_INT,
                enabledProviders = enabledProviders,
            )

        if (providers.isEmpty()) {
            return false
        }

        this.onLocation =
            onLocation

        var registeredAnyProvider =
            false

        providers.forEach { provider ->
            val requestIntervalMs =
                if (provider != LocationManager.NETWORK_PROVIDER) {
                    GPS_UPDATE_INTERVAL_MS
                } else {
                    NETWORK_UPDATE_INTERVAL_MS
                }

            val registered =
                try {
                    locationManager.requestLocationUpdates(
                        provider,
                        requestIntervalMs,
                        MIN_UPDATE_DISTANCE_M,
                        this,
                        Looper.getMainLooper(),
                    )
                    true
                } catch (_: SecurityException) {
                    false
                } catch (_: IllegalArgumentException) {
                    false
                }

            registeredAnyProvider =
                registeredAnyProvider || registered
        }

        if (!registeredAnyProvider) {
            stop()
        }

        return registeredAnyProvider
    }

    override fun stop() {
        runCatching {
            locationManager
                .removeUpdates(
                    this
                )
        }

        onLocation =
            null
    }

    override fun onLocationChanged(
        location: Location,
    ) {
        val nowElapsedRealtimeNanos =
            SystemClock.elapsedRealtimeNanos()

        val fixElapsedRealtimeNanos =
            location.elapsedRealtimeNanos

        if (
            !isNavigationLocationFixFresh(
                fixElapsedRealtimeNanos = fixElapsedRealtimeNanos,
                nowElapsedRealtimeNanos = nowElapsedRealtimeNanos,
            )
        ) {
            return
        }

        val latitude =
            location.latitude

        val longitude =
            location.longitude

        if (
            !latitude.isFinite() ||
            latitude !in -90.0..90.0 ||
            !longitude.isFinite() ||
            longitude !in -180.0..180.0
        ) {
            return
        }

        val horizontalAccuracyM =
            if (
                location.hasAccuracy() &&
                location.accuracy.isFinite() &&
                location.accuracy >= 0.0f
            ) {
                location
                    .accuracy
                    .toDouble()
            } else {
                null
            }

        onLocation?.invoke(
            NavigationLocationSample(
                position =
                    RoutePoint(
                        latitude =
                            latitude,

                        longitude =
                            longitude,
                    ),

                horizontalAccuracyM =
                    horizontalAccuracyM,

                elapsedRealtimeNanos =
                    location
                        .elapsedRealtimeNanos,

                provider =
                    location.provider,

                speedMps =
                    location.speed
                        .toDouble()
                        .takeIf { location.hasSpeed() && it.isFinite() && it >= 0.0 },

                speedAccuracyMps =
                    if (android.os.Build.VERSION.SDK_INT >= 26 && location.hasSpeedAccuracy()) {
                        location.speedAccuracyMetersPerSecond
                            .toDouble()
                            .takeIf { it.isFinite() && it >= 0.0 }
                    } else null,

                bearingDegrees =
                    location.bearing
                        .toDouble()
                        .takeIf { location.hasBearing() && it.isFinite() && it >= 0.0 && it < 360.0 },

                bearingAccuracyDegrees =
                    if (android.os.Build.VERSION.SDK_INT >= 26 && location.hasBearingAccuracy()) {
                        location.bearingAccuracyDegrees
                            .toDouble()
                            .takeIf { it.isFinite() && it >= 0.0 }
                    } else null,
            )
        )
    }
}

private const val GPS_UPDATE_INTERVAL_MS =
    500L

private const val NETWORK_UPDATE_INTERVAL_MS =
    1000L

private const val MIN_UPDATE_DISTANCE_M =
    0.0f

internal fun selectNavigationProviders(
    apiLevel: Int,
    enabledProviders: Set<String>,
    fusedProvider: String = "fused",
    gpsProvider: String = LocationManager.GPS_PROVIDER,
    networkProvider: String = LocationManager.NETWORK_PROVIDER,
): List<String> {
    if (
        apiLevel >= Build.VERSION_CODES.S &&
        fusedProvider in enabledProviders
    ) {
        return listOf(fusedProvider)
    }

    return listOf(gpsProvider, networkProvider)
        .filter { it in enabledProviders }
}

private const val MAX_LOCATION_FIX_AGE_NANOS =
    10_000_000_000L

private const val MAX_FUTURE_FIX_SKEW_NANOS =
    1_000_000_000L

internal fun isNavigationLocationFixFresh(
    fixElapsedRealtimeNanos: Long,
    nowElapsedRealtimeNanos: Long,
    maximumAgeNanos: Long = MAX_LOCATION_FIX_AGE_NANOS,
    maximumFutureSkewNanos: Long = MAX_FUTURE_FIX_SKEW_NANOS,
): Boolean =
    fixElapsedRealtimeNanos > 0L &&
        nowElapsedRealtimeNanos >= 0L &&
        maximumAgeNanos >= 0L &&
        maximumFutureSkewNanos >= 0L &&
        fixElapsedRealtimeNanos <= nowElapsedRealtimeNanos + maximumFutureSkewNanos &&
        nowElapsedRealtimeNanos - fixElapsedRealtimeNanos <= maximumAgeNanos
