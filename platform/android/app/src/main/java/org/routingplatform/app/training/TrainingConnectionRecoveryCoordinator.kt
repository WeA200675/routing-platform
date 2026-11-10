package org.routingplatform.app.training

/**
 * Autonomous connection recovery policy used by the routing runtime.
 *
 * The policy is deliberately transport-agnostic: the host supplies probes and
 * recovery actions (refresh discovery, restart companion, reconnect API, flush
 * FIFO). Navigation remains independent of the training channel.
 */
internal enum class TrainingRecoveryState {
    ONLINE,
    RECONNECTING,
    OFFLINE_BUFFERING,
    CIRCUIT_OPEN,
}

internal data class TrainingConnectionSnapshot(
    val state: TrainingRecoveryState = TrainingRecoveryState.OFFLINE_BUFFERING,
    val consecutiveFailures: Int = 0,
    val nextRetryAtElapsedMs: Long = 0L,
    val lastSuccessAtElapsedMs: Long? = null,
)

internal class TrainingConnectionRecoveryCoordinator(
    private val maxFailuresBeforeCircuitOpen: Int = 5,
    private val baseRetryDelayMs: Long = 1_000L,
    private val maxRetryDelayMs: Long = 60_000L,
) {
    init {
        require(maxFailuresBeforeCircuitOpen > 0)
        require(baseRetryDelayMs > 0L)
        require(maxRetryDelayMs >= baseRetryDelayMs)
    }

    private var snapshot = TrainingConnectionSnapshot()

    fun current(): TrainingConnectionSnapshot = snapshot

    fun onProbeSuccess(nowElapsedMs: Long): TrainingConnectionSnapshot {
        snapshot = TrainingConnectionSnapshot(
            state = TrainingRecoveryState.ONLINE,
            consecutiveFailures = 0,
            nextRetryAtElapsedMs = 0L,
            lastSuccessAtElapsedMs = nowElapsedMs,
        )
        return snapshot
    }

    fun onProbeFailure(nowElapsedMs: Long): TrainingConnectionSnapshot {
        val failures = (snapshot.consecutiveFailures + 1).coerceAtMost(maxFailuresBeforeCircuitOpen)
        val state = if (failures >= maxFailuresBeforeCircuitOpen) {
            TrainingRecoveryState.CIRCUIT_OPEN
        } else {
            TrainingRecoveryState.RECONNECTING
        }
        val exponent = (failures - 1).coerceIn(0, 6)
        val delay = (baseRetryDelayMs * (1L shl exponent)).coerceAtMost(maxRetryDelayMs)
        snapshot = snapshot.copy(
            state = state,
            consecutiveFailures = failures,
            nextRetryAtElapsedMs = nowElapsedMs + delay,
        )
        return snapshot
    }

    fun shouldRetry(nowElapsedMs: Long): Boolean =
        snapshot.state != TrainingRecoveryState.ONLINE &&
            snapshot.state != TrainingRecoveryState.CIRCUIT_OPEN &&
            nowElapsedMs >= snapshot.nextRetryAtElapsedMs

    fun allowHalfOpenProbe(nowElapsedMs: Long): Boolean =
        snapshot.state == TrainingRecoveryState.CIRCUIT_OPEN &&
            nowElapsedMs >= snapshot.nextRetryAtElapsedMs

    fun beginHalfOpenProbe(nowElapsedMs: Long): TrainingConnectionSnapshot {
        require(allowHalfOpenProbe(nowElapsedMs))
        snapshot = snapshot.copy(
            state = TrainingRecoveryState.RECONNECTING,
            nextRetryAtElapsedMs = nowElapsedMs,
        )
        return snapshot
    }

    fun reset() {
        snapshot = TrainingConnectionSnapshot()
    }
}
