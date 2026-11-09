package org.routingplatform.app.navigation

import android.content.Context
import org.routingplatform.app.intelligence.DiscoveryCandidate
import org.routingplatform.app.intelligence.DiscoveryCandidateKind
import org.routingplatform.app.intelligence.ProductionDiscoveryController
import java.net.URI

internal object GovernedNavigationLiveProviderFactory {
    fun fromManifest(
        context: Context,
        controller: ProductionDiscoveryController,
        manifestKeyId: String?,
        signatureVerifier: (List<NavigationLiveEvent>) -> Boolean = { false },
    ): List<NavigationLiveEventProvider> {
        val config = NavigationLiveProviderConfig.fromManifest(context)
        val headers = config.apiKey
            ?.let { mapOf("Authorization" to "Bearer $it") }
            .orEmpty()
        val locationProvider = AndroidLastKnownRoutePointProvider(context)::current
        val endpoints = listOfNotNull(
            config.trafficEndpoint?.let { "traffic" to (it to DiscoveryCandidateKind.LIVE_PROVIDER) },
            config.hazardEndpoint?.let { "hazard" to (it to DiscoveryCandidateKind.LIVE_PROVIDER) },
            config.weatherEndpoint?.let { "weather" to (it to DiscoveryCandidateKind.WEATHER_SOURCE) },
            config.poiEndpoint?.let { "poi" to (it to DiscoveryCandidateKind.POI_SOURCE) },
        )
        return endpoints.map { (id, pair) ->
            val endpoint = pair.first
            val raw: NavigationLiveEventProvider = if (id == "weather" && endpoint.host.equals("api.open-meteo.com", ignoreCase = true)) {
                OpenMeteoLiveWeatherProvider(endpoint, locationProvider, config.readTimeoutMs)
            } else if (id == "poi" && endpoint.host.equals("overpass-api.de", ignoreCase = true)) {
                OverpassPoiProvider(endpoint, locationProvider, config.readTimeoutMs)
            } else {
                HttpNavigationLiveEventProvider(
                    providerId = id,
                    endpoint = endpoint.toString(),
                    headers = headers,
                    connectTimeoutMs = config.connectTimeoutMs,
                    readTimeoutMs = config.readTimeoutMs,
                )
            }
            GovernedNavigationLiveEventProvider(
                delegate = CachedNavigationLiveEventProvider(raw, config.cacheMaxAgeMs),
                controller = controller,
                candidate = DiscoveryCandidate(
                    id = id,
                    kind = pair.second,
                    endpoint = URI(endpoint.toString()),
                    source = id,
                    discoveredAtEpochMs = System.currentTimeMillis(),
                    evidence = listOf("manifest-configured"),
                    confidence = 1.0,
                ),
                manifestKeyId = manifestKeyId,
                signatureVerifier = signatureVerifier,
            )
        }
    }
}
