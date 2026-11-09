package org.routingplatform.app.intelligence

data class DiscoveryReputation(
    val candidateId: String,
    val value: Double,
    val observations: Long,
    val consecutiveFailures: Int,
    val quarantinedUntilEpochMs: Long?,
    val eligibleForActivation: Boolean,
)

internal class DiscoveryReputationEngine(
    private val decay: Double = 0.12,
    private val quarantineBaseMs: Long = 5 * 60 * 1_000L,
) {
    private data class State(
        var value: Double = 0.5,
        var observations: Long = 0,
        var consecutiveFailures: Int = 0,
        var quarantinedUntil: Long? = null,
    )

    private val states = mutableMapOf<String, State>()

    @Synchronized
    fun record(
        candidateId: String,
        nowEpochMs: Long,
        success: Boolean,
        safetyValid: Boolean,
        usefulData: Boolean,
    ): DiscoveryReputation {
        val state = states.getOrPut(candidateId) { State() }
        val signal = when {
            !safetyValid -> 0.0
            !success -> 0.10
            !usefulData -> 0.35
            else -> 1.0
        }
        state.value += decay * (signal - state.value)
        state.observations += 1
        if (success && safetyValid && usefulData) {
            state.consecutiveFailures = 0
            if (state.quarantinedUntil != null && nowEpochMs >= state.quarantinedUntil!!) {
                state.quarantinedUntil = null
            }
        } else {
            state.consecutiveFailures += 1
            if (!safetyValid || state.consecutiveFailures >= 3) {
                val multiplier = state.consecutiveFailures.coerceAtMost(6)
                state.quarantinedUntil = nowEpochMs + quarantineBaseMs * multiplier
            }
        }
        val quarantined = state.quarantinedUntil?.let { nowEpochMs < it } == true
        return DiscoveryReputation(
            candidateId = candidateId,
            value = state.value.coerceIn(0.0, 1.0),
            observations = state.observations,
            consecutiveFailures = state.consecutiveFailures,
            quarantinedUntilEpochMs = state.quarantinedUntil,
            eligibleForActivation = !quarantined &&
                state.observations >= 5 &&
                state.value >= 0.80,
        )
    }

    @Synchronized
    fun current(candidateId: String): DiscoveryReputation {
        val state = states[candidateId] ?: State()
        return DiscoveryReputation(
            candidateId = candidateId,
            value = state.value,
            observations = state.observations,
            consecutiveFailures = state.consecutiveFailures,
            quarantinedUntilEpochMs = state.quarantinedUntil,
            eligibleForActivation = state.observations >= 5 && state.value >= 0.80,
        )
    }
}
