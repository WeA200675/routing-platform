package org.routingplatform.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SocialAiInferenceBackendTest {

    @Test
    fun deterministicBackendIsLocalAndUsesRuntimeSafetyPolicy() {
        val backend =
            DeterministicSocialAiInferenceBackend

        val result =
            backend.plan(
                SocialAiRuntimeInput(
                    settings =
                        SocialAiPersonalitySettings(
                            humorLevel = 100,
                            charmLevel = 100,
                            playfulnessLevel = 100,
                            proactivityLevel = 100,
                            flirtLevel = 100,
                            adultFlirtOptIn = true,
                        ),
                    context =
                        SocialAiResponseContext.CriticalGuidance,
                )
            )

        assertTrue(backend.localOnly)
        assertEquals(0, result.humorLevel)
        assertEquals(0, result.flirtLevel)
        assertTrue(result.suppressNonessentialSocial)
    }

    @Test
    fun modelMetadataRequiresPinnedDigestAndExplicitLicenses() {
        val runtime =
            OpenSourceComponentMetadata(
                componentId = "ggml-org/llama.cpp",
                revision = "example-revision",
                licenseSpdx = "MIT",
                sourceUrl = "https://github.com/ggml-org/llama.cpp",
            )

        val model =
            LocalModelArtifactMetadata(
                modelId = "example/model",
                revision = "example-revision",
                licenseSpdx = "Apache-2.0",
                sha256 = "a".repeat(64),
                sourceUrl = "https://example.invalid/model",
            )

        assertEquals("MIT", runtime.licenseSpdx)
        assertEquals("Apache-2.0", model.licenseSpdx)

        assertThrows(
            IllegalArgumentException::class.java
        ) {
            LocalModelArtifactMetadata(
                modelId = "example/model",
                revision = "floating-main",
                licenseSpdx = "Apache-2.0",
                sha256 = "not-a-digest",
                sourceUrl = "https://example.invalid/model",
            )
        }
    }
}
