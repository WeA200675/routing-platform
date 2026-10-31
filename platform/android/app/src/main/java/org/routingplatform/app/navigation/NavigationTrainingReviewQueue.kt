package org.routingplatform.app.navigation

/**
 * Deterministic, bounded review queue. It only records user decisions; it never
 * promotes a model or changes authoritative navigation behavior.
 */
class NavigationTrainingReviewQueue(
    cases: List<NavigationTrainingCase> = emptyList(),
    private val maximumSize: Int = 256,
) {
    private var entries: List<NavigationTrainingCase>

    init {
        require(maximumSize in 1..4096)
        require(cases.map { it.id }.distinct().size == cases.size)
        cases.forEach { it.validateForReview() }
        entries = cases.take(maximumSize).sortedWith(order)
    }

    fun snapshot(): List<NavigationTrainingCase> = entries

    fun enqueue(case: NavigationTrainingCase): NavigationTrainingReviewQueue {
        case.validateForReview()
        require(entries.none { it.id == case.id }) { "Training case already exists." }
        entries = (entries + case).sortedWith(order).take(maximumSize)
        return this
    }

    fun review(id: String, state: NavigationTrainingReviewState): Boolean {
        val index = entries.indexOfFirst { it.id == id }
        if (index < 0) return false
        entries = entries.toMutableList().also { it[index] = it[index].copy(reviewState = state) }
        return true
    }

    companion object {
        private val order = compareBy<NavigationTrainingCase> { it.reviewState != NavigationTrainingReviewState.Proposed }
            .thenBy { it.priority.ordinal }
            .thenBy { it.id }
    }
}
