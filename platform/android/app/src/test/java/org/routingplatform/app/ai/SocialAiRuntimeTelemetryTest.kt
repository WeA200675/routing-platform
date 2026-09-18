package org.routingplatform.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SocialAiRuntimeTelemetryTest {
    @Test
    fun metricsContainOnlySizesIdentityAndTiming() {
        val captured = mutableListOf<SocialAiInferenceMetrics>()
        val delegate = object : LocalSocialAiTextGenerationBackend {
            override val backendId = "local"
            override val runtimeMetadata = OpenSourceComponentMetadata(
                "runtime", "abc", "MIT", "https://example.invalid/runtime"
            )
            override val modelMetadata = LocalModelArtifactMetadata(
                "model", "def", "MIT", "a".repeat(64), "https://example.invalid/model"
            )
            override fun generate(request: SocialAiTextGenerationRequest) =
                SocialAiTextGenerationResult("secret answer", backendId, true)
        }
        val times = ArrayDeque(listOf(100L, 125L))
        val measured = SocialAiMeasuredBackend(delegate, { times.removeFirst() }, captured::add)
        val request = SocialAiTextGenerationRequest(
            systemInstruction = "secret system",
            userText = "secret user",
            responsePlan = SocialAiRuntime.plan(SocialAiRuntimeInput(SocialAiPersonalitySettings())),
        )

        measured.generate(request)

        assertEquals(25L, captured.single().elapsedMillis)
        assertEquals("model", captured.single().modelId)
        assertFalse(captured.single().toString().contains("secret answer"))
        assertFalse(captured.single().toString().contains("secret user"))
    }
}
