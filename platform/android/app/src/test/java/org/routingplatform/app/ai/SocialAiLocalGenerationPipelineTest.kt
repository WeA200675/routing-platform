package org.routingplatform.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class SocialAiLocalGenerationPipelineTest {
    @Test
    fun noBackendMeansNoGeneration() {
        val result = SocialAiLocalGenerationPipeline(null).generateOrNull(
            SocialAiGenerationEnvelope(
                input = SocialAiRuntimeInput(SocialAiPersonalitySettings()),
                userText = "Hallo",
                recalledKnowledge = emptyList(),
            )
        )
        assertNull(result)
    }

    @Test
    fun recalledMemoryIsSanitizedBeforeBackend() {
        var captured: SocialAiTextGenerationRequest? = null
        val backend = object : LocalSocialAiTextGenerationBackend {
            override val backendId = "local"
            override val runtimeMetadata = OpenSourceComponentMetadata(
                "runtime", "abc", "MIT", "https://example.invalid/runtime"
            )
            override val modelMetadata = LocalModelArtifactMetadata(
                "model", "def", "MIT", "a".repeat(64), "https://example.invalid/model"
            )
            override fun generate(request: SocialAiTextGenerationRequest): SocialAiTextGenerationResult {
                captured = request
                return SocialAiTextGenerationResult(" Antwort ", backendId, true)
            }
        }
        val knowledge = SocialAiKnowledge(
            id = "k1",
            kind = SocialAiKnowledgeKind.Preference,
            key = "name",
            value = "A\u0000B",
            confidence = 1.0,
            scope = SocialAiMemoryScope.LongTerm,
            source = "test",
            observedAtEpochMillis = 1,
        )
        val result = SocialAiLocalGenerationPipeline(backend).generateOrNull(
            SocialAiGenerationEnvelope(
                input = SocialAiRuntimeInput(SocialAiPersonalitySettings()),
                userText = "Hallo",
                recalledKnowledge = listOf(knowledge),
            )
        )
        assertEquals("AB", captured!!.rememberedContext.single().value)
        assertEquals("Antwort", result!!.text)
    }

    @Test
    fun criticalOutputOverEnvelopeIsRejected() {
        val backend = object : LocalSocialAiTextGenerationBackend {
            override val backendId = "local"
            override val runtimeMetadata = OpenSourceComponentMetadata(
                "runtime", "abc", "MIT", "https://example.invalid/runtime"
            )
            override val modelMetadata = LocalModelArtifactMetadata(
                "model", "def", "MIT", "a".repeat(64), "https://example.invalid/model"
            )
            override fun generate(request: SocialAiTextGenerationRequest) =
                SocialAiTextGenerationResult("x".repeat(1_025), backendId, true)
        }
        assertThrows(IllegalArgumentException::class.java) {
            SocialAiLocalGenerationPipeline(backend).generateOrNull(
                SocialAiGenerationEnvelope(
                    input = SocialAiRuntimeInput(
                        settings = SocialAiPersonalitySettings(),
                        context = SocialAiResponseContext.CriticalGuidance,
                    ),
                    userText = "Hinweis",
                    recalledKnowledge = emptyList(),
                )
            )
        }
    }
}
