package org.routingplatform.app.ai

data class SocialAiArtifactProvenance(
    val runtime: OpenSourceComponentMetadata,
    val model: LocalModelArtifactMetadata,
    val modelFormat: String,
    val quantization: String,
    val expectedModelBytes: Long,
    val minimumRamBytes: Long,
) {
    init {
        require(modelFormat == "GGUF") { "Only the reviewed GGUF boundary is admitted." }
        require(quantization.isNotBlank() && quantization.length <= 64)
        require(expectedModelBytes > 0)
        require(minimumRamBytes > 0)
    }
}

object SocialAiOpenSourceAdmission {
    private val immutableGitRevision = Regex("[a-fA-F0-9]{40}")
    private val immutableModelRevision = Regex("[A-Za-z0-9][A-Za-z0-9._+-]{6,127}")
    private val forbiddenFloatingRevisions = setOf(
        "main", "master", "head", "latest", "stable", "dev", "develop", "nightly", "snapshot"
    )
    private val allowedRuntimeLicenses = setOf("MIT", "Apache-2.0", "BSD-2-Clause", "BSD-3-Clause")
    private val allowedModelLicenses = setOf("MIT", "Apache-2.0", "BSD-2-Clause", "BSD-3-Clause")

    fun validate(provenance: SocialAiArtifactProvenance): List<String> {
        val violations = mutableListOf<String>()
        if (provenance.runtime.licenseSpdx !in allowedRuntimeLicenses) {
            violations += "Runtime license is not in the reviewed permissive set."
        }
        if (provenance.model.licenseSpdx !in allowedModelLicenses) {
            violations += "Model license is not in the reviewed permissive set."
        }
        val runtimeRevision = provenance.runtime.revision.trim()
        val modelRevision = provenance.model.revision.trim()
        if (runtimeRevision.lowercase() in forbiddenFloatingRevisions ||
            !immutableGitRevision.matches(runtimeRevision)
        ) {
            violations += "Runtime revision must be an immutable 40-character Git commit SHA."
        }
        if (modelRevision.lowercase() in forbiddenFloatingRevisions ||
            !immutableModelRevision.matches(modelRevision)
        ) {
            violations += "Model revision must be an explicit immutable revision identifier."
        }
        return violations
    }

    fun requireAdmitted(provenance: SocialAiArtifactProvenance) {
        val violations = validate(provenance)
        require(violations.isEmpty()) { violations.joinToString(" ") }
    }
}
