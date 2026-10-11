package org.routingplatform.app.ai

import org.routingplatform.app.navigation.NavigationRouteFamily

/**
 * Turns explicit post-drive answers into a route-family suggestion.
 * The suggestion is persisted only when learning is enabled and is applied
 * only after the user confirms it in the route planner.
 */
internal object RouteFeedbackRoutePreference {
    fun recommendedFamily(
        kind: RouteFeedbackQuestionKind,
        answer: String,
        currentFamily: NavigationRouteFamily,
    ): NavigationRouteFamily? =
        when (kind) {
            RouteFeedbackQuestionKind.GuidanceTiming -> null
            RouteFeedbackQuestionKind.RouteComfort ->
                when (answer) {
                    "Angenehm" -> currentFamily
                    "Zu viele Abbiegungen" -> NavigationRouteFamily.Comfort
                    "Zu viel Stadtverkehr" -> NavigationRouteFamily.LowUrban
                    "Zu viele schnelle Straßen" -> NavigationRouteFamily.Scenic
                    else -> null
                }
            RouteFeedbackQuestionKind.RoadChoice ->
                when (answer) {
                    "Autobahn und Bundesstraßen" -> NavigationRouteFamily.MajorRoads
                    "Ruhige Nebenstraßen" -> NavigationRouteFamily.LowTraffic
                    "Kürzere Verbindungen" -> NavigationRouteFamily.Shortest
                    "So wie heute" -> currentFamily
                    else -> null
                }
            RouteFeedbackQuestionKind.PersonalPriority ->
                when (answer) {
                    "Schnell ankommen" -> NavigationRouteFamily.Fastest
                    "Entspannt fahren" -> NavigationRouteFamily.Comfort
                    "Weniger Abbiegen" -> NavigationRouteFamily.Stable
                    "Weniger Stadtverkehr" -> NavigationRouteFamily.LowUrban
                    else -> null
                }
        }
}
