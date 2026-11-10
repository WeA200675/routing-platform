package org.routingplatform.app.navigation

import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Lifecycle-bound coordinator for durable diagnostics uploads.
 * It never blocks navigation and backs off after failures.
 */
class NavigationDiagnosticsUploadCoordinator(
    private val worker: NavigationDiagnosticsUploadWorker,
    private val baseUrlProvider: () -> String?,
    private val onResult: (NavigationDiagnosticsUploadResult) -> Unit = {},
) {
    private val executor = Executors.newSingleThreadScheduledExecutor()
    private val running = AtomicBoolean(false)
    private var task: ScheduledFuture<*>? = null
    private var delaySeconds = INITIAL_DELAY_SECONDS

    @Synchronized
    fun start() {
        if (!running.compareAndSet(false, true)) return
        delaySeconds = INITIAL_DELAY_SECONDS
        schedule(0L)
    }

    fun triggerNow() {
        synchronized(this) { schedule(0L) }
    }

    @Synchronized
    fun stop() {
        running.set(false)
        task?.cancel(false)
        task = null
    }

    fun close() {
        stop()
        executor.shutdownNow()
    }

    @Synchronized
    private fun schedule(delay: Long) {
        if (!running.get()) return
        task?.cancel(false)
        task = executor.schedule({ runOnce() }, delay, TimeUnit.SECONDS)
    }

    private fun runOnce() {
        if (!running.get()) return
        val result = baseUrlProvider()?.let(worker::uploadNext)
            ?: NavigationDiagnosticsUploadResult.Failed("Kein Trainingsendpunkt verfügbar")
        onResult(result)
        synchronized(this) {
            if (!running.get()) return
            when (result) {
                NavigationDiagnosticsUploadResult.NothingPending,
                is NavigationDiagnosticsUploadResult.Uploaded -> {
                    delaySeconds = INITIAL_DELAY_SECONDS
                    schedule(SUCCESS_INTERVAL_SECONDS)
                }
                is NavigationDiagnosticsUploadResult.Failed -> {
                    delaySeconds = (delaySeconds * 2).coerceAtMost(MAX_BACKOFF_SECONDS)
                    schedule(delaySeconds)
                }
            }
        }
    }

    private companion object {
        const val INITIAL_DELAY_SECONDS = 5L
        const val SUCCESS_INTERVAL_SECONDS = 30L
        const val MAX_BACKOFF_SECONDS = 300L
    }
}
