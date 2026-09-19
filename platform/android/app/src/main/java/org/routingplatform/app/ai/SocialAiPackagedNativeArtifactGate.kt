package org.routingplatform.app.ai

/** Release-time hashes independently computed from packaged APK native entries. */
data class SocialAiPackagedNativeArtifacts(
    val sha256ByAbi: Map<String, String>,
) {
    init {
        require(sha256ByAbi.isNotEmpty())
        require(sha256ByAbi.keys.all { it in setOf("arm64-v8a", "x86_64") })
        require(sha256ByAbi.values.all { it.matches(Regex("[A-Fa-f0-9]{64}")) })
    }
}

object SocialAiPackagedNativeArtifactGate {
    fun requireAdmitted(
        packaged: SocialAiPackagedNativeArtifacts,
        manifest: SocialAiRuntimeReleaseManifest,
    ) {
        require(packaged.sha256ByAbi.keys == manifest.runtimeArtifactSha256ByAbi.keys) {
            "Packaged native ABI set does not match the reviewed release manifest."
        }
        require(packaged.sha256ByAbi.all { (abi, sha) ->
            sha.equals(manifest.runtimeArtifactSha256ByAbi.getValue(abi), ignoreCase = true)
        }) {
            "Packaged native artifact SHA-256 does not match the reviewed release manifest."
        }
    }
}
