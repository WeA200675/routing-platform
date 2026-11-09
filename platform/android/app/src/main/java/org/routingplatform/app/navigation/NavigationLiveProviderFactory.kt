package org.routingplatform.app.navigation

import android.content.Context

internal object NavigationLiveProviderFactory {
    fun fromManifest(context: Context): List<NavigationLiveEventProvider> {
        val config = NavigationLiveProviderConfig.fromManifest(context)
        return fromConfig(config, locationProvider = AndroidLastKnownRoutePointProvider(context)::current)
    }

    fun fromConfig(
        config: NavigationLiveProviderConfig,
        locationProvider: () -> RoutePoint? = { null },
    ): List<NavigationLiveEventProvider> {
        val headers = config.apiKey
            ?.let { mapOf("Authorization" to "Bearer $it") }
            .orEmpty()
        return buildList {
            config.trafficEndpoint?.let {
                add(HttpNavigationLiveEventProvider("traffic", it.toString(), headers, config.connectTimeoutMs, config.readTimeoutMs))
            }
            config.hazardEndpoint?.let {
                add(HttpNavigationLiveEventProvider("hazard", it.toString(), headers, config.connectTimeoutMs, config.readTimeoutMs))
            }
            config.weatherEndpoint?.let {
                if (it.host.equals("api.open-meteo.com", ignoreCase = true)) {
                    add(OpenMeteoLiveWeatherProvider(it, locationProvider, config.readTimeoutMs))
                } else {
                    add(HttpNavigationLiveEventProvider("weather", it.toString(), headers, config.connectTimeoutMs, config.readTimeoutMs))
                }
            }
            config.poiEndpoint?.let {
                add(HttpNavigationLiveEventProvider("poi", it.toString(), headers, config.connectTimeoutMs, config.readTimeoutMs))
            }
        }.map { provider ->
            ObservableNavigationLiveEventProvider(
                CachedNavigationLiveEventProvider(
                    delegate = provider,
                    maxAgeMs = config.cacheMaxAgeMs,
                ),
            )
        }
    }
}
