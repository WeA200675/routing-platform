package org.routingplatform.app.ai

import org.routingplatform.app.navigation.NavigationRouteRequest
import org.routingplatform.app.navigation.RoutePoint
import org.routingplatform.app.places.DestinationSearchResult
import org.routingplatform.app.places.FavoriteDestinationCollection

sealed interface SocialAiProductionRoutingResult {
    data class Ready(val request: NavigationRouteRequest) : SocialAiProductionRoutingResult
    data class CategoryLookupRequired(
        val category: String,
        val validatedIntent: SocialAiRoutingIntent,
    ) : SocialAiProductionRoutingResult
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
        return resolveValidated(
            intent = intent,
            origin = origin,
            favorites = favorites,
            categoryResults = categoryResults,
        )
    }

    /**
     * Continues a routing request from an already validated symbolic intent.
     * This deliberately performs no model generation: deterministic category
     * lookup/selection must never cause the user's command to be reinterpreted.
     */
    fun resolveValidated(
        intent: SocialAiRoutingIntent,
        origin: RoutePoint,
        favorites: FavoriteDestinationCollection,
        categoryResults: Map<String, List<DestinationSearchResult>> = emptyMap(),
    ): SocialAiProductionRoutingResult {
        if (intent.viaCategory != null && intent.viaCategory !in categoryResults) {
            return SocialAiProductionRoutingResult.CategoryLookupRequired(
                category = intent.viaCategory,
                validatedIntent = intent,
            )
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
