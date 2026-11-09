package org.routingplatform.app.navigation

import android.os.Handler
import android.os.Looper
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Fans one navigation request out to several Valhalla route families.
 *
 * Every candidate is evaluated independently. A failed family is discarded,
 * while a successful family remains eligible; if all families fail the original
 * error is returned. Cancellation is terminal and prevents late callbacks.
 */
class ParallelNavigationRouteSource(
    private val delegateFactory: (NavigationRouteFamily) -> NavigationRouteSource,
    private val families: List<NavigationRouteFamily> = listOf(
        NavigationRouteFamily.ProfileOptimal,
        NavigationRouteFamily.ProfileFastest,
        NavigationRouteFamily.ProfileShortest,
    ),
) : NavigationRouteSource {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor = Executors.newFixedThreadPool(families.size.coerceAtLeast(1))
    private val closed = AtomicBoolean(false)

    init {
        require(families.isNotEmpty()) { "At least one route family is required." }
        require(families.distinct().size == families.size) { "Route families must be unique." }
    }

    override fun acquire(
        request: NavigationRouteRequest,
        onResult: (Result<NavigationRouteContract>) -> Unit,
    ): NavigationRouteAcquisitionHandle {
        check(!closed.get()) { "Parallel route source is closed." }
        val cancelled = AtomicBoolean(false)
        val remaining = AtomicInteger(families.size)
        val successes = java.util.Collections.synchronizedList(mutableListOf<NavigationRouteContract>())
        val failures = java.util.Collections.synchronizedList(mutableListOf<Throwable>())
        val handles = java.util.Collections.synchronizedList(mutableListOf<NavigationRouteAcquisitionHandle>())
        val completed = AtomicBoolean(false)
        val tasks = families.map { family ->
            executor.submit {
                if (cancelled.get() || closed.get()) return@submit
                val source = runCatching { delegateFactory(family) }
                    .onFailure { failures.add(it) }
                    .getOrNull()
                    ?: run {
                        finish(request, successes, failures, remaining, completed, cancelled, onResult)
                        return@submit
                    }
                try {
                    val handle = source.acquire(request.copy(family = family)) { result ->
                        result.onSuccess { successes.add(it) }.onFailure { failures.add(it) }
                        runCatching { source.close() }
                        finish(request, successes, failures, remaining, completed, cancelled, onResult)
                    }
                    handles.add(handle)
                } catch (error: Throwable) {
                    failures.add(error)
                    runCatching { source.close() }
                    finish(request, successes, failures, remaining, completed, cancelled, onResult)
                }
            }
        }
        return object : NavigationRouteAcquisitionHandle {
            override fun cancel() {
                if (cancelled.compareAndSet(false, true)) {
                    handles.toList().forEach { runCatching { it.cancel() } }
                    tasks.forEach { it.cancel(true) }
                }
            }
        }
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) executor.shutdownNow()
    }

    private fun finish(
        request: NavigationRouteRequest,
        successes: List<NavigationRouteContract>,
        failures: List<Throwable>,
        remaining: AtomicInteger,
        completed: AtomicBoolean,
        cancelled: AtomicBoolean,
        onResult: (Result<NavigationRouteContract>) -> Unit,
    ) {
        if (remaining.decrementAndGet() != 0 || !completed.compareAndSet(false, true) || cancelled.get() || closed.get()) return
        val result = if (successes.isNotEmpty()) {
            val scores = successes.map {
                RouteCandidateScore(
                    routeId = it.routeId,
                    safetyScore = 1.0,
                    timeSeconds = it.durationS,
                    costScore = it.distanceM,
                    confidence = if (it.segmentDataStatus == NavigationRouteSegmentDataStatus.Complete) 0.95 else 0.75,
                )
            }
            val selectedId = NavigationRouteCandidateSelector.select(scores)?.routeId
            successes.firstOrNull { it.routeId == selectedId } ?: successes.minBy { it.durationS }
        } else {
            Result.failure(
                failures.firstOrNull() ?: IllegalStateException("All route families failed for $request")
            )
        }
        mainHandler.post { onResult(runCatching { result as NavigationRouteContract }) }
    }
}
