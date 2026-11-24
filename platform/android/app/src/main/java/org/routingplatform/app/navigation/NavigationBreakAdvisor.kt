package org.routingplatform.app.navigation

/**
 * Pure, offline-capable decision logic for voluntary WC/rest-area suggestions.
 * Discovery and ETA must come from a real route engine; never estimate detours
 * using straight-line distance or automatically change the active route.
 */
internal object NavigationBreakAdvisor {
    const val MIN_DRIVE_MINUTES = 90
    const val MIN_REMAINING_KM = 60.0
    const val MAX_DETOUR_MINUTES = 10
    const val TRAFFIC_LOOKAHEAD_MINUTES = 30
    const val RECOMMENDATION_COOLDOWN_MINUTES = 30

    data class Candidate(
        val id: String,
        val name: String,
        val kind: Kind,
        val additionalDriveMinutes: Int?,
        val beforeCongestion: Boolean,
        val safelyAccessible: Boolean,
    ) {
        enum class Kind { WC, REST_AREA }
    }

    data class Context(
        val drivenMinutesSinceBreak: Int,
        val remainingKilometers: Double,
        val trafficDelayAheadMinutes: Int?,
        val trafficStartsInMinutes: Int?,
        val minutesSinceLastSuggestion: Int?,
    )

    data class Recommendation(val candidate: Candidate, val reason: Reason) {
        enum class Reason { LONG_DRIVE, UPCOMING_CONGESTION }
    }

    fun recommend(context: Context, candidates: List<Candidate>): Recommendation? {
        if (context.drivenMinutesSinceBreak < 0 ||
            !context.remainingKilometers.isFinite() || context.remainingKilometers < 0.0 ||
            (context.minutesSinceLastSuggestion != null && context.minutesSinceLastSuggestion < 0)
        ) return null
        if (context.minutesSinceLastSuggestion != null &&
            context.minutesSinceLastSuggestion < RECOMMENDATION_COOLDOWN_MINUTES) return null

        val longDrive = context.drivenMinutesSinceBreak > MIN_DRIVE_MINUTES &&
            context.remainingKilometers > MIN_REMAINING_KM
        val congestion = context.trafficDelayAheadMinutes?.let { it > 0 } == true &&
            context.trafficStartsInMinutes?.let { it in 0..TRAFFIC_LOOKAHEAD_MINUTES } == true &&
            context.remainingKilometers > MIN_REMAINING_KM
        if (!longDrive && !congestion) return null

        val eligible = candidates.filter {
            it.id.isNotBlank() && it.name.isNotBlank() && it.safelyAccessible &&
                it.additionalDriveMinutes != null &&
                it.additionalDriveMinutes in 0..MAX_DETOUR_MINUTES &&
                (!congestion || it.beforeCongestion)
        }
        val best = eligible.minWithOrNull(compareBy<Candidate> { it.additionalDriveMinutes!! }
            .thenBy { it.kind != Candidate.Kind.WC }.thenBy { it.id }) ?: return null
        return Recommendation(best, if (congestion) Recommendation.Reason.UPCOMING_CONGESTION
            else Recommendation.Reason.LONG_DRIVE)
    }
}
