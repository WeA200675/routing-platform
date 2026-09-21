package org.routingplatform.app.navigation

/**
 * Deterministic policy for requesting a fresh engine route while navigating.
 *
 * The client never invents traffic conditions. A refresh only asks the configured
 * route engine to re-evaluate the unchanged trip request. Provider failures keep
 * the currently active route.
 */
data class NavigationTrafficReevaluationPolicy(
    val refreshIntervalMs: Long = 120_000L,
    val retryIntervalMs: Long = 30_000L,
) {
    init {
        require(refreshIntervalMs >= 30_000L)
        require(retryIntervalMs >= 10_000L)
    }

    fun decide(
        nowMs: Long,
        lastSuccessfulRefreshMs: Long?,
        lastAttemptMs: Long?,
        requestInFlight: Boolean,
    ): NavigationTrafficReevaluationDecision {
        require(nowMs >= 0L) { "Monotonic time must not be negative." }
        require(lastSuccessfulRefreshMs == null || lastSuccessfulRefreshMs <= nowMs) {
            "Successful refresh timestamp cannot be in the future."
        }
        require(lastAttemptMs == null || lastAttemptMs <= nowMs) {
            "Refresh attempt timestamp cannot be in the future."
        }

        if (requestInFlight) {
            return NavigationTrafficReevaluationDecision.Hold(
                NavigationTrafficReevaluationHoldReason.RequestInFlight
            )
        }
        val attempt = lastAttemptMs
        if (attempt != null && nowMs - attempt < retryIntervalMs) {
            return NavigationTrafficReevaluationDecision.Hold(
                NavigationTrafficReevaluationHoldReason.RetryCooldown
            )
        }
        val success = lastSuccessfulRefreshMs
        if (success == null || nowMs - success >= refreshIntervalMs) {
            return NavigationTrafficReevaluationDecision.Refresh
        }
        return NavigationTrafficReevaluationDecision.Hold(
            NavigationTrafficReevaluationHoldReason.Fresh
        )
    }
}

sealed interface NavigationTrafficReevaluationDecision {
    data object Refresh : NavigationTrafficReevaluationDecision
    data class Hold(
        val reason: NavigationTrafficReevaluationHoldReason,
    ) : NavigationTrafficReevaluationDecision
}

enum class NavigationTrafficReevaluationHoldReason {
    Fresh,
    RequestInFlight,
    RetryCooldown,
}


/**
 * A periodic refresh may replace an active route only when it is the same
 * deterministic routing family and the engine result is materially better.
 * This prevents harmless provider jitter from restarting navigation.
 */
data class NavigationTrafficReplacementPolicy(
    val minimumDurationImprovementS: Double = 30.0,
    val minimumRelativeImprovement: Double = 0.05,
) {
    init {
        require(minimumDurationImprovementS >= 0.0)
        require(minimumRelativeImprovement in 0.0..1.0)
    }

    fun shouldReplace(
        current: NavigationRouteContract,
        candidate: NavigationRouteContract,
    ): Boolean {
        if (candidate.family != current.family) return false
        val improvement = current.durationS - candidate.durationS
        if (improvement < minimumDurationImprovementS) return false
        return improvement / current.durationS >= minimumRelativeImprovement
    }
}
