package org.routingplatform.app.navigation

import android.content.Context

internal object NavigationLiveProviderFactory {
    fun fromManifest(context: Context): List<NavigationLiveEventProvider> {
        val config = NavigationLiveProviderConfig.fromManifest(context)
        return fromConfig(config)
    }

    fun fromConfig(
        config: NavigationLiveProviderConfig,
    ): List<NavigationLiveEventProvider> {
        val headers = config.apiKey
            ?.let { mapOf("Authorization" to "Bearer $it") }
            .orEmpty()
        return buildList {
            config.trafficEndpoint?.let {
                add(HttpNavigationLiveEventProvider(
                    providerId = "traffic",
                    endpoint = it.toString(),
                    headers = headers,
                    connectTimeoutMs = config.connectTimeoutMs,
                    readTimeoutMs = config.readTimeoutMs,
                ))
            }
            config.hazardEndpoint?.let {
                add(HttpNavigationLiveEventProvider(
                    providerId = "hazard",
                    endpoint = it.toString(),
                    headers = headers,
                    connectTimeoutMs = config.connectTimeoutMs,
                    readTimeoutMs = config.readTimeoutMs,
                ))
            }
            config.weatherEndpoint?.let {
                add(HttpNavigationLiveEventProvider(
                    providerId = "weather",
                    endpoint = it.toString(),
                    headers = headers,
                    connectTimeoutMs = config.connectTimeoutMs,
                    readTimeoutMs = config.readTimeoutMs,
                ))
            }
            config.poiEndpoint?.let {
                add(HttpNavigationLiveEventProvider(
                    providerId = "poi",
                    endpoint = it.toString(),
                    headers = headers,
                    connectTimeoutMs = config.connectTimeoutMs,
                    readTimeoutMs = config.readTimeoutMs,
                ))
            }
        }.map { provider ->
            CachedNavigationLiveEventProvider(
                delegate = provider,
                maxAgeMs = config.cacheMaxAgeMs,
            )
        }
    }
}
