package org.routingplatform.app.training

import android.content.Context

class TrainingRuntimeStateStore(context: Context) {
    private val preferences = context.getSharedPreferences("training-runtime-state", Context.MODE_PRIVATE)

    fun save(config: TrainingRuntimeConfig, artifact: ActiveTrainingArtifact) {
        preferences.edit()
            .putString("runtime_mode", config.runtimeMode)
            .putString("active_artifact", config.activeArtifact)
            .putBoolean("live_data_enabled", config.liveDataEnabled)
            .putBoolean("offline_fallback_enabled", config.offlineFallbackEnabled)
            .putBoolean("recovery_enabled", config.recoveryEnabled)
            .putString("artifact_id", artifact.artifactId)
            .putString("artifact_version", artifact.version)
            .putString("artifact_stage", artifact.stage)
            .putFloat("artifact_quality", artifact.qualityScore.toFloat())
            .putInt("artifact_regressions", artifact.regressionCount)
            .apply()
    }

    fun load(): Pair<TrainingRuntimeConfig, ActiveTrainingArtifact>? {
        val mode = preferences.getString("runtime_mode", null) ?: return null
        val artifactId = preferences.getString("artifact_id", null) ?: return null
        return TrainingRuntimeConfig(
            runtimeMode = mode,
            activeArtifact = preferences.getString("active_artifact", artifactId) ?: artifactId,
            liveDataEnabled = preferences.getBoolean("live_data_enabled", false),
            offlineFallbackEnabled = preferences.getBoolean("offline_fallback_enabled", true),
            recoveryEnabled = preferences.getBoolean("recovery_enabled", true),
        ) to ActiveTrainingArtifact(
            artifactId = artifactId,
            version = preferences.getString("artifact_version", "unknown") ?: "unknown",
            stage = preferences.getString("artifact_stage", "ACTIVE") ?: "ACTIVE",
            qualityScore = preferences.getFloat("artifact_quality", 0f).toDouble(),
            regressionCount = preferences.getInt("artifact_regressions", 0),
        )
    }
}
