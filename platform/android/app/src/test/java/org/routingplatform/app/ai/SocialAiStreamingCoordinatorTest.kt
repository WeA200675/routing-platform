package org.routingplatform.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class SocialAiStreamingCoordinatorTest {
    private val input = SocialAiRuntimeInput(SocialAiPersonalitySettings())

    @Test
    fun partialTokensAreNotPresentedBeforeEnforcement() {
        val emitted = mutableListOf<SocialAiGenerationEvent>()
        val result = SocialAiStreamingCoordinator(backend { _, emit ->
            emit(SocialAiGenerationEvent.Token("unsafe partial"))
            emit(SocialAiGenerationEvent.Completed(
                SocialAiTextGenerationResult(" final answer ", "stream-local", true)
            ))
        }).generateOrNull(input, "Hallo", emit = emitted::add)

        assertEquals("final answer", result!!.text)
        assertEquals(1, emitted.size)
        assertEquals("final answer", (emitted.single() as SocialAiGenerationEvent.Completed).result.text)
    }

    @Test
    fun cancellationProducesNoGeneratedText() {
        val emitted = mutableListOf<SocialAiGenerationEvent>()
        val result = SocialAiStreamingCoordinator(backend { _, emit ->
            emit(SocialAiGenerationEvent.Cancelled)
        }).generateOrNull(input, "Hallo", emit = emitted::add)

        assertNull(result)
        assertEquals(listOf(SocialAiGenerationEvent.Cancelled), emitted)
    }

    @Test
    fun missingTerminalEventFailsClosed() {
        assertThrows(IllegalStateException::class.java) {
            SocialAiStreamingCoordinator(backend { _, emit ->
                emit(SocialAiGenerationEvent.Token("partial"))
            }).generateOrNull(input, "Hallo", emit = {})
        }
    }

    private fun backend(
        body: (SocialAiTextGenerationRequest, (SocialAiGenerationEvent) -> Unit) -> Unit,
    ) = object : StreamingLocalSocialAiTextGenerationBackend {
        override val backendId = "stream-local"
        override val runtimeMetadata = OpenSourceComponentMetadata(
            "runtime", "abc123", "MIT", "https://example.invalid/runtime"
        )
        override val modelMetadata = LocalModelArtifactMetadata(
            "model", "def456", "MIT", "a".repeat(64), "https://example.invalid/model"
        )
        override fun generate(request: SocialAiTextGenerationRequest) =
            SocialAiTextGenerationResult("sync", backendId, true)
        override fun generateStreaming(
            request: SocialAiTextGenerationRequest,
            cancellation: SocialAiGenerationCancellation,
            emit: (SocialAiGenerationEvent) -> Unit,
        ) = body(request, emit)
    }
}
