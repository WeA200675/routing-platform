package org.routingplatform.app.navigation

import org.routingplatform.app.intelligence.DiscoveryCandidate
import org.routingplatform.app.intelligence.DiscoveryCandidateKind
import org.routingplatform.app.intelligence.ProductionDiscoveryController

/**
 * Ensures live provider responses pass the production discovery gates before
 * they can influence routing. Rejected or quarantined responses are fail-closed.
 */
internal class GovernedNavigationLiveEventProvider(
    private val delegate: NavigationLiveEventProvider,
    private val controller: ProductionDiscoveryController,
    private val candidate: DiscoveryCandidate,
    private val manifestKeyId: String?,
    private val signatureVerifier: (List<NavigationLiveEvent>) -> Boolean = { false },
    private val clockEpochMs: () -> Long = { System.currentTimeMillis() },
    private val clockElapsedMs: () -> Long = { System.nanoTime() / 1_000_000L },
) : NavigationLiveEventProvider {
    override val providerId: String = delegate.providerId

    init {
        controller.register(candidate)
    }

    override fun fetch(nowEpochMs: Long): List<NavigationLiveEvent> {
        val start = clockElapsedMs()
        val result = runCatching { delegate.fetch(nowEpochMs) }
        val events = result.getOrDefault(emptyList())
        val observation = controller.observe(
            candidate = candidate,
            manifestKeyId = manifestKeyId,
            signatureVerified = result.isSuccess && signatureVerifier(events),
            capturedAtEpochMs = clockEpochMs(),
            latencyMs = (clockElapsedMs() - start).coerceAtLeast(0L),
            success = result.isSuccess,
            eventCount = events.size,
            freshEventCount = events.count { it.isValid(nowEpochMs) },
            schemaValid = result.isSuccess && events.all { it.isValid(nowEpochMs) },
            error = result.exceptionOrNull()?.message,
        )
        return if (observation?.acceptedForProduction == true) events else emptyList()
    }
}
