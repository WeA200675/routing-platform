package org.routingplatform.app.training

import android.content.Context
import org.routingplatform.app.navigation.NavigationLiveEventProvider
import org.routingplatform.app.navigation.NavigationLiveProviderFactory
import org.routingplatform.app.navigation.ResilientLiveEventProvider
import org.routingplatform.app.navigation.NavigationRuntimeTrainingSession

/**
 * Builds the production training runtime with bounded local storage and the
 * currently resolved HTTPS API endpoint. Providers can be extended without
 * changing the navigation lifecycle.
 */
internal object NavigationTrainingSessionFactory {
    fun create(
        apiBaseUrl: String,
        liveEventProviders: List<NavigationLiveEventProvider> = emptyList(),
        journalMaxBytes: Long = 8L * 1024L * 1024L,
    ): NavigationRuntimeTrainingSessionHandle {
        require(apiBaseUrl.startsWith("https://")) {
            "Training API muss HTTPS verwenden"
        }
        val journal = FifoEventJournal(journalMaxBytes)
        val apiClient = TrainingCenterApiClient(apiBaseUrl)
        val bridge = TrainingRuntimeBridge(
            telemetry = NavigationPerformanceTelemetry(),
            journal = journal,
            apiClient = apiClient,
        )
        val sync = TrainingSyncCoordinator(bridge)
        val session = NavigationRuntimeTrainingSession(
            liveEventProvider = ResilientLiveEventProvider(liveEventProviders),
            bridge = bridge,
            syncCoordinator = sync,
        )
        return NavigationRuntimeTrainingSessionHandle(session, bridge)
    }

    fun createFromManifest(
        context: Context,
        apiBaseUrl: String,
        journalMaxBytes: Long = 8L * 1024L * 1024L,
    ): NavigationRuntimeTrainingSessionHandle =
        create(
            apiBaseUrl = apiBaseUrl,
            liveEventProviders = NavigationLiveProviderFactory.fromManifest(context),
            journalMaxBytes = journalMaxBytes,
        )
}

internal class NavigationRuntimeTrainingSessionHandle(
    val session: NavigationRuntimeTrainingSession,
    val bridge: TrainingRuntimeBridge,
) : AutoCloseable {
    override fun close() {
        session.close()
    }
}
