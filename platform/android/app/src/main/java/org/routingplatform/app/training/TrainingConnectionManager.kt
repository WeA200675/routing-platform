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

    fun currentState(): TrainingConnectionState = state.get()

    fun refresh(onComplete: (TrainingConnectionState) -> Unit = {}) {
        state.set(TrainingConnectionState.Connecting)
        mainHandler.post { onComplete(TrainingConnectionState.Connecting) }
        executor.execute {
            val result = runCatching { TrainingConnectionState.Connected(discovery.resolve()) }
                .getOrElse { TrainingConnectionState.Offline(it.message ?: "Trainingscloud nicht erreichbar") }
            state.set(result)
            mainHandler.post { onComplete(result) }
        }
    }
}
