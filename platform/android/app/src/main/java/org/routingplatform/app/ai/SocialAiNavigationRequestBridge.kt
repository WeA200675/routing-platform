package org.routingplatform.app.ai

import org.routingplatform.app.navigation.NavigationRouteFamily
import org.routingplatform.app.navigation.NavigationRouteRequest
import org.routingplatform.app.navigation.RoutePoint

interface SocialAiRoutingPlaceResolver {
    fun resolveDestination(identifier: String): RoutePoint?
    fun resolveViaCategory(identifier: String): RoutePoint?
}

sealed interface SocialAiNavigationRequestResult {
    data class Ready(val request: NavigationRouteRequest) : SocialAiNavigationRequestResult
    data class ClarificationRequired(val reason: String) : SocialAiNavigationRequestResult
}

/**
 * The only bridge from LLM interpretation to deterministic routing input.
 * Coordinates are resolved by trusted app code; model text never becomes
 * coordinates, route geometry, GPS state, or safety state.
 */
class SocialAiNavigationRequestBridge(
    private val parser: SocialAiRoutingIntentParser,
    private val placeResolver: SocialAiRoutingPlaceResolver,
) {
    fun buildRequest(origin: RoutePoint, userText: String): SocialAiNavigationRequestResult {
        return when (val parsed = parser.parse(userText)) {
            is SocialAiRoutingIntentResult.ClarificationRequired ->
                SocialAiNavigationRequestResult.ClarificationRequired(parsed.reason)
            is SocialAiRoutingIntentResult.Ready -> buildResolved(origin, parsed.intent)
        }
    }

    internal fun buildResolved(
        origin: RoutePoint,
        intent: SocialAiRoutingIntent,
    ): SocialAiNavigationRequestResult {
        val destination = placeResolver.resolveDestination(intent.destination)
            ?: return clarification("Ziel konnte nicht eindeutig aufgelöst werden.")
        val via = intent.viaCategory?.let {
            placeResolver.resolveViaCategory(it)
                ?: return clarification("Zwischenstopp konnte nicht eindeutig aufgelöst werden.")
        }
        // No current NavigationRouteFamily is an exact avoidance contract.
        // LowUrban is deliberately NOT treated as "avoid motorways".
        // Until deterministic routing exposes explicit avoidance flags, fail closed.
        if (intent.avoid.isNotEmpty()) {
            return clarification("Diese Routenvermeidung wird von der Routing-Engine noch nicht exakt unterstützt.")
        }
        val family = NavigationRouteFamily.ProfileOptimal
        return SocialAiNavigationRequestResult.Ready(
            NavigationRouteRequest(
                origin = origin,
                destination = destination,
                viaPoints = listOfNotNull(via),
                family = family,
            )
        )
    }

    private fun clarification(reason: String) =
        SocialAiNavigationRequestResult.ClarificationRequired(reason)
}
