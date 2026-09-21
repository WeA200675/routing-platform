package org.routingplatform.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.routingplatform.app.navigation.RoutePoint

class SocialAiRoutingIntentParserTest {
    private fun parser(output: String) = SocialAiRoutingIntentParser(object : SocialAiNativeEngine {
        override val engineId = "test"
        override val artifactSha256 = "0".repeat(64)
        override fun loadModel(localPath: String, contextTokens: Int) = true
        override fun unloadModel() = Unit
        override fun generate(prompt: String, maximumOutputTokens: Int) = output
    })

    @Test fun parsesStrictRoutingIntent() {
        val result = parser("").parseStrict("DESTINATION=home\nAVOID=motorway\nVIA=supermarket")
        assertEquals(
            SocialAiRoutingIntentResult.Ready(
                SocialAiRoutingIntent("home", setOf(SocialAiRouteAvoidance.Motorway), "supermarket")
            ),
            result,
        )
    }

    @Test fun malformedAndUnknownValuesFailClosed() {
        assertTrue(parser("").parseStrict("Natürlich! DESTINATION=home") is SocialAiRoutingIntentResult.ClarificationRequired)
        assertTrue(parser("").parseStrict("DESTINATION=home\nAVOID=spaceship\nVIA=") is SocialAiRoutingIntentResult.ClarificationRequired)
        assertTrue(parser("").parseStrict("DESTINATION=clarify\nAVOID=\nVIA=") is SocialAiRoutingIntentResult.ClarificationRequired)
    }

    @Test fun bridgeCreatesOnlyDeterministicRequestWithoutAvoidance() {
        val p = parser("DESTINATION=home\nAVOID=\nVIA=supermarket")
        val bridge = SocialAiNavigationRequestBridge(p, object : SocialAiRoutingPlaceResolver {
            override fun resolveDestination(identifier: String) = if (identifier == "home") RoutePoint(48.1, 11.5) else null
            override fun resolveViaCategory(identifier: String) = if (identifier == "supermarket") RoutePoint(48.2, 11.6) else null
        })
        val result = bridge.buildRequest(RoutePoint(48.0, 11.4), "Fahr heim, vorher Supermarkt")
        assertTrue(result is SocialAiNavigationRequestResult.Ready)
        val request = (result as SocialAiNavigationRequestResult.Ready).request
        assertEquals(1, request.viaPoints.size)
    }

    @Test fun motorwayAvoidanceIsNotApproximated() {
        val resolver = object : SocialAiRoutingPlaceResolver {
            override fun resolveDestination(identifier: String) = RoutePoint(48.1, 11.5)
            override fun resolveViaCategory(identifier: String): RoutePoint? = null
        }
        val result = SocialAiNavigationRequestBridge(
            parser("DESTINATION=home\nAVOID=motorway\nVIA="), resolver
        ).buildRequest(RoutePoint(48.0, 11.4), "ohne Autobahn")
        assertTrue(result is SocialAiNavigationRequestResult.ClarificationRequired)
    }

    @Test fun unresolvedPlaceAndUnsupportedAvoidanceFailClosed() {
        val resolver = object : SocialAiRoutingPlaceResolver {
            override fun resolveDestination(identifier: String) = RoutePoint(48.1, 11.5)
            override fun resolveViaCategory(identifier: String): RoutePoint? = null
        }
        val via = SocialAiNavigationRequestBridge(
            parser("DESTINATION=home\nAVOID=\nVIA=supermarket"), resolver
        ).buildRequest(RoutePoint(48.0, 11.4), "test")
        assertTrue(via is SocialAiNavigationRequestResult.ClarificationRequired)

        val toll = SocialAiNavigationRequestBridge(
            parser("DESTINATION=home\nAVOID=toll\nVIA="), resolver
        ).buildRequest(RoutePoint(48.0, 11.4), "test")
        assertTrue(toll is SocialAiNavigationRequestResult.ClarificationRequired)
    }
}
