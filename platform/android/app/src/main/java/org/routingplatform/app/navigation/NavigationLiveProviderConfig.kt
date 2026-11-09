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
    val discoveryManifestKeyId: String? = null,
    val discoveryTrustedManifestKeys: Set<String> = emptySet(),
    val discoveryTrustedPublicKeys: Map<String, String> = emptyMap(),
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
        trafficEndpoint != null || hazardEndpoint != null || weatherEndpoint != null || poiEndpoint != null

    fun providerHosts(): Set<String> =
        listOf(trafficEndpoint, hazardEndpoint, weatherEndpoint, poiEndpoint)
            .filterNotNull()
            .mapNotNull { it.host?.lowercase() }
            .toSet()

    companion object {
        const val TRAFFIC_KEY = "org.routingplatform.live.TRAFFIC_ENDPOINT"
        const val HAZARD_KEY = "org.routingplatform.live.HAZARD_ENDPOINT"
        const val WEATHER_KEY = "org.routingplatform.live.WEATHER_ENDPOINT"
        const val POI_KEY = "org.routingplatform.live.POI_ENDPOINT"
        const val API_KEY = "org.routingplatform.live.API_KEY"
        const val DISCOVERY_KEY_ID = "org.routingplatform.discovery.MANIFEST_KEY_ID"
        const val DISCOVERY_TRUSTED_KEYS = "org.routingplatform.discovery.TRUSTED_MANIFEST_KEYS"
        const val DISCOVERY_PUBLIC_KEYS = "org.routingplatform.discovery.TRUSTED_PUBLIC_KEYS"

        fun fromManifest(context: Context): NavigationLiveProviderConfig {
            val info = context.packageManager.getApplicationInfo(
                context.packageName,
                PackageManager.GET_META_DATA,
            )
            val meta = info.metaData
            fun uri(key: String): URI? =
                meta?.getString(key)?.trim()?.takeIf { it.isNotEmpty() }?.let(::URI)
            fun csv(key: String): Set<String> =
                meta?.getString(key)
                    ?.split(',', ';', ' ', '\n')
                    ?.map { it.trim() }
                    ?.filter { it.isNotEmpty() }
                    ?.toSet()
                    .orEmpty()
            fun keyMap(key: String): Map<String, String> =
                meta?.getString(key)?.split(',', ';')?.mapNotNull { entry ->
                    val parts = entry.split('=', limit = 2).map { it.trim() }
                    if (parts.size == 2 && parts[0].isNotEmpty() && parts[1].isNotEmpty()) parts[0] to parts[1] else null
                }?.toMap().orEmpty()
            return NavigationLiveProviderConfig(
                trafficEndpoint = uri(TRAFFIC_KEY),
                hazardEndpoint = uri(HAZARD_KEY),
                weatherEndpoint = uri(WEATHER_KEY),
                poiEndpoint = uri(POI_KEY),
                apiKey = meta?.getString(API_KEY)?.trim()?.takeIf { it.isNotEmpty() },
                discoveryManifestKeyId = meta?.getString(DISCOVERY_KEY_ID)?.trim()?.takeIf { it.isNotEmpty() },
                discoveryTrustedManifestKeys = csv(DISCOVERY_TRUSTED_KEYS),
                discoveryTrustedPublicKeys = keyMap(DISCOVERY_PUBLIC_KEYS),
            )
        }
    }
}
