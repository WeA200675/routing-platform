package org.routingplatform.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.routingplatform.app.navigation.RoutePoint
import org.routingplatform.app.places.DestinationSearchResult
import org.routingplatform.app.places.FavoriteDestinationCollection

class SocialAiProductionRoutingTest {
    @Test
    fun validatedContinuationDoesNotInvokeModelAgain() {
        var generations = 0
        val engine = object : SocialAiNativeEngine {
            override val engineId = "counting-fixture"
            override val artifactSha256 = "0".repeat(64)
            override fun loadModel(localPath: String, contextTokens: Int) = true
            override fun unloadModel() = Unit
            override fun generate(prompt: String, maximumOutputTokens: Int): String {
                generations += 1
                return "DESTINATION=home\nAVOID=\nVIA=supermarket"
            }
        }
        val routing = SocialAiProductionRouting(engine)
        val favorites =
            FavoriteDestinationCollection.empty("profile")
                .withHome(RoutePoint(48.1, 11.5))
        val origin = RoutePoint(48.2, 11.6)

        val lookup =
            routing.interpret(
                "Nach Hause über Supermarkt",
                origin,
                favorites,
            )
        assertTrue(
            lookup is
                SocialAiProductionRoutingResult.CategoryLookupRequired
        )
        assertEquals(
            "supermarket",
            (lookup as SocialAiProductionRoutingResult.CategoryLookupRequired)
                .category,
        )
        assertEquals(1, generations)

        val validated =
            lookup.validatedIntent
        val market =
            DestinationSearchResult(
                "market",
                "Markt",
                null,
                RoutePoint(48.15, 11.55),
            )
        val resolved =
            routing.resolveValidated(
                intent = validated,
                origin = origin,
                favorites = favorites,
                categoryResults = mapOf("supermarket" to listOf(market)),
            )

        assertTrue(resolved is SocialAiProductionRoutingResult.Ready)
        assertEquals(1, generations)
        val request =
            (resolved as SocialAiProductionRoutingResult.Ready).request
        assertEquals(RoutePoint(48.1, 11.5), request.destination)
        assertEquals(listOf(market.point), request.via)
    }

    @Test
    fun validatedContinuationFailsClosedForZeroOrMultipleCategoryMatches() {
        val routing =
            SocialAiProductionRouting(
                engine("DESTINATION=home\nAVOID=\nVIA=supermarket")
            )
        val favorites =
            FavoriteDestinationCollection.empty("profile")
                .withHome(RoutePoint(48.1, 11.5))
        val origin = RoutePoint(48.2, 11.6)
        val intent =
            SocialAiRoutingIntent(
                destination = "home",
                avoid = emptySet(),
                viaCategory = "supermarket",
            )

        val none =
            routing.resolveValidated(
                intent,
                origin,
                favorites,
                mapOf("supermarket" to emptyList()),
            )
        assertTrue(none is SocialAiProductionRoutingResult.ClarificationRequired)

        val multiple =
            routing.resolveValidated(
                intent,
                origin,
                favorites,
                mapOf(
                    "supermarket" to
                        listOf(
                            DestinationSearchResult(
                                "a", "A", null, RoutePoint(48.11, 11.51)
                            ),
                            DestinationSearchResult(
                                "b", "B", null, RoutePoint(48.12, 11.52)
                            ),
                        )
                ),
            )
        assertTrue(multiple is SocialAiProductionRoutingResult.ClarificationRequired)
    }

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
        assertTrue(result is SocialAiProductionRoutingResult.CategoryLookupRequired)
        assertEquals(
            "supermarket",
            (result as SocialAiProductionRoutingResult.CategoryLookupRequired).category,
        )
        assertEquals(
            SocialAiRoutingIntent(
                destination = "home",
                avoid = emptySet(),
                viaCategory = "supermarket",
            ),
            result.validatedIntent,
        )
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
