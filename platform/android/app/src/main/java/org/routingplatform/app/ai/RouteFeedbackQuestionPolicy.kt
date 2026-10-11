package org.routingplatform.app.ai

internal enum class RouteFeedbackQuestionKind {
    RouteComfort,
    GuidanceTiming,
    RoadChoice,
    PersonalPriority,
}

internal data class RouteFeedbackQuestion(
    val kind: RouteFeedbackQuestionKind,
    val prompt: String,
    val options: List<String>,
)

internal object RouteFeedbackQuestionPolicy {
    private val questions = listOf(
        RouteFeedbackQuestion(
            RouteFeedbackQuestionKind.RouteComfort,
            "Wie hat sich die gefahrene Route angefühlt?",
            listOf("Angenehm", "Zu viele Abbiegungen", "Zu viel Stadtverkehr", "Zu viele schnelle Straßen"),
        ),
        RouteFeedbackQuestion(
            RouteFeedbackQuestionKind.GuidanceTiming,
            "Wie war der Zeitpunkt der Fahransagen?",
            listOf("Passend", "Früher ansagen", "Später ansagen", "Weniger Ansagen"),
        ),
        RouteFeedbackQuestion(
            RouteFeedbackQuestionKind.RoadChoice,
            "Welche Straßen möchtest du künftig öfter nutzen?",
            listOf("Autobahn und Bundesstraßen", "Ruhige Nebenstraßen", "Kürzere Verbindungen", "So wie heute"),
        ),
        RouteFeedbackQuestion(
            RouteFeedbackQuestionKind.PersonalPriority,
            "Was ist dir bei der nächsten Route heute am wichtigsten?",
            listOf("Schnell ankommen", "Entspannt fahren", "Weniger Abbiegen", "Weniger Stadtverkehr"),
        ),
    )

    fun next(
        lastQuestionIndex: Int,
        routeFamilyName: String?,
        driveDurationMinutes: Int,
    ): Pair<Int, RouteFeedbackQuestion> {
        require(questions.isNotEmpty())
        val seed = (
            (lastQuestionIndex + 1).coerceAtLeast(0) +
                (routeFamilyName?.hashCode()?.and(Int.MAX_VALUE) ?: 0) +
                driveDurationMinutes.coerceAtLeast(0)
            ) % questions.size
        val index = if (questions.size > 1 && indexWouldRepeat(seed, lastQuestionIndex)) {
            (seed + 1) % questions.size
        } else {
            seed
        }
        return index to questions[index]
    }

    private fun indexWouldRepeat(candidate: Int, previous: Int): Boolean =
        candidate == previous

}
