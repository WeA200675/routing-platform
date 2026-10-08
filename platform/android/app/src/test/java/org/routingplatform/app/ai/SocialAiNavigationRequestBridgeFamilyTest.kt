package org.routingplatform.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.routingplatform.app.navigation.NavigationRouteFamily
import org.routingplatform.app.navigation.RoutePoint

class SocialAiNavigationRequestBridgeFamilyTest {
    @Test
    fun selectedFamilyReachesTheValidatedRouteRequest() {
        val bridge = SocialAiNavigationRequestBridge(
            parser = SocialAiRoutingIntentParser(FakeEngine()),
            placeResolver = object : SocialAiRoutingPlaceResolver {
                override fun resolveDestination(identifier: String) =
                    if (identifier == "home") RoutePoint(1.0, 2.0) else null

                override fun resolveViaCategory(identifier: String): RoutePoint? = null
            },
        )

        for (family in NavigationRouteFamily.values()) {
            val result = bridge.buildResolved(
                origin = RoutePoint(3.0, 4.0),
                intent = SocialAiRoutingIntent(
                    destination = "home",
                    avoid = emptySet(),
                    viaCategory = null,
                ),
                family = family,
            )
            assertTrue(result is SocialAiNavigationRequestResult.Ready)
            assertEquals(family, (result as SocialAiNavigationRequestResult.Ready).request.family)
        }
    }

    private class FakeEngine : SocialAiNativeEngine {
        override val engineId: String = "test-engine"
        override val artifactSha256: String = "0".repeat(64)
        override fun loadModel(localPath: String, contextTokens: Int): Boolean = true
        override fun unloadModel() = Unit
        override fun generate(prompt: String, maximumOutputTokens: Int): String = ""
    }
}
