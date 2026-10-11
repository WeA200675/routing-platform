package org.routingplatform.app.navigation

data class NavigationResourceBudget(
    val maximumBufferedSamples: Int = 256,
    val maximumPendingWork: Int = 32,
) {
    init {
        require(maximumBufferedSamples > 0)
        require(maximumPendingWork > 0)
    }
}

data class NavigationResourceSnapshot(
    val bufferedSamples: Int,
    val pendingWork: Int,
)

enum class NavigationResourceAdmission { Available, Exhausted, Invalid }

object NavigationResourceGovernor {
    fun admit(
        snapshot: NavigationResourceSnapshot,
        budget: NavigationResourceBudget = NavigationResourceBudget(),
    ): NavigationResourceAdmission {
        if (snapshot.bufferedSamples < 0 || snapshot.pendingWork < 0) {
            return NavigationResourceAdmission.Invalid
        }
        if (snapshot.bufferedSamples > budget.maximumBufferedSamples ||
            snapshot.pendingWork > budget.maximumPendingWork
        ) {
            return NavigationResourceAdmission.Exhausted
        }
        return NavigationResourceAdmission.Available
    }
}

data class NavigationBackpressureDecision(
    val admitNewWork: Boolean,
    val cancelQueuedWork: Boolean,
)

fun NavigationResourceAdmission.toBackpressureDecision(): NavigationBackpressureDecision =
    when (this) {
        NavigationResourceAdmission.Available ->
            NavigationBackpressureDecision(admitNewWork = true, cancelQueuedWork = false)
        NavigationResourceAdmission.Exhausted ->
            NavigationBackpressureDecision(admitNewWork = false, cancelQueuedWork = true)
        NavigationResourceAdmission.Invalid ->
            NavigationBackpressureDecision(admitNewWork = false, cancelQueuedWork = true)
    }
