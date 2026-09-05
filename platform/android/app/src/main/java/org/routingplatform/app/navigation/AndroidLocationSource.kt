package org.routingplatform.app.navigation

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper

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
            listOf(
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER,
            ).filter { provider ->
                runCatching {
                    locationManager
                        .isProviderEnabled(
                            provider
                        )
                }.getOrDefault(
                    false
                )
            }

        if (enabledProviders.isEmpty()) {
            return false
        }

        this.onLocation =
            onLocation

        return try {
            enabledProviders.forEach {
                    provider ->

                locationManager
                    .requestLocationUpdates(
                        provider,
                        MIN_UPDATE_TIME_MS,
                        MIN_UPDATE_DISTANCE_M,
                        this,
                        Looper.getMainLooper(),
                    )
            }

            true
        } catch (
            _: SecurityException
        ) {
            stop()

            false
        } catch (
            _: IllegalArgumentException
        ) {
            stop()

            false
        }
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
            )
        )
    }
}

private const val MIN_UPDATE_TIME_MS =
    1000L

private const val MIN_UPDATE_DISTANCE_M =
    1.0f