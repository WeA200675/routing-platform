package org.routingplatform.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.routingplatform.app.navigation.RoutePoint
import org.routingplatform.app.places.DestinationSearchResult
import org.routingplatform.app.places.FavoriteDestinationCollection

class SocialAiProductionRoutingTest {
    @Test
    fun homeResolvesOnlyFromTrustedFavorites() {
        val routing = SocialAiProductionRouting(engine("DESTINATION=home\nAVOID=\nVIA="))
        val favorites = FavoriteDestinationCollection.empty("profile").withHome(RoutePoint(48.1, 11.5))
        val result = routing.interpret("Fahr mich nach Hause", RoutePoint(48.2, 11.6), favorites)
        assertTrue(result is SocialAiProductionRoutingResult.Ready)
        assertEquals(RoutePoint(48.1, 11.5), (result as SocialAiProductionRoutingResult.Ready).request.destination)
    }

    @Test
    fun viaRequiresDeterministicCategoryLookupBeforeRouting() {
        val routing = SocialAiProductionRouting(engine("DESTINATION=home\nAVOID=\nVIA=supermarket"))
        val favorites = FavoriteDestinationCollection.empty("profile").withHome(RoutePoint(48.1, 11.5))
        val result = routing.interpret("Nach Hause über Supermarkt", RoutePoint(48.2, 11.6), favorites)
        assertEquals(SocialAiProductionRoutingResult.CategoryLookupRequired("supermarket"), result)
    }

    @Test
    fun multipleViaMatchesRequireClarification() {
        val routing = SocialAiProductionRouting(engine("DESTINATION=home\nAVOID=\nVIA=supermarket"))
        val favorites = FavoriteDestinationCollection.empty("profile").withHome(RoutePoint(48.1, 11.5))
        val matches = listOf(
            DestinationSearchResult("a", "Markt A", null, RoutePoint(48.11, 11.51)),
            DestinationSearchResult("b", "Markt B", null, RoutePoint(48.12, 11.52)),
        )
        val result = routing.interpret(
            "Nach Hause über Supermarkt",
            RoutePoint(48.2, 11.6),
            favorites,
            mapOf("supermarket" to matches),
        )
        assertTrue(result is SocialAiProductionRoutingResult.ClarificationRequired)
    }

    @Test
    fun motorwayAvoidanceFailsClosed() {
        val routing = SocialAiProductionRouting(engine("DESTINATION=home\nAVOID=motorway\nVIA="))
        val favorites = FavoriteDestinationCollection.empty("profile").withHome(RoutePoint(48.1, 11.5))
        val result = routing.interpret("Nach Hause ohne Autobahn", RoutePoint(48.2, 11.6), favorites)
        assertTrue(result is SocialAiProductionRoutingResult.ClarificationRequired)
    }

    private fun engine(answer: String) = object : SocialAiNativeEngine {
        override val engineId = "fixture"
        override val artifactSha256 = "0".repeat(64)
        override fun loadModel(localPath: String, contextTokens: Int) = true
        override fun unloadModel() = Unit
        override fun generate(prompt: String, maximumOutputTokens: Int) = answer
    }
}
