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
