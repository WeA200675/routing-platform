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
 */
internal class InstrumentedNavigationLiveEventProvider(
    private val delegate: NavigationLiveEventProvider,
    private val onObservation: (NavigationProviderObservation) -> Unit,
    private val clockEpochMs: () -> Long = { System.currentTimeMillis() },
    private val clockElapsedMs: () -> Long = { System.nanoTime() / 1_000_000L },
) : NavigationLiveEventProvider {
    override val providerId: String = delegate.providerId

    override fun fetch(nowEpochMs: Long): List<NavigationLiveEvent> {
        val started = clockElapsedMs()
        return runCatching { delegate.fetch(nowEpochMs) }
            .onSuccess { events ->
                val fresh = events.count { it.isValid(nowEpochMs) }
                onObservation(
                    NavigationProviderObservation(
                        providerId = providerId,
                        capturedAtEpochMs = clockEpochMs(),
                        latencyMs = (clockElapsedMs() - started).coerceAtLeast(0L),
                        success = true,
                        eventCount = events.size,
                        freshEventCount = fresh,
                        schemaValid = events.all { it.isValid(nowEpochMs) },
                    ),
                )
            }
            .onFailure { error ->
                onObservation(
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
}
