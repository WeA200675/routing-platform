package org.routingplatform.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SocialAiTextGenerationCoordinatorTest {
    @Test
    fun absentBackendHasNoImplicitFallback() {
        val coordinator = SocialAiTextGenerationCoordinator(null)

        assertNull(
            coordinator.generateOrNull(
                input = SocialAiRuntimeInput(settings = SocialAiPersonalitySettings()),
                userText = "Hallo",
            )
        )
    }

    @Test
    fun criticalGuidanceReachesBackendAlreadySuppressed() {
        var captured: SocialAiTextGenerationRequest? = null
        val backend =
            object : LocalSocialAiTextGenerationBackend {
                override val backendId = "test-local"
                override val runtimeMetadata =
                    OpenSourceComponentMetadata(
                        componentId = "test/runtime",
                        revision = "1",
                        licenseSpdx = "MIT",
                        sourceUrl = "https://example.invalid/runtime",
                    )
                override val modelMetadata =
                    LocalModelArtifactMetadata(
                        modelId = "test/model",
                        revision = "1",
                        licenseSpdx = "Apache-2.0",
                        sha256 = "a".repeat(64),
                        sourceUrl = "https://example.invalid/model",
                    )

                override fun generate(
                    request: SocialAiTextGenerationRequest,
                ): SocialAiTextGenerationResult {
                    captured = request
                    return SocialAiTextGenerationResult(
                        text = "Bitte jetzt rechts abbiegen.",
                        backendId = backendId,
                        localInference = true,
                    )
                }
            }

        val result =
            SocialAiTextGenerationCoordinator(backend)
                .generateOrNull(
                    input =
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
                            context = SocialAiResponseContext.CriticalGuidance,
                        ),
                    userText = "Nächster Hinweis",
                )

        assertEquals("test-local", result?.backendId)
        assertEquals(0, captured?.responsePlan?.humorLevel)
        assertEquals(0, captured?.responsePlan?.flirtLevel)
        assertTrue(captured?.responsePlan?.suppressNonessentialSocial == true)
    }
}
