package org.routingplatform.app.training

data class ReleaseReadiness(
    val navigationReady: Boolean,
    val liveDataReady: Boolean,
    val fallbackReady: Boolean,
    val storageReady: Boolean,
    val trainingReady: Boolean,
) {
    val isReady: Boolean
        get() = navigationReady &&
            liveDataReady &&
            fallbackReady &&
            storageReady &&
            trainingReady
}

class ReleaseReadinessEvaluator {
    fun evaluate(
        health: TrainingServiceHealth,
        navigationReady: Boolean,
        fallbackReady: Boolean,
        activeArtifact: TrainingArtifact?,
    ): ReleaseReadiness =
        ReleaseReadiness(
            navigationReady = navigationReady,
            liveDataReady = health.liveData != TrainingServiceStatus.OFFLINE,
            fallbackReady = fallbackReady,
            storageReady = health.storage == TrainingServiceStatus.HEALTHY,
            trainingReady = activeArtifact != null &&
                activeArtifact.stage == TrainingArtifactStage.ACTIVE &&
                activeArtifact.regressionCount == 0,
        )
}
