package org.routingplatform.app.training

import org.routingplatform.app.intelligence.ProductionDiscoveryController

internal object DiscoveryTrainingRuntimeFactory {
    fun createController(
        bridge: TrainingRuntimeBridge,
        trustedHosts: Set<String>,
        trustedManifestKeys: Set<String>,
    ): ProductionDiscoveryController {
        val sink = DiscoveryTrainingEventSink(bridge)
        return ProductionDiscoveryController(
            allowedHosts = trustedHosts,
            trustedManifestKeys = trustedManifestKeys,
            onTrainingRecord = { record ->
                sink.record(record)
            },
        )
    }
}
