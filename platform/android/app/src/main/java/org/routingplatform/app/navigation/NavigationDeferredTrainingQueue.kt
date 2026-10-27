package org.routingplatform.app.navigation

data class NavigationDeferredTrainingCase(
    val id: String,
    val payload: String,
    val reason: String,
    val attempt: Int = 0,
)

data class NavigationRecoveryRun(
    val sourceCount: Int,
    val processedCount: Int,
    val requeuedCount: Int,
    val stillDeferredCount: Int,
    val batchSize: Int,
)

class NavigationDeferredTrainingQueue(
    private val recoveryThreshold: Int = 600,
    private val recoveryBatchSize: Int = 32,
) {
    init {
        require(recoveryThreshold > 0)
        require(recoveryBatchSize > 0)
    }

    private val deferred =
        ArrayDeque<NavigationDeferredTrainingCase>()

    fun defer(case: NavigationDeferredTrainingCase) {
        deferred.addLast(case)
    }

    fun size(): Int = deferred.size

    fun recoveryReady(): Boolean =
        deferred.size >= recoveryThreshold

    /**
     * Makes oversized/unprocessable cases usable again by splitting the
     * payload into bounded windows before returning them to the learner.
     */
    fun recover(
        normalize: (String) -> List<String>,
    ): NavigationRecoveryRun? {
        if (!recoveryReady()) return null

        val sourceCount = deferred.size
        var processed = 0
        var requeued = 0
        var stillDeferred = 0
        val recovered = ArrayDeque<NavigationDeferredTrainingCase>()

        while (deferred.isNotEmpty()) {
            val original = deferred.removeFirst()
            val chunks = normalize(original.payload)
                .filter { it.isNotBlank() }

            if (chunks.isEmpty()) {
                stillDeferred++
                if (original.attempt < MAX_RECOVERY_ATTEMPTS) {
                    recovered.addLast(
                        original.copy(attempt = original.attempt + 1)
                    )
                }
                continue
            }

            processed++
            chunks.chunked(recoveryBatchSize).forEachIndexed { index, chunk ->
                recovered.addLast(
                    NavigationDeferredTrainingCase(
                        id = original.id + ":recovered:" + index,
                        payload = chunk.joinToString("\n"),
                        reason = "recovered_window",
                        attempt = original.attempt + 1,
                    )
                )
                requeued++
            }
        }

        deferred.addAll(recovered)

        return NavigationRecoveryRun(
            sourceCount = sourceCount,
            processedCount = processed,
            requeuedCount = requeued,
            stillDeferredCount = stillDeferred,
            batchSize = recoveryBatchSize,
        )
    }

    companion object {
        private const val MAX_RECOVERY_ATTEMPTS = 3
    }
}
