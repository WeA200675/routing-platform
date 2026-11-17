package org.routingplatform.app.navigation

/**
 * Fail-closed journey-break policy. Inputs are independently verified by
 * POI/Valhalla/traffic adapters; this module does not fetch or invent them.
 * All detour calculations use seconds for the *whole* route via the stop.
 */
internal object JourneyBreakPolicy {
    const val MAX_AUTOMATIC_DETOUR_SECONDS = 600L
    const val MIN_DRIVE_SECONDS = 5400L
    const val MIN_REMAINING_METERS = 60_000L
    const val DEFAULT_COOLDOWN_SECONDS = 1800L

    enum class WcEvidence { VERIFIED, UNKNOWN, ABSENT }
    enum class Trigger { REGULAR, CONGESTION, URGENT_USER_REQUEST }
    enum class DecisionReason {
        ELIGIBLE, NOT_DUE, COOLDOWN, INVALID_CONTEXT, NO_VERIFIED_STOP
    }

    data class Stop(
        val id: String,
        val name: String,
        val wc: WcEvidence,
        val legalAccessAndReturn: Boolean,
        val sameDirection: Boolean,
        val originalRouteSeconds: Long?,
        val viaStopRouteSeconds: Long?,
        val beforeCongestion: Boolean = false,
    ) {
        val detourSeconds: Long?
            get() {
                val base = originalRouteSeconds ?: return null
                val via = viaStopRouteSeconds ?: return null
                if (base < 0 || via < base) return null
                return via - base
            }
    }

    data class Context(
        val driveSecondsSinceConfirmedBreak: Long,
        val remainingMeters: Long,
        val secondsSinceLastOffer: Long?,
        val confirmedTrafficAhead: Boolean = false,
        val userRequestedWcNow: Boolean = false,
        val requireVerifiedWc: Boolean = true,
    )

    data class Recommendation(
        val trigger: Trigger,
        val stops: List<Stop>,
        val reason: DecisionReason = DecisionReason.ELIGIBLE,
    )

    data class Result(
        val reason: DecisionReason,
        val recommendation: Recommendation? = null,
    )

    fun evaluate(context: Context, stops: List<Stop>): Result {
        if (context.driveSecondsSinceConfirmedBreak < 0 || context.remainingMeters < 0 ||
            (context.secondsSinceLastOffer != null && context.secondsSinceLastOffer < 0)) {
            return Result(DecisionReason.INVALID_CONTEXT)
        }
        val trigger = when {
            context.userRequestedWcNow -> Trigger.URGENT_USER_REQUEST
            context.secondsSinceLastOffer != null &&
                context.secondsSinceLastOffer < DEFAULT_COOLDOWN_SECONDS ->
                return Result(DecisionReason.COOLDOWN)
            context.confirmedTrafficAhead && context.remainingMeters > MIN_REMAINING_METERS ->
                Trigger.CONGESTION
            context.driveSecondsSinceConfirmedBreak > MIN_DRIVE_SECONDS &&
                context.remainingMeters > MIN_REMAINING_METERS -> Trigger.REGULAR
            else -> return Result(DecisionReason.NOT_DUE)
        }
        val candidates = stops.asSequence().filter {
            it.id.isNotBlank() && it.name.isNotBlank() &&
                it.legalAccessAndReturn && it.sameDirection &&
                (it.wc == WcEvidence.VERIFIED || (!context.requireVerifiedWc &&
                    !context.userRequestedWcNow && it.wc == WcEvidence.UNKNOWN)) &&
                (trigger != Trigger.CONGESTION || it.beforeCongestion) &&
                it.detourSeconds != null && it.detourSeconds!! <= MAX_AUTOMATIC_DETOUR_SECONDS
        }.sortedWith(
            compareBy<Stop> { it.wc != WcEvidence.VERIFIED }
                .thenBy { it.detourSeconds!! }.thenBy { it.id }
        ).take(3).toList()
        if (candidates.isEmpty()) return Result(DecisionReason.NO_VERIFIED_STOP)
        return Result(DecisionReason.ELIGIBLE, Recommendation(trigger, candidates))
    }
}
