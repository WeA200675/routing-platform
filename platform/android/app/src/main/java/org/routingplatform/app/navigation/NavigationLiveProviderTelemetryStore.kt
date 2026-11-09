package org.routingplatform.app.navigation

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

internal class NavigationLiveProviderTelemetryStore {
    private val latest = ConcurrentHashMap<String, NavigationProviderObservation>()
    private val totalCalls = AtomicLong(0)
    private val failedCalls = AtomicLong(0)

    fun record(observation: NavigationProviderObservation) {
        latest[observation.providerId] = observation
        totalCalls.incrementAndGet()
        if (!observation.success) failedCalls.incrementAndGet()
    }

    fun latest(): List<NavigationProviderObservation> =
        latest.values.sortedBy { it.providerId }

    fun calls(): Long = totalCalls.get()

    fun failures(): Long = failedCalls.get()

    fun errorRate(): Double =
        if (totalCalls.get() == 0L) 0.0 else failures().toDouble() / totalCalls.get()
}
