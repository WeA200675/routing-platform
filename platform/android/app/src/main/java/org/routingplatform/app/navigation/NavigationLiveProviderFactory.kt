package org.routingplatform.app.navigation

import android.content.Context

internal object NavigationLiveProviderFactory {
    fun fromManifest(context: Context): List<NavigationLiveEventProvider> {
        val config = NavigationLiveProviderConfig.fromManifest(context)
        return fromConfig(
            config,
            locationProvider = AndroidLastKnownRoutePointProvider(context)::current,
            diagnosticStore = NavigationDiagnosticsStore(context),
        )
    }

    fun fromConfig(
        config: NavigationLiveProviderConfig,
        locationProvider: () -> RoutePoint? = { null },
        diagnosticStore: NavigationDiagnosticsStore? = null,
        telemetryStore: NavigationLiveProviderTelemetryStore? = null,
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
                if (it.host.equals("overpass-api.de", ignoreCase = true)) {
                    add(OverpassPoiProvider(it, locationProvider, config.readTimeoutMs))
                } else {
                    add(HttpNavigationLiveEventProvider("poi", it.toString(), headers, config.connectTimeoutMs, config.readTimeoutMs))
                }
            }
        }.map { provider ->
            ObservableNavigationLiveEventProvider(
                CachedNavigationLiveEventProvider(
                    delegate = InstrumentedNavigationLiveEventProvider(
                        delegate = provider,
                        onObservation = { observation -> telemetryStore?.record(observation) },
                        diagnosticStore = diagnosticStore,
                    ),
                    maxAgeMs = config.cacheMaxAgeMs,
                ),
            )
        }
    }
}
