package org.routingplatform.app.training

import android.content.Context
import org.routingplatform.app.intelligence.ProductionDiscoveryController
import org.routingplatform.app.navigation.GovernedNavigationLiveProviderFactory
import org.routingplatform.app.navigation.NavigationLiveEvent
import org.routingplatform.app.navigation.NavigationLiveEventProvider
import org.routingplatform.app.navigation.NavigationLiveProviderConfig
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
        require(apiBaseUrl.startsWith("https://")) { "Training API muss HTTPS verwenden" }
        return createInternal(apiBaseUrl, liveEventProviders, journalMaxBytes, eventStore)
    }

    fun createFromManifest(
        context: Context,
        apiBaseUrl: String,
        journalMaxBytes: Long = 8L * 1024L * 1024L,
        signatureVerifier: ((List<NavigationLiveEvent>) -> Boolean)? = null,
    ): NavigationRuntimeTrainingSessionHandle {
        val config = NavigationLiveProviderConfig.fromManifest(context)
        val eventStore = PersistentFifoJournal(
            maxBytes = journalMaxBytes,
            persistence = AndroidFifoJournalPersistence(context),
        )
        val canGovern = signatureVerifier != null &&
            config.discoveryManifestKeyId != null &&
            config.discoveryTrustedManifestKeys.isNotEmpty() &&
            config.hasAnyProvider()
        if (!canGovern) {
            return createInternal(
                apiBaseUrl,
                NavigationLiveProviderFactory.fromManifest(context),
                journalMaxBytes,
                eventStore,
            )
        }

        lateinit var bridge: TrainingRuntimeBridge
        val controller = ProductionDiscoveryController(
            allowedHosts = config.providerHosts(),
            trustedManifestKeys = config.discoveryTrustedManifestKeys,
            onTrainingRecord = { record ->
                DiscoveryTrainingEventSink(bridge).record(record)
            },
        )
        val providers = GovernedNavigationLiveProviderFactory.fromManifest(
            context = context,
            controller = controller,
            manifestKeyId = config.discoveryManifestKeyId,
            signatureVerifier = signatureVerifier,
        )
        return createInternal(
            apiBaseUrl,
            providers,
            journalMaxBytes,
            eventStore,
        ).also { bridge = it.bridge }
    }

    private fun createInternal(
        apiBaseUrl: String,
        liveEventProviders: List<NavigationLiveEventProvider>,
        journalMaxBytes: Long,
        eventStore: TrainingEventStore,
    ): NavigationRuntimeTrainingSessionHandle {
        require(apiBaseUrl.startsWith("https://")) { "Training API muss HTTPS verwenden" }
        val apiClient = TrainingCenterApiClient(apiBaseUrl)
        val bridge = TrainingRuntimeBridge(
            telemetry = NavigationPerformanceTelemetry(),
            journal = eventStore,
            apiClient = apiClient,
        )
        val session = NavigationRuntimeTrainingSession(
            liveEventProvider = ResilientLiveEventProvider(liveEventProviders),
            bridge = bridge,
            syncCoordinator = TrainingSyncCoordinator(bridge),
        )
        return NavigationRuntimeTrainingSessionHandle(session, bridge)
    }
}

internal class NavigationRuntimeTrainingSessionHandle(
    val session: NavigationRuntimeTrainingSession,
    val bridge: TrainingRuntimeBridge,
) : AutoCloseable {
    override fun close() = session.close()
}
