package org.routingplatform.app.training

import android.content.Context
import org.routingplatform.app.navigation.NavigationLiveEventProvider
import org.routingplatform.app.navigation.NavigationLiveProviderFactory
import org.routingplatform.app.navigation.ResilientLiveEventProvider
import org.routingplatform.app.navigation.NavigationRuntimeTrainingSession

internal object NavigationTrainingSessionFactory {
    fun create(
        apiBaseUrl: String,
        liveEventProviders: List<NavigationLiveEventProvider> = emptyList(),
        journalMaxBytes: Long = 8L * 1024L * 1024L,
        eventStore: TrainingEventStore = FifoEventJournal(journalMaxBytes),
    ): NavigationRuntimeTrainingSessionHandle {
        require(apiBaseUrl.startsWith("https://")) {
            "Training API muss HTTPS verwenden"
        }
        val apiClient = TrainingCenterApiClient(apiBaseUrl)
        val bridge = TrainingRuntimeBridge(
            telemetry = NavigationPerformanceTelemetry(),
            journal = eventStore,
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
            eventStore = PersistentFifoJournal(
                maxBytes = journalMaxBytes,
                persistence = AndroidFifoJournalPersistence(context),
            ),
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
