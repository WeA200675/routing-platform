package org.routingplatform.app.ai

import org.routingplatform.app.navigation.NavigationRouteRequest
import org.routingplatform.app.navigation.RoutePoint
import org.routingplatform.app.places.DestinationSearchResult
import org.routingplatform.app.places.FavoriteDestinationCollection

sealed interface SocialAiProductionRoutingResult {
    data class Ready(val request: NavigationRouteRequest) : SocialAiProductionRoutingResult
    data class CategoryLookupRequired(val category: String) : SocialAiProductionRoutingResult
    data class ClarificationRequired(val reason: String) : SocialAiProductionRoutingResult
}

/**
 * Product-path orchestration for local Social AI.
 *
 * The model can only produce a validated symbolic intent. Coordinates always
 * originate from trusted favorites or deterministic search results.
 */
class SocialAiProductionRouting(
    private val engine: SocialAiNativeEngine,
) {
    private val parser = SocialAiRoutingIntentParser(engine)

    fun interpret(
        userText: String,
        origin: RoutePoint,
        favorites: FavoriteDestinationCollection,
        categoryResults: Map<String, List<DestinationSearchResult>> = emptyMap(),
    ): SocialAiProductionRoutingResult {
        val parsed = parser.parse(userText)
        if (parsed is SocialAiRoutingIntentResult.ClarificationRequired) {
            return SocialAiProductionRoutingResult.ClarificationRequired(parsed.reason)
        }
        val intent = (parsed as SocialAiRoutingIntentResult.Ready).intent
        if (intent.viaCategory != null && intent.viaCategory !in categoryResults) {
            return SocialAiProductionRoutingResult.CategoryLookupRequired(intent.viaCategory)
        }
        intent.viaCategory?.let { category ->
            val matches = categoryResults[category].orEmpty()
            if (matches.size != 1) {
                return SocialAiProductionRoutingResult.ClarificationRequired(
                    if (matches.isEmpty()) "Kein eindeutiger Zwischenstopp gefunden."
                    else "Mehrere Zwischenstopps gefunden. Bitte einen Treffer auswählen."
                )
            }
        }
        val bridge = SocialAiNavigationRequestBridge(
            parser = parser,
            placeResolver = SocialAiTrustedPlaceResolver(favorites, categoryResults),
        )
        return when (val built = bridge.buildResolved(origin, intent)) {
            is SocialAiNavigationRequestResult.Ready -> SocialAiProductionRoutingResult.Ready(built.request)
            is SocialAiNavigationRequestResult.ClarificationRequired ->
                SocialAiProductionRoutingResult.ClarificationRequired(built.reason)
        }
    }

}
