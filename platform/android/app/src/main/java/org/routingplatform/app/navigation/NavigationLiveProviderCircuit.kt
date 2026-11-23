package org.routingplatform.app.navigation

data class ProviderHealth(
    val providerId: String,
    val consecutiveFailures: Int,
    val suspendedUntilEpochMs: Long,
)

/**
 * Isolates failing optional live-data providers. Never blocks the offline router.
 * All state changes are serialized because callbacks may arrive on different threads.
 */
class NavigationLiveProviderCircuit(
    private val failureThreshold: Int = 3,
    private val suspensionMs: Long = 60_000L,
) {
    private data class State(var failures: Int = 0, var suspendedUntil: Long = 0L)
    private val states = mutableMapOf<String, State>()

    init {
        require(failureThreshold > 0)
        require(suspensionMs > 0)
    }

    @Synchronized
    fun isAllowed(providerId: String, nowEpochMs: Long): Boolean {
        val state = states[providerId] ?: return true
        return nowEpochMs >= state.suspendedUntil
    }

    @Synchronized
    fun recordSuccess(providerId: String) {
        states.remove(providerId)
    }

    @Synchronized
    fun recordFailure(providerId: String, nowEpochMs: Long) {
        val state = states.getOrPut(providerId) { State() }
        // A completed cooldown begins a fresh failure window. Without this,
        // a single failure after recovery would immediately trip the circuit.
        if (state.suspendedUntil > 0L && nowEpochMs >= state.suspendedUntil) {
            state.failures = 0
            state.suspendedUntil = 0L
        }
        // Ignore failures reported while suspended; they must not extend cooldown.
        if (state.suspendedUntil > 0L && nowEpochMs < state.suspendedUntil) return
        if (state.failures < failureThreshold) state.failures++
        if (state.failures >= failureThreshold) {
            // Saturate instead of overflowing near Long.MAX_VALUE.
            state.suspendedUntil =
                if (nowEpochMs > Long.MAX_VALUE - suspensionMs) Long.MAX_VALUE
                else nowEpochMs + suspensionMs
        }
    }

    @Synchronized
    fun health(providerId: String): ProviderHealth {
        val state = states[providerId]
        return ProviderHealth(
            providerId = providerId,
            consecutiveFailures = state?.failures ?: 0,
            suspendedUntilEpochMs = state?.suspendedUntil ?: 0L,
        )
    }
}
