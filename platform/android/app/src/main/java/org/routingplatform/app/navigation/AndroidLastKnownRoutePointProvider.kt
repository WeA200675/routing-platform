package org.routingplatform.app.navigation

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager

internal class AndroidLastKnownRoutePointProvider(
    private val context: Context,
) {
    fun current(): RoutePoint? {
        val fine = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (fine != PackageManager.PERMISSION_GRANTED && coarse != PackageManager.PERMISSION_GRANTED) return null
        val manager = context.getSystemService(LocationManager::class.java) ?: return null
        val candidates = runCatching {
            listOfNotNull(
                manager.getLastKnownLocation(LocationManager.GPS_PROVIDER),
                manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER),
                manager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER),
            )
        }.getOrDefault(emptyList())
        return candidates
            .filter { it.latitude.isFinite() && it.longitude.isFinite() }
            .maxByOrNull { it.elapsedRealtimeNanos }
            ?.let { RoutePoint(it.latitude, it.longitude) }
    }
}
