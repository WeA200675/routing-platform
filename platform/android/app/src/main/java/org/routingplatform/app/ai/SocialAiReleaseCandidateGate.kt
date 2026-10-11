package org.routingplatform.app.ai

/**
 * A release candidate must use real reviewed values. Fixture/sentinel metadata
 * is deliberately rejected before it can be treated as distributable evidence.
 */
object SocialAiReleaseCandidateGate {
    private val forbiddenHosts = setOf("example.invalid", "localhost", "127.0.0.1")
    private val repeatedDigest = Regex("^([A-Fa-f0-9])\\1{63}$")

    fun requireRealCandidate(
        attestation: SocialAiRuntimeAttestation,
        evidence: SocialAiRuntimeBuildEvidence,
        manifest: SocialAiRuntimeReleaseManifest,
    ) {
        listOf(
            attestation.provenance.runtime.sourceUrl,
            attestation.provenance.model.sourceUrl,
        ).forEach { source ->
            val host = java.net.URI(source).host?.lowercase()
            require(host != null && host !in forbiddenHosts) {
                "Release candidate provenance must reference a real reviewed upstream source."
            }
        }
        val digests = buildList {
            add(attestation.provenance.model.sha256)
            add(evidence.sourceSha256)
            add(evidence.sbomSha256)
            addAll(evidence.artifactSha256ByAbi.values)
            add(manifest.modelSha256)
            add(manifest.sbomSha256)
            addAll(manifest.runtimeArtifactSha256ByAbi.values)
        }
        require(digests.none(repeatedDigest::matches)) {
            "Release candidate contains fixture/sentinel SHA-256 values."
        }
    }
}
