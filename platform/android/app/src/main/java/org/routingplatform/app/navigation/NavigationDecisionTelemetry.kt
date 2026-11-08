package org.routingplatform.app.navigation

data class NavigationDecisionTelemetry(
    val capturedAtElapsedMs: Long,
    val watchdogStatus: NavigationWatchdogStatus,
    val liveAction: LiveRouteAction,
    val recoveryAction: NavigationRecoveryAction,
    val recalculationRequested: Boolean,
    val reason: String?,
)

class NavigationDecisionTelemetryBuilder {
    fun build(
        nowElapsedMs: Long,
        decision: NavigationDecisionSnapshot,
    ): NavigationDecisionTelemetry =
        NavigationDecisionTelemetry(
            capturedAtElapsedMs = nowElapsedMs,
            watchdogStatus = decision.recovery.watchdog.status,
            liveAction = decision.liveAction,
            recoveryAction = decision.recovery.action,
            recalculationRequested = decision.shouldRequestRecalculation,
            reason = decision.recovery.watchdog.reason,
        )
}
