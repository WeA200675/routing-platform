package org.routingplatform.app.training

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

internal sealed interface TrainingConnectionState {
    data object Idle : TrainingConnectionState
    data object Connecting : TrainingConnectionState
    data class Connected(val endpoint: TrainingEndpoint) : TrainingConnectionState
    data class Offline(val reason: String) : TrainingConnectionState
}

/** Owns discovery and keeps the last good endpoint available during outages. */
internal class TrainingConnectionManager(
    context: Context,
    discoveryUrl: String,
    fallbackUrl: String? = null,
    private val executor: Executor = Executors.newSingleThreadExecutor(),
    private val mainHandler: Handler = Handler(Looper.getMainLooper()),
) {
    private val discovery = TrainingEndpointDiscovery(
        context = context.applicationContext,
        discoveryUrl = discoveryUrl,
        fallbackUrl = fallbackUrl,
    )
    private val state = AtomicReference<TrainingConnectionState>(TrainingConnectionState.Idle)
    private val recovery = TrainingConnectionRecoveryCoordinator()
    private val recoveryRunnable = Runnable { refresh() }

    fun currentState(): TrainingConnectionState = state.get()

    fun refresh(onComplete: (TrainingConnectionState) -> Unit = {}) {
        state.set(TrainingConnectionState.Connecting)
        mainHandler.post { onComplete(TrainingConnectionState.Connecting) }
        executor.execute {
            val result = runCatching { TrainingConnectionState.Connected(discovery.resolve()) }
                .getOrElse { TrainingConnectionState.Offline(it.message ?: "Trainingscloud nicht erreichbar") }
            state.set(result)
            val now = android.os.SystemClock.elapsedRealtime()
            if (result is TrainingConnectionState.Connected) {
                recovery.onProbeSuccess(now)
            } else {
                recovery.onProbeFailure(now)
                scheduleRecovery(now)
            }
            mainHandler.post { onComplete(result) }
        }
    }

    /** Starts bounded autonomous recovery after discovery/API outages. */
    fun startAutonomousRecovery() {
        mainHandler.removeCallbacks(recoveryRunnable)
        refresh()
    }

    fun stopAutonomousRecovery() {
        mainHandler.removeCallbacks(recoveryRunnable)
    }

    private fun scheduleRecovery(nowElapsedMs: Long) {
        val snapshot = recovery.current()
        if (snapshot.state == TrainingRecoveryState.CIRCUIT_OPEN) {
            mainHandler.postDelayed(recoveryRunnable, 60_000L)
        } else {
            val delay = (snapshot.nextRetryAtElapsedMs - nowElapsedMs).coerceAtLeast(250L)
            mainHandler.postDelayed(recoveryRunnable, delay)
        }
    }
}
