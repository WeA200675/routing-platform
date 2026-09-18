package org.routingplatform.app.ai

/**
 * Deterministic ownership gate for a loaded local model. A generation may only
 * execute while its lease still matches the currently loaded model revision.
 */
data class SocialAiModelLease(
    val modelId: String,
    val revision: String,
    val sha256: String,
    val generation: Long,
) {
    init {
        require(modelId.isNotBlank())
        require(revision.isNotBlank())
        require(sha256.matches(Regex("[A-Fa-f0-9]{64}")))
        require(generation > 0)
    }
}

class SocialAiModelLeaseRegistry {
    private var nextGeneration = 1L
    private var active: SocialAiModelLease? = null

    @Synchronized
    fun activate(metadata: LocalModelArtifactMetadata): SocialAiModelLease {
        val lease = SocialAiModelLease(
            modelId = metadata.modelId,
            revision = metadata.revision,
            sha256 = metadata.sha256.lowercase(),
            generation = nextGeneration++,
        )
        active = lease
        return lease
    }

    @Synchronized
    fun revoke() {
        active = null
    }

    @Synchronized
    fun requireActive(lease: SocialAiModelLease) {
        check(active == lease) { "Local model lease is stale or revoked." }
    }

    @Synchronized
    fun current(): SocialAiModelLease? = active
}
