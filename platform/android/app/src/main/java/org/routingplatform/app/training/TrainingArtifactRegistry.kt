package org.routingplatform.app.training

enum class TrainingArtifactStage {
    SHADOW,
    CANARY,
    ACTIVE,
    ROLLED_BACK,
    QUARANTINED,
}

data class TrainingArtifact(
    val id: String,
    val version: String,
    val stage: TrainingArtifactStage,
    val qualityScore: Double,
    val regressionCount: Int,
    val signature: String,
)

class TrainingArtifactRegistry {
    private val artifacts = LinkedHashMap<String, TrainingArtifact>()
    private var activeId: String? = null
    private var rollbackId: String? = null

    fun register(artifact: TrainingArtifact): Boolean {
        if (artifact.id.isBlank() ||
            artifact.version.isBlank() ||
            artifact.signature.isBlank() ||
            artifact.qualityScore !in 0.0..1.0 ||
            artifact.regressionCount < 0
        ) return false
        artifacts[artifact.id] = artifact.copy(stage = TrainingArtifactStage.SHADOW)
        return true
    }

    fun promoteCanary(id: String): Boolean =
        transition(id, TrainingArtifactStage.CANARY)

    fun activate(id: String): Boolean {
        val candidate = artifacts[id] ?: return false
        if (candidate.stage != TrainingArtifactStage.CANARY ||
            candidate.regressionCount > 0
        ) return false
        rollbackId = activeId
        artifacts[id] = candidate.copy(stage = TrainingArtifactStage.ACTIVE)
        activeId = id
        return true
    }

    fun rollback(): Boolean {
        val previous = rollbackId ?: return false
        val current = activeId
        if (current != null) {
            artifacts[current]?.let {
                artifacts[current] = it.copy(stage = TrainingArtifactStage.ROLLED_BACK)
            }
        }
        artifacts[previous]?.let {
            artifacts[previous] = it.copy(stage = TrainingArtifactStage.ACTIVE)
        }
        activeId = previous
        rollbackId = null
        return true
    }

    fun active(): TrainingArtifact? = activeId?.let(artifacts::get)

    private fun transition(id: String, stage: TrainingArtifactStage): Boolean {
        val artifact = artifacts[id] ?: return false
        artifacts[id] = artifact.copy(stage = stage)
        return true
    }
}
