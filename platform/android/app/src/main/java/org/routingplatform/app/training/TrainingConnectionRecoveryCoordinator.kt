package org.routingplatform.app.training

/**
 * Autonomous connection recovery policy used by the routing runtime.
 *
 * The policy is deliberately transport-agnostic: the host supplies probes and
 * recovery actions (refresh discovery, restart companion, reconnect API, flush
 * FIFO). Navigation remains independent of the training channel.
 */
enum class TrainingConnectionState {
    ONLINE,
    RECONNECTING,
    OFFLINE_BUFFERING,
    CIRCUIT_OPEN,
}

data class TrainingConnectionSnapshot(
    val state: TrainingConnectionState = TrainingConnectionState.OFFLINE_BUFFERING,
    val consecutiveFailures: Int = 0,
    val nextRetryAtElapsedMs: Long = 0L,
    val lastSuccessAtElapsedMs: Long? = null,
)

class TrainingConnectionRecoveryCoordinator(
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
            state = TrainingConnectionState.ONLINE,
            consecutiveFailures = 0,
            nextRetryAtElapsedMs = 0L,
            lastSuccessAtElapsedMs = nowElapsedMs,
        )
        return snapshot
    }

    fun onProbeFailure(nowElapsedMs: Long): TrainingConnectionSnapshot {
        val failures = (snapshot.consecutiveFailures + 1).coerceAtMost(maxFailuresBeforeCircuitOpen)
        val state = if (failures >= maxFailuresBeforeCircuitOpen) {
            TrainingConnectionState.CIRCUIT_OPEN
        } else {
            TrainingConnectionState.RECONNECTING
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
        snapshot.state != TrainingConnectionState.ONLINE &&
            snapshot.state != TrainingConnectionState.CIRCUIT_OPEN &&
            nowElapsedMs >= snapshot.nextRetryAtElapsedMs

    fun allowHalfOpenProbe(nowElapsedMs: Long): Boolean =
        snapshot.state == TrainingConnectionState.CIRCUIT_OPEN &&
            nowElapsedMs >= snapshot.nextRetryAtElapsedMs

    fun beginHalfOpenProbe(nowElapsedMs: Long): TrainingConnectionSnapshot {
        require(allowHalfOpenProbe(nowElapsedMs))
        snapshot = snapshot.copy(
            state = TrainingConnectionState.RECONNECTING,
            nextRetryAtElapsedMs = nowElapsedMs,
        )
        return snapshot
    }

    fun reset() {
        snapshot = TrainingConnectionSnapshot()
    }
}
