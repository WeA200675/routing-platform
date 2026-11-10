package org.routingplatform.app.vehicle

import android.content.Context
import android.content.pm.PackageManager
import java.net.URI

data class EnergyPriceProviderConfig(
    val fuelEndpoint: URI? = null,
    val chargingEndpoint: URI? = null,
    val tankerkoenigApiKey: String? = null,
) {
    init {
        listOfNotNull(fuelEndpoint, chargingEndpoint).forEach {
            require(it.scheme == "https") { "Energy price providers must use HTTPS." }
            require(!it.host.isNullOrBlank()) { "Energy price provider requires a host." }
        }
    }

    companion object {
        const val FUEL_KEY = "org.routingplatform.energy.FUEL_PRICE_ENDPOINT"
        const val CHARGING_KEY = "org.routingplatform.energy.CHARGING_PRICE_ENDPOINT"
        const val TANKERKOENIG_KEY = "org.routingplatform.energy.TANKERKOENIG_API_KEY"

        fun fromManifest(context: Context): EnergyPriceProviderConfig {
            val info = context.packageManager.getApplicationInfo(
                context.packageName,
                PackageManager.GET_META_DATA,
            )
            val meta = info.metaData
            fun uri(key: String): URI? =
                meta?.getString(key)?.trim()?.takeIf { it.isNotEmpty() }?.let(::URI)
            return EnergyPriceProviderConfig(
                fuelEndpoint = uri(FUEL_KEY),
                chargingEndpoint = uri(CHARGING_KEY),
                tankerkoenigApiKey = meta?.getString(TANKERKOENIG_KEY)?.trim()?.takeIf { it.isNotEmpty() },
            )
        }
    }
}
