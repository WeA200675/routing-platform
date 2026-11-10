package org.routingplatform.app.training

import org.routingplatform.app.navigation.NavigationRuntimeCycleInputs
import org.routingplatform.app.navigation.NavigationRuntimeCycleResult
import org.routingplatform.app.navigation.NavigationRuntimeCycleScheduler

/**
 * Lifecycle-owned scheduler that runs the navigation intelligence cycle and
 * forwards every real decision to the training journal/sync boundary.
 */
internal class NavigationTrainingRuntimeScheduler(
    private val scheduler: NavigationRuntimeCycleScheduler,
    private val training: NavigationTrainingRuntimeAdapter,
) {
    fun start(
        inputs: () -> NavigationRuntimeCycleInputs,
        batteryPercent: () -> Int,
        networkAvailable: () -> Boolean,
        navigationActive: () -> Boolean,
        sessionId: () -> String? = { null },
        routeId: () -> String? = { null },
        onResult: (NavigationRuntimeCycleResult) -> Unit = {},
    ) {
        scheduler.start(
            inputs = inputs,
            onResult = { result ->
                onResult(result)
                training.onCycle(
                    result = result,
                    networkAvailable = networkAvailable(),
                    navigationActive = navigationActive(),
                    batteryPercent = batteryPercent(),
                    sessionId = sessionId(),
                    routeId = routeId(),
                )
            },
        )
    }

    fun stop() {
        scheduler.stop()
    }
}
