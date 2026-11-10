package org.routingplatform.app.vehicle

import android.content.Context

object EnergyPriceProviderFactory {
    fun fromManifest(context: Context): EnergyPriceAvailabilityProvider? {
        val config = EnergyPriceProviderConfig.fromManifest(context)
        val fuel = config.fuelEndpoint
        val tanker = config.tankerkoenigApiKey?.let { TankerkoenigPriceProvider(it) }
        val charging = config.chargingEndpoint
        if (fuel == null && charging == null) return null
        return object : EnergyPriceAvailabilityProvider {
            override fun lookup(
                poi: org.routingplatform.app.navigation.NavigationPoiRecommendation,
                kind: EnergyStopKind,
            ): EnergyPriceAvailability? {
                val endpoint = if (kind == EnergyStopKind.FUEL) fuel else charging
                return if (kind == EnergyStopKind.FUEL && tanker != null) {
                    tanker.lookup(poi, kind)
                } else {
                    endpoint?.let { HttpEnergyPriceAvailabilityProvider(it).lookup(poi, kind) }
                }
            }
        }
    }
}
