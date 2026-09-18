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
        if (provenance.runtime.revision.equals("main", true) ||
            provenance.runtime.revision.equals("master", true) ||
            provenance.model.revision.equals("main", true) ||
            provenance.model.revision.equals("master", true)
        ) {
            violations += "Floating revisions are forbidden."
        }
        return violations
    }

    fun requireAdmitted(provenance: SocialAiArtifactProvenance) {
        val violations = validate(provenance)
        require(violations.isEmpty()) { violations.joinToString(" ") }
    }
}
