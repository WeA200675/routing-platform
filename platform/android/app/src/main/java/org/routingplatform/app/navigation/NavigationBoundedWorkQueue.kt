package org.routingplatform.app.navigation

data class NavigationWorkQueueDiagnostics(
    val queuedWork: Int,
    val highWaterMark: Int,
    val admittedWork: Long,
    val rejectedWork: Long,
    val cancelledWork: Long,
)

/**
 * Concrete bounded admission queue for navigation work. It turns the P24/P31
 * resource contract into an enforceable runtime primitive rather than leaving
 * backpressure as a semantic-only decision.
 */
class NavigationBoundedWorkQueue<T>(
    private val budget: NavigationResourceBudget = NavigationResourceBudget(),
) {
    private val queued = ArrayDeque<T>()
    private var highWaterMark = 0
    private var admittedWork = 0L
    private var rejectedWork = 0L
    private var cancelledWork = 0L

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
            rejectedWork += 1
            if (decision.cancelQueuedWork) {
                cancelledWork += queued.size.toLong()
                queued.clear()
            }
            return decision
        }

        if (queued.size >= budget.maximumPendingWork) {
            rejectedWork += 1
            cancelledWork += queued.size.toLong()
            queued.clear()
            return NavigationBackpressureDecision(
                admitNewWork = false,
                cancelQueuedWork = true,
            )
        }

        queued.addLast(item)
        admittedWork += 1
        highWaterMark = maxOf(highWaterMark, queued.size)
        return decision
    }

    @Synchronized
    fun poll(): T? = if (queued.isEmpty()) null else queued.removeFirst()

    @Synchronized
    fun cancelAll() {
        cancelledWork += queued.size.toLong()
        queued.clear()
    }

    @Synchronized
    fun diagnostics() = NavigationWorkQueueDiagnostics(
        queuedWork = queued.size,
        highWaterMark = highWaterMark,
        admittedWork = admittedWork,
        rejectedWork = rejectedWork,
        cancelledWork = cancelledWork,
    )

    @Synchronized
    fun resetDiagnostics() {
        highWaterMark = queued.size
        admittedWork = 0
        rejectedWork = 0
        cancelledWork = 0
    }

    val size: Int
        @Synchronized get() = queued.size
}
