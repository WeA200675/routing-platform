package org.routingplatform.app.navigation

import android.content.Context
import android.content.pm.PackageManager
import java.net.URI

data class NavigationLiveProviderConfig(
    val trafficEndpoint: URI? = null,
    val hazardEndpoint: URI? = null,
    val weatherEndpoint: URI? = null,
    val poiEndpoint: URI? = null,
    val apiKey: String? = null,
    val connectTimeoutMs: Int = 5_000,
    val readTimeoutMs: Int = 8_000,
    val cacheMaxAgeMs: Long = 15 * 60 * 1_000L,
) {
    init {
        require(connectTimeoutMs in 500..30_000)
        require(readTimeoutMs in 500..60_000)
        require(cacheMaxAgeMs in 1_000..86_400_000)
        listOf(trafficEndpoint, hazardEndpoint, weatherEndpoint, poiEndpoint)
            .filterNotNull()
            .forEach { endpoint ->
                require(endpoint.scheme == "https") { "Live provider endpoints must use HTTPS." }
                require(endpoint.host != null) { "Live provider endpoint requires a host." }
            }
    }

    fun hasAnyProvider(): Boolean =
        trafficEndpoint != null ||
            hazardEndpoint != null ||
            weatherEndpoint != null ||
            poiEndpoint != null

    companion object {
        const val TRAFFIC_KEY = "org.routingplatform.live.TRAFFIC_ENDPOINT"
        const val HAZARD_KEY = "org.routingplatform.live.HAZARD_ENDPOINT"
        const val WEATHER_KEY = "org.routingplatform.live.WEATHER_ENDPOINT"
        const val POI_KEY = "org.routingplatform.live.POI_ENDPOINT"
        const val API_KEY = "org.routingplatform.live.API_KEY"

        fun fromManifest(context: Context): NavigationLiveProviderConfig {
            val info = context.packageManager.getApplicationInfo(
                context.packageName,
                PackageManager.GET_META_DATA,
            )
            val meta = info.metaData
            fun uri(key: String): URI? =
                meta?.getString(key)?.trim()?.takeIf { it.isNotEmpty() }?.let(::URI)
            return NavigationLiveProviderConfig(
                trafficEndpoint = uri(TRAFFIC_KEY),
                hazardEndpoint = uri(HAZARD_KEY),
                weatherEndpoint = uri(WEATHER_KEY),
                poiEndpoint = uri(POI_KEY),
                apiKey = meta?.getString(API_KEY)?.trim()?.takeIf { it.isNotEmpty() },
            )
        }
    }
}
