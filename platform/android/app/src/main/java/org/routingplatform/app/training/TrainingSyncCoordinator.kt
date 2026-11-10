package org.routingplatform.app.training

internal class TrainingSyncCoordinator(
    private val bridge: TrainingRuntimeBridge,
) {
    fun sync(
        networkAvailable: Boolean,
        navigationActive: Boolean,
        queuedEvents: Int,
        batteryPercent: Int,
    ): TrainingSyncResult {
        val decision = TrainingSyncPolicy.decide(
            networkAvailable = networkAvailable,
            navigationActive = navigationActive,
            queuedEvents = queuedEvents,
            batteryPercent = batteryPercent,
        )
        if (!decision.shouldUpload) {
            return TrainingSyncResult.Skipped(decision.reason)
        }
        return when (val result = bridge.flush(maxEvents = decision.maxEvents)) {
            UploadResult.NothingToUpload -> TrainingSyncResult.Skipped("journal leer")
            is UploadResult.Uploaded -> TrainingSyncResult.Uploaded(result.count)
            is UploadResult.Failed -> TrainingSyncResult.Failed(result.reason)
        }
    }
}

sealed interface TrainingSyncResult {
    data class Uploaded(val count: Int) : TrainingSyncResult
    data class Failed(val reason: String) : TrainingSyncResult
    data class Skipped(val reason: String) : TrainingSyncResult
}
