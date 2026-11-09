package org.routingplatform.app.navigation

import org.routingplatform.app.training.NavigationTrainingRuntimeAdapter
import org.routingplatform.app.training.NavigationTrainingRuntimeScheduler
import org.routingplatform.app.training.TrainingRuntimeBridge
import org.routingplatform.app.training.TrainingSyncCoordinator
import java.util.concurrent.Executors

/**
 * Complete runtime-owned training session. MainActivity creates one instance
 * when a navigation session starts and closes it when navigation stops.
 */
internal class NavigationRuntimeTrainingSession(
    private val liveEventProvider: ResilientLiveEventProvider,
    bridge: TrainingRuntimeBridge,
    syncCoordinator: TrainingSyncCoordinator,
) : AutoCloseable {
    private val executor = Executors.newSingleThreadScheduledExecutor()
    private val scheduler = NavigationRuntimeCycleScheduler(
        executor = executor,
        cycle = NavigationRuntimeCycle(
            liveRuntime = NavigationLiveRuntimeCoordinator(liveEventProvider),
            intelligence = NavigationRuntimeIntelligence(),
        ),
    )
    private val binding = NavigationRuntimeLifecycleBinding(
        trainingScheduler = NavigationTrainingRuntimeScheduler(
            scheduler = scheduler,
            training = NavigationTrainingRuntimeAdapter(
                bridge = bridge,
                syncCoordinator = syncCoordinator,
            ),
        ),
        onRecoveryRequest = { requestId ->
            pendingRecoveryRequestId = requestId
            onRecoveryRequest?.invoke(requestId)
        },
    )

    @Volatile
    var pendingRecoveryRequestId: Long? = null
        private set

    @Volatile
    private var onRecoveryRequest: ((Long) -> Unit)? = null

    fun start(
        inputMapper: NavigationRuntimeCycleInputMapper,
        navigationActive: () -> Boolean,
        sessionId: () -> String?,
        routeId: () -> String?,
        onRecoveryRequest: (Long) -> Unit = {},
        onResult: (NavigationRuntimeCycleResult) -> Unit = {},
    ) {
        this.onRecoveryRequest = onRecoveryRequest
        binding.start(
            inputs = { inputMapper.inputs(navigationActive()) },
            batteryPercent = { inputMapper.inputs(navigationActive()).batteryPercent },
            networkAvailable = { inputMapper.inputs(navigationActive()).networkAvailable },
            navigationActive = navigationActive,
            sessionId = sessionId,
            routeId = routeId,
            onResult = onResult,
        )
    }

    fun stop() {
        binding.stop()
        onRecoveryRequest = null
        pendingRecoveryRequestId = null
    }

    override fun close() {
        stop()
        executor.shutdownNow()
    }
}
