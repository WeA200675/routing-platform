package org.routingplatform.app.navigation

data class NavigationProviderObservation(
    val providerId: String,
    val capturedAtEpochMs: Long,
    val latencyMs: Long,
    val success: Boolean,
    val eventCount: Int,
    val freshEventCount: Int,
    val schemaValid: Boolean,
    val error: String? = null,
)

/**
 * Observable wrapper for real provider calls. It reports measured runtime
 * evidence without changing the provider result or routing fallback behavior.
 *
 * When a [diagnosticStore] is supplied the same evidence is persisted locally
 * so the dashboard and support tooling can inspect real last-mile behaviour.
 */
internal class InstrumentedNavigationLiveEventProvider(
    private val delegate: NavigationLiveEventProvider,
    private val onObservation: (NavigationProviderObservation) -> Unit,
    private val diagnosticStore: NavigationDiagnosticsStore? = null,
    private val clockEpochMs: () -> Long = { System.currentTimeMillis() },
    private val clockElapsedMs: () -> Long = { System.nanoTime() / 1_000_000L },
) : NavigationLiveEventProvider {
    override val providerId: String = delegate.providerId

    override fun fetch(nowEpochMs: Long): List<NavigationLiveEvent> {
        val started = clockElapsedMs()
        return runCatching { delegate.fetch(nowEpochMs) }
            .onSuccess { events ->
                val observation = NavigationProviderObservation(
                    providerId = providerId,
                    capturedAtEpochMs = clockEpochMs(),
                    latencyMs = (clockElapsedMs() - started).coerceAtLeast(0L),
                    success = true,
                    eventCount = events.size,
                    freshEventCount = events.count { it.isValid(nowEpochMs) },
                    schemaValid = events.all { it.isValid(nowEpochMs) },
                )
                publish(observation)
            }
            .onFailure { error ->
                publish(
                    NavigationProviderObservation(
                        providerId = providerId,
                        capturedAtEpochMs = clockEpochMs(),
                        latencyMs = (clockElapsedMs() - started).coerceAtLeast(0L),
                        success = false,
                        eventCount = 0,
                        freshEventCount = 0,
                        schemaValid = false,
                        error = error.message ?: error.javaClass.simpleName,
                    ),
                )
            }
            .getOrDefault(emptyList())
    }

    private fun publish(observation: NavigationProviderObservation) {
        onObservation(observation)
        diagnosticStore?.record(
            NavigationProviderDiagnostic(
                providerId = observation.providerId,
                capturedAtEpochMs = observation.capturedAtEpochMs,
                success = observation.success,
                eventCount = observation.eventCount,
                acceptedEventCount = observation.freshEventCount,
                latencyMs = observation.latencyMs,
                fallbackUsed = !observation.success || !observation.schemaValid,
                error = observation.error,
            ),
        )
    }
}
