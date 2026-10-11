package org.routingplatform.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.routingplatform.app.navigation.NavigationRouteFamily

class RouteFeedbackRoutePreferenceTest {
    private val current = NavigationRouteFamily.Energy

    @Test
    fun routeComfortAnswersBecomeExplicitSuggestions() {
        assertEquals(current, suggest(RouteFeedbackQuestionKind.RouteComfort, "Angenehm"))
        assertEquals(NavigationRouteFamily.Comfort, suggest(RouteFeedbackQuestionKind.RouteComfort, "Zu viele Abbiegungen"))
        assertEquals(NavigationRouteFamily.LowUrban, suggest(RouteFeedbackQuestionKind.RouteComfort, "Zu viel Stadtverkehr"))
        assertEquals(NavigationRouteFamily.Scenic, suggest(RouteFeedbackQuestionKind.RouteComfort, "Zu viele schnelle Straßen"))
    }

    @Test
    fun roadChoiceAnswersBecomeExplicitSuggestions() {
        assertEquals(NavigationRouteFamily.MajorRoads, suggest(RouteFeedbackQuestionKind.RoadChoice, "Autobahn und Bundesstraßen"))
        assertEquals(NavigationRouteFamily.LowTraffic, suggest(RouteFeedbackQuestionKind.RoadChoice, "Ruhige Nebenstraßen"))
        assertEquals(NavigationRouteFamily.Shortest, suggest(RouteFeedbackQuestionKind.RoadChoice, "Kürzere Verbindungen"))
        assertEquals(current, suggest(RouteFeedbackQuestionKind.RoadChoice, "So wie heute"))
    }

    @Test
    fun personalPriorityAnswersBecomeExplicitSuggestions() {
        assertEquals(NavigationRouteFamily.Fastest, suggest(RouteFeedbackQuestionKind.PersonalPriority, "Schnell ankommen"))
        assertEquals(NavigationRouteFamily.Comfort, suggest(RouteFeedbackQuestionKind.PersonalPriority, "Entspannt fahren"))
        assertEquals(NavigationRouteFamily.Stable, suggest(RouteFeedbackQuestionKind.PersonalPriority, "Weniger Abbiegen"))
        assertEquals(NavigationRouteFamily.LowUrban, suggest(RouteFeedbackQuestionKind.PersonalPriority, "Weniger Stadtverkehr"))
    }

    @Test
    fun timingAndUnrecognizedAnswersDoNotChangeRouteFamily() {
        assertNull(suggest(RouteFeedbackQuestionKind.GuidanceTiming, "Früher ansagen"))
        assertNull(suggest(RouteFeedbackQuestionKind.RouteComfort, "unbekannt"))
    }

    private fun suggest(kind: RouteFeedbackQuestionKind, answer: String): NavigationRouteFamily? =
        RouteFeedbackRoutePreference.recommendedFamily(kind, answer, current)
}
