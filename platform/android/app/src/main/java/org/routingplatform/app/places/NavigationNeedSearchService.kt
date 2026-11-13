package org.routingplatform.app.places

/** Bridges natural-language needs to ranked POI results and trip-stop actions. */
internal class NavigationNeedSearchService(
    private val coordinator: NavigationPoiSearchCoordinator,
) {
    fun search(
        text: String,
        nowEpochMs: Long,
    ): NavigationNeedSearchResult {
        val intent = NavigationNeedIntentParser.parse(text)
            ?: return NavigationNeedSearchResult.Invalid("Bitte beschreibe das gewÃ¼nschte Ziel.")
        val pois = coordinator.search(
            request = NavigationNeedIntentParser.toSearchRequest(intent),
            nowEpochMs = nowEpochMs,
        ).filter { poi ->
            (!intent.requireOpenNow || poi.openNow != false) &&
                (poi.detourSeconds == null || poi.detourSeconds <= intent.maxDetourSeconds)
        }
        return if (pois.isEmpty()) {
            NavigationNeedSearchResult.Empty(intent)
        } else {
            NavigationNeedSearchResult.Found(intent, pois)
        }
    }
}

internal sealed interface NavigationNeedSearchResult {
    data class Found(val intent: NavigationNeedIntent, val pois: List<NavigationPoi>) : NavigationNeedSearchResult
    data class Empty(val intent: NavigationNeedIntent) : NavigationNeedSearchResult
    data class Invalid(val reason: String) : NavigationNeedSearchResult
}

