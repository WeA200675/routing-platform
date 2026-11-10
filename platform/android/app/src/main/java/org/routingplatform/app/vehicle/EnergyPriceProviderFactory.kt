package org.routingplatform.app.vehicle

import android.content.Context

object EnergyPriceProviderFactory {
    fun fromManifest(context: Context): EnergyPriceAvailabilityProvider? {
        val config = EnergyPriceProviderConfig.fromManifest(context)
        val fuel = config.fuelEndpoint
        val charging = config.chargingEndpoint
        if (fuel == null && charging == null) return null
        return object : EnergyPriceAvailabilityProvider {
            override fun lookup(
                poi: org.routingplatform.app.navigation.NavigationPoiRecommendation,
                kind: EnergyStopKind,
            ): EnergyPriceAvailability? {
                val endpoint = if (kind == EnergyStopKind.FUEL) fuel else charging
                return endpoint?.let { HttpEnergyPriceAvailabilityProvider(it).lookup(poi, kind) }
            }
        }
    }
}
