package org.routingplatform.app.training

import android.content.Context
import org.routingplatform.app.navigation.NavigationRuntimeCycleInputMapper
import org.routingplatform.app.navigation.NavigationRuntimeCycleResult
import org.routingplatform.app.navigation.NavigationRuntimeTrainingSession
import org.routingplatform.app.navigation.ResilientLiveEventProvider

/**
 * Application-facing host for one navigation session. It resolves the current
 * training API first, then creates the bounded runtime session with configured
 * live providers. If discovery is offline, navigation remains usable and the
 * session is simply not started.
 */
internal class NavigationTrainingRuntimeHost(
    context: Context,
    discoveryUrl: String,
    private val onConnectionState: (TrainingConnectionState) -> Unit = {},
) : AutoCloseable {
    private val connection = TrainingConnectionManager(
        context = context,
        discoveryUrl = discoveryUrl,
    )
    private val appContext = context.applicationContext
    private var handle: NavigationRuntimeTrainingSessionHandle? = null

    fun start(
        inputMapper: NavigationRuntimeCycleInputMapper,
        navigationActive: () -> Boolean,
        sessionId: () -> String?,
        routeId: () -> String?,
        onResult: (NavigationRuntimeCycleResult) -> Unit = {},
    ) {
        connection.refresh { state ->
            onConnectionState(state)
            val endpoint = (state as? TrainingConnectionState.Connected)?.endpoint
                ?: return@refresh
            handle?.close()
            handle = NavigationTrainingSessionFactory.createFromManifest(
                context = appContext,
                apiBaseUrl = endpoint.apiUrl,
            )
            handle?.session?.start(
                inputMapper = inputMapper,
                navigationActive = navigationActive,
                sessionId = sessionId,
                routeId = routeId,
                onResult = onResult,
            )
        }
    }

    fun stop() {
        handle?.close()
        handle = null
    }

    override fun close() {
        stop()
    }
}
