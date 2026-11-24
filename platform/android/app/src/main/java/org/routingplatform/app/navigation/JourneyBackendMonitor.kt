package org.routingplatform.app.navigation

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Non-blocking status probe. The navigation runtime is never blocked by
 * backend connectivity. All failures degrade to an offline status.
 */
internal class JourneyBackendMonitor(
    private val client: JourneyBackendClient,
    private val clockMs: () -> Long = System::currentTimeMillis,
    private val executor: ExecutorService = Executors.newSingleThreadExecutor(),
) {
    private val inFlight = AtomicBoolean(false)
    private val lock = Any()
    private var nextAttemptMs = 0L
    private var failures = 0

    @Volatile
    private var lastStatus = JourneyBackendStatus.offline("not_checked")

    fun snapshot(): JourneyBackendStatus = lastStatus

    fun refresh(onResult: (JourneyBackendStatus) -> Unit = {}) {
        val now = clockMs()
        synchronized(lock) {
            if (now < nextAttemptMs || !inFlight.compareAndSet(false, true)) return
        }
        try {
            executor.execute {
                val status = try {
                    client.status()
                } catch (_: Exception) {
                    JourneyBackendStatus.offline()
                }
                synchronized(lock) {
                    failures = if (status.reachable) 0 else (failures + 1).coerceAtMost(8)
                    val delay = if (status.reachable) 60_000L
                    else (1_000L * (1 shl failures)).coerceAtMost(120_000L)
                    nextAttemptMs = clockMs() + delay
                    lastStatus = status
                    inFlight.set(false)
                }
                try { onResult(status) } catch (_: Exception) { /* observer cannot stop navigation */ }
            }
        } catch (_: RuntimeException) {
            inFlight.set(false)
            lastStatus = JourneyBackendStatus.offline("executor_unavailable")
        }
    }

    fun close() {
        executor.shutdownNow()
    }
}
