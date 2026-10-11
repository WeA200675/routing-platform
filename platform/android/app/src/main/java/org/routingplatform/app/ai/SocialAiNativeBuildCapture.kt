package org.routingplatform.app.ai

/** Exact native outputs produced from the pinned runtime source build. */
data class SocialAiNativeBuildCapture(
    val runtimeCommitSha: String,
    val ndkVersion: String,
    val cmakeVersion: String,
    val abi: String,
    val artifactFilename: String,
    val artifactSha256: String,
    val artifactBytes: Long,
) {
    init {
        require(runtimeCommitSha.matches(Regex("[A-Fa-f0-9]{40}")))
        require(ndkVersion.matches(Regex("[0-9]+(\\.[0-9]+)+")))
        require(cmakeVersion.matches(Regex("[0-9]+(\\.[0-9]+)+")))
        require(abi == "arm64-v8a")
        require(artifactFilename == "libsocial_ai_llama.so")
        require(artifactSha256.matches(Regex("[A-Fa-f0-9]{64}")))
        require(artifactBytes > 0)
    }
}

object SocialAiNativeBuildCaptureGate {
    fun requireMatches(
        capture: SocialAiNativeBuildCapture,
        artifactRecord: SocialAiCandidateArtifactRecord,
        expectedNdkVersion: String,
        expectedCmakeVersion: String,
    ) {
        require(capture.runtimeCommitSha.equals(artifactRecord.runtimeCommitSha, ignoreCase = true))
        require(capture.ndkVersion == expectedNdkVersion)
        require(capture.cmakeVersion == expectedCmakeVersion)
        require(capture.abi == "arm64-v8a")
        require(capture.artifactFilename == "libsocial_ai_llama.so")
        require(capture.artifactBytes > 0)
    }
}
