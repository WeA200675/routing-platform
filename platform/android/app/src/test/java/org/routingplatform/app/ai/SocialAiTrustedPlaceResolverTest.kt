package org.routingplatform.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.routingplatform.app.navigation.RoutePoint
import org.routingplatform.app.places.DestinationSearchResult
import org.routingplatform.app.places.FavoriteDestinationCollection

class SocialAiTrustedPlaceResolverTest {
    private val home = RoutePoint(48.1, 11.5)
    private val market = RoutePoint(48.2, 11.6)

    @Test fun resolvesSavedHomeAndSingleCategoryCandidate() {
        val resolver = SocialAiTrustedPlaceResolver(
            favorites = FavoriteDestinationCollection.empty("default").withHome(home),
            categoryResults = mapOf(
                "supermarket" to listOf(
                    DestinationSearchResult("market-1", "Supermarkt", null, market)
                )
            ),
        )
        assertEquals(home, resolver.resolveDestination("home"))
        assertEquals(market, resolver.resolveViaCategory("supermarket"))
    }

    @Test fun ambiguousOrMissingPlacesFailClosed() {
        val candidate = DestinationSearchResult("market-1", "A", null, market)
        val other = DestinationSearchResult("market-2", "B", null, RoutePoint(48.3, 11.7))
        val resolver = SocialAiTrustedPlaceResolver(
            favorites = FavoriteDestinationCollection.empty("default"),
            categoryResults = mapOf("supermarket" to listOf(candidate, other)),
        )
        assertNull(resolver.resolveDestination("home"))
        assertNull(resolver.resolveViaCategory("supermarket"))
    }
}
