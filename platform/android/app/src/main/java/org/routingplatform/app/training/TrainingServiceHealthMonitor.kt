package org.routingplatform.app.training

enum class TrainingServiceStatus {
    HEALTHY,
    DEGRADED,
    OFFLINE,
}

data class TrainingServiceHealth(
    val api: TrainingServiceStatus,
    val discovery: TrainingServiceStatus,
    val liveData: TrainingServiceStatus,
    val storage: TrainingServiceStatus,
    val training: TrainingServiceStatus,
) {
    val overall: TrainingServiceStatus
        get() = when {
            listOf(api, discovery, liveData, storage, training)
                .all { it == TrainingServiceStatus.HEALTHY } -> TrainingServiceStatus.HEALTHY
            listOf(api, discovery, storage)
                .any { it == TrainingServiceStatus.OFFLINE } -> TrainingServiceStatus.OFFLINE
            else -> TrainingServiceStatus.DEGRADED
        }
}

class TrainingServiceHealthMonitor {
    fun evaluate(
        apiReachable: Boolean,
        discoveryReachable: Boolean,
        liveDataReachable: Boolean,
        storageWithinLimit: Boolean,
        trainingActive: Boolean,
    ): TrainingServiceHealth =
        TrainingServiceHealth(
            api = coreStatus(apiReachable),
            discovery = coreStatus(discoveryReachable),
            liveData = optionalStatus(liveDataReachable),
            storage = coreStatus(storageWithinLimit),
            training = optionalStatus(trainingActive),
        )

    private fun coreStatus(ok: Boolean): TrainingServiceStatus =
        if (ok) TrainingServiceStatus.HEALTHY else TrainingServiceStatus.OFFLINE

    private fun optionalStatus(ok: Boolean): TrainingServiceStatus =
        if (ok) TrainingServiceStatus.HEALTHY else TrainingServiceStatus.DEGRADED
}
