package org.routingplatform.app.ai

import org.routingplatform.app.navigation.RoutePoint
import org.routingplatform.app.places.DestinationSearchResult
import org.routingplatform.app.places.FavoriteDestinationCollection

class SocialAiTrustedPlaceResolver(
    private val favorites: FavoriteDestinationCollection,
    private val categoryResults: Map<String, List<DestinationSearchResult>> = emptyMap(),
) : SocialAiRoutingPlaceResolver {
    override fun resolveDestination(identifier: String): RoutePoint? =
        when (identifier) {
            "home" -> favorites.home?.point
            "work" -> favorites.work?.point
            else -> favorites.custom.singleOrNull { it.id == identifier }?.point
        }

    override fun resolveViaCategory(identifier: String): RoutePoint? {
        val candidates = categoryResults[identifier].orEmpty()
        // Never let the model choose among ambiguous geocoder/category results.
        return candidates.singleOrNull()?.point
    }
}
