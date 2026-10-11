package org.routingplatform.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SocialAiDevicePerformanceEvidenceTest {
    @Test
    fun computesDeterministicP50AndP95FromTwentySamples() {
        val evidence = evidence((1L..20L).toList())
        assertEquals(10L, evidence.p50GenerationMillis)
        assertEquals(19L, evidence.p95GenerationMillis)
        assertTrue(SocialAiDevicePerformanceGate.validate(evidence).isEmpty())
    }

    @Test
    fun missingMemoryReclamationFailsEvidenceGate() {
        val evidence = evidence((1L..20L).toList(), afterUnload = 1_500L)
        assertTrue(SocialAiDevicePerformanceGate.validate(evidence).isNotEmpty())
    }

    private fun evidence(latencies: List<Long>, afterUnload: Long = 1_100L) =
        SocialAiDevicePerformanceEvidence(
            sampleCount = 20,
            coldLoadMillis = 500,
            memoryBeforeLoadBytes = 1_000,
            memoryAfterLoadBytes = 1_500,
            memoryAfterUnloadBytes = afterUnload,
            generationLatencyMillis = latencies,
        )
}
