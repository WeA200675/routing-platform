package org.routingplatform.app.ai

/** Keeps repeated native failures from producing a restart/failure loop. */
class SocialAiRuntimeCircuitBreaker(
    private val maximumConsecutiveFailures: Int = 2,
) {
    init {
        require(maximumConsecutiveFailures in 1..10)
    }

    private var consecutiveFailures = 0

    @Synchronized
    fun allowAttempt(): Boolean = consecutiveFailures < maximumConsecutiveFailures

    @Synchronized
    fun recordSuccess() {
        consecutiveFailures = 0
    }

    @Synchronized
    fun recordFailure() {
        consecutiveFailures = (consecutiveFailures + 1).coerceAtMost(maximumConsecutiveFailures)
    }

    @Synchronized
    fun reset() {
        consecutiveFailures = 0
    }

    @Synchronized
    fun failureCount(): Int = consecutiveFailures
}
