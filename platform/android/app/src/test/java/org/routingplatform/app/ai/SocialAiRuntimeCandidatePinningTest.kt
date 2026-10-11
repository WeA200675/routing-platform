package org.routingplatform.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SocialAiRuntimeCandidatePinningTest {
    private val candidate = SocialAiRuntimeCandidateCatalog.smolLm2_360mInstructQ4Km

    @Test
    fun immutableInputsProduceAdmittedProvenance() {
        val result = SocialAiRuntimeCandidatePinning.pin(
            candidate, "1".repeat(40), "model-revision-123",
            "0123456789abcdef".repeat(4), 271_000_000, 768L * 1024 * 1024,
        )
        assertEquals("GGUF", result.modelFormat)
        assertEquals("Q4_K_M", result.quantization)
    }

    @Test
    fun floatingRuntimeRevisionCannotBePinned() {
        assertThrows(IllegalArgumentException::class.java) {
            SocialAiRuntimeCandidatePinning.pin(
                candidate, "main", "model-revision-123",
                "0123456789abcdef".repeat(4), 271_000_000, 768L * 1024 * 1024,
            )
        }
    }
}
