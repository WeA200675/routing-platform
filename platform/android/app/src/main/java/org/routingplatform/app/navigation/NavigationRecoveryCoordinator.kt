package org.routingplatform.app.navigation

enum class NavigationRecoveryAction {
    NONE,
    REQUEST_RECALCULATION,
    KEEP_CURRENT_ROUTE,
}

data class NavigationRecoveryDecision(
    val action: NavigationRecoveryAction,
    val watchdog: NavigationWatchdogSnapshot,
    val requestId: Long? = null,
)

class NavigationRecoveryCoordinator(
    private val watchdog: NavigationProgressWatchdog = NavigationProgressWatchdog(),
) {
    private var nextRequestId: Long = 0L
    private var pendingRequestId: Long? = null

    fun evaluate(
        nowElapsedMs: Long,
        lastPositionElapsedMs: Long?,
        lastProgressElapsedMs: Long?,
        navigationActive: Boolean,
    ): NavigationRecoveryDecision {
        val snapshot = watchdog.evaluate(
            nowElapsedMs = nowElapsedMs,
            lastPositionElapsedMs = lastPositionElapsedMs,
            lastProgressElapsedMs = lastProgressElapsedMs,
            navigationActive = navigationActive,
        )

        if (snapshot.status != NavigationWatchdogStatus.RECOVERY_REQUIRED) {
            return NavigationRecoveryDecision(NavigationRecoveryAction.NONE, snapshot)
        }

        val existing = pendingRequestId
        if (existing != null) {
            return NavigationRecoveryDecision(
                NavigationRecoveryAction.KEEP_CURRENT_ROUTE,
                snapshot,
                existing,
            )
        }

        val requestId = ++nextRequestId
        pendingRequestId = requestId
        return NavigationRecoveryDecision(
            NavigationRecoveryAction.REQUEST_RECALCULATION,
            snapshot,
            requestId,
        )
    }

    fun complete(requestId: Long) {
        if (pendingRequestId == requestId) pendingRequestId = null
    }

    fun cancel() {
        pendingRequestId = null
    }
}
