package org.routingplatform.app.navigation

/**
 * Concrete bounded admission queue for navigation work. It turns the P24/P31
 * resource contract into an enforceable runtime primitive rather than leaving
 * backpressure as a semantic-only decision.
 */
class NavigationBoundedWorkQueue<T>(
    private val budget: NavigationResourceBudget = NavigationResourceBudget(),
) {
    private val queued = ArrayDeque<T>()

    @Synchronized
    fun offer(item: T, bufferedSamples: Int): NavigationBackpressureDecision {
        val decision = NavigationResourceGovernor.admit(
            NavigationResourceSnapshot(
                bufferedSamples = bufferedSamples,
                pendingWork = queued.size,
            ),
            budget,
        ).toBackpressureDecision()

        if (!decision.admitNewWork) {
            if (decision.cancelQueuedWork) queued.clear()
            return decision
        }

        // Admitting this item must not make pending work exceed the budget.
        if (queued.size >= budget.maximumPendingWork) {
            queued.clear()
            return NavigationBackpressureDecision(
                admitNewWork = false,
                cancelQueuedWork = true,
            )
        }
        queued.addLast(item)
        return decision
    }

    @Synchronized
    fun poll(): T? = if (queued.isEmpty()) null else queued.removeFirst()

    @Synchronized
    fun cancelAll() = queued.clear()

    val size: Int get() = queued.size
}
