package org.routingplatform.app.ai

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SocialAiModelLifecycleTest {
    @Test
    fun rejectsDigestMismatchBeforeBackendLoad() {
        val file = File.createTempFile("g620-model", ".gguf")
        file.writeText("not the pinned model")
        var loadCalls = 0
        val metadata = LocalModelArtifactMetadata(
            modelId = "test/model",
            revision = "immutable",
            licenseSpdx = "Apache-2.0",
            sha256 = "a".repeat(64),
            sourceUrl = "https://example.invalid/model",
        )
        val backend = object : ManagedLocalSocialAiBackend {
            override val backendId = "test"
            override val runtimeMetadata = OpenSourceComponentMetadata(
                "runtime", "immutable", "MIT", "https://example.invalid/runtime"
            )
            override val modelMetadata = metadata
            override var isLoaded = false
            override fun load(request: SocialAiModelLoadRequest): SocialAiModelLoadResult {
                loadCalls++
                isLoaded = true
                return SocialAiModelLoadResult.Loaded(metadata.modelId)
            }
            override fun unload() { isLoaded = false }
            override fun generate(request: SocialAiTextGenerationRequest) =
                SocialAiTextGenerationResult("local", backendId, true)
        }

        val result = SocialAiModelLifecycle(backend).load(
            SocialAiModelLoadRequest(file, metadata, Long.MAX_VALUE)
        )

        assertTrue(result is SocialAiModelLoadResult.Rejected)
        assertEquals(0, loadCalls)
        file.delete()
    }

    @Test
    fun clampsOutputTokensToRuntimeEnvelope() {
        val metadata = LocalModelArtifactMetadata(
            "test/model", "immutable", "MIT", "b".repeat(64), "https://example.invalid/model"
        )
        val backend = object : ManagedLocalSocialAiBackend {
            override val backendId = "test"
            override val runtimeMetadata = OpenSourceComponentMetadata(
                "runtime", "immutable", "MIT", "https://example.invalid/runtime"
            )
            override val modelMetadata = metadata
            override val isLoaded = false
            override fun load(request: SocialAiModelLoadRequest) =
                SocialAiModelLoadResult.Loaded(metadata.modelId)
            override fun unload() = Unit
            override fun generate(request: SocialAiTextGenerationRequest) =
                SocialAiTextGenerationResult("local", backendId, true)
        }

        val lifecycle = SocialAiModelLifecycle(
            backend,
            SocialAiRuntimeLimits(maximumOutputTokens = 256)
        )
        assertEquals(256, lifecycle.admittedOutputTokens(2_048))
        assertEquals(1, lifecycle.admittedOutputTokens(0))
    }
}
