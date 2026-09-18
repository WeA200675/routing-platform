package org.routingplatform.app.ai

private val SPDX_ID_PATTERN =
    Regex("[A-Za-z0-9.+-]{1,64}")

private val SHA256_PATTERN =
    Regex("[A-Fa-f0-9]{64}")

data class OpenSourceComponentMetadata(
    val componentId: String,
    val revision: String,
    val licenseSpdx: String,
    val sourceUrl: String,
) {
    init {
        require(componentId.isNotBlank() && componentId.length <= 128) {
            "componentId must be non-blank and at most 128 characters."
        }
        require(revision.isNotBlank() && revision.length <= 128) {
            "revision must be non-blank and at most 128 characters."
        }
        require(SPDX_ID_PATTERN.matches(licenseSpdx)) {
            "licenseSpdx must be a simple SPDX identifier."
        }
        require(sourceUrl.startsWith("https://")) {
            "sourceUrl must use HTTPS."
        }
    }
}

data class LocalModelArtifactMetadata(
    val modelId: String,
    val revision: String,
    val licenseSpdx: String,
    val sha256: String,
    val sourceUrl: String,
) {
    init {
        require(modelId.isNotBlank() && modelId.length <= 160) {
            "modelId must be non-blank and at most 160 characters."
        }
        require(revision.isNotBlank() && revision.length <= 160) {
            "revision must be non-blank and at most 160 characters."
        }
        require(SPDX_ID_PATTERN.matches(licenseSpdx)) {
            "licenseSpdx must be a simple SPDX identifier."
        }
        require(SHA256_PATTERN.matches(sha256)) {
            "sha256 must contain exactly 64 hexadecimal characters."
        }
        require(sourceUrl.startsWith("https://")) {
            "sourceUrl must use HTTPS."
        }
    }
}

interface SocialAiInferenceBackend {
    val backendId: String
    val localOnly: Boolean

    fun plan(
        input: SocialAiRuntimeInput,
    ): SocialAiResponsePlan
}

object DeterministicSocialAiInferenceBackend :
    SocialAiInferenceBackend {

    override val backendId: String =
        "deterministic-local-v1"

    override val localOnly: Boolean =
        true

    override fun plan(
        input: SocialAiRuntimeInput,
    ): SocialAiResponsePlan =
        SocialAiRuntime.plan(input)
}

data class SocialAiTextGenerationRequest(
    val systemInstruction: String,
    val userText: String,
    val responsePlan: SocialAiResponsePlan,
    val rememberedContext: List<SocialAiKnowledge> = emptyList(),
    val maximumOutputTokens: Int = 192,
) {
    init {
        require(systemInstruction.isNotBlank()) {
            "systemInstruction must not be blank."
        }
        require(systemInstruction.length <= 8_192) {
            "systemInstruction is too long."
        }
        require(userText.isNotBlank()) {
            "userText must not be blank."
        }
        require(userText.length <= 16_384) {
            "userText is too long."
        }
        require(rememberedContext.size <= 64) {
            "rememberedContext must contain at most 64 entries."
        }
        require(maximumOutputTokens in 1..2_048) {
            "maximumOutputTokens must be in [1, 2048]."
        }
    }
}

data class SocialAiTextGenerationResult(
    val text: String,
    val backendId: String,
    val localInference: Boolean,
) {
    init {
        require(text.isNotBlank()) {
            "Generated text must not be blank."
        }
        require(text.length <= 32_768) {
            "Generated text is too long."
        }
        require(backendId.isNotBlank()) {
            "backendId must not be blank."
        }
    }
}

interface LocalSocialAiTextGenerationBackend {
    val backendId: String
    val runtimeMetadata: OpenSourceComponentMetadata
    val modelMetadata: LocalModelArtifactMetadata

    fun generate(
        request: SocialAiTextGenerationRequest,
    ): SocialAiTextGenerationResult
}
