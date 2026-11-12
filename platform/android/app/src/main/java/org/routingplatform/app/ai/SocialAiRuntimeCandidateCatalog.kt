package org.routingplatform.app.ai

/** Reviewed candidate metadata. This is not release admission until immutable hashes are filled. */
data class SocialAiRuntimeCandidate(
    val runtimeComponentId: String,
    val runtimeLicenseSpdx: String,
    val runtimeSourceUrl: String,
    val modelId: String,
    val modelLicenseSpdx: String,
    val modelSourceUrl: String,
    val ggufFilename: String,
    val quantization: String,
)

object SocialAiRuntimeCandidateCatalog {
    /**
     * Small on-device candidate selected for integration work. Immutable upstream
     * revisions and artifact hashes must be independently captured before release.
     */
    val smolLm2_360mInstructQ4Km = SocialAiRuntimeCandidate(
        runtimeComponentId = "ggml-org/llama.cpp",
        runtimeLicenseSpdx = "MIT",
        runtimeSourceUrl = "https://github.com/ggml-org/llama.cpp",
        modelId = "HuggingFaceTB/SmolLM2-360M-Instruct-GGUF",
        modelLicenseSpdx = "Apache-2.0",
        modelSourceUrl = "https://huggingface.co/HuggingFaceTB/SmolLM2-360M-Instruct-GGUF",
        ggufFilename = "smollm2-360m-instruct-q4_k_m.gguf",
        quantization = "Q4_K_M",
    )
}
