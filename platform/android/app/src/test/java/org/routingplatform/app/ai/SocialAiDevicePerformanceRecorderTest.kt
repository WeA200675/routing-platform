package org.routingplatform.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SocialAiDevicePerformanceRecorderTest {
    @Test
    fun aggregatesTwentySamplesAndMemoryLifecycle() {
        val recorder = SocialAiDevicePerformanceRecorder()
        recorder.recordColdLoad(400, 1_000, 1_600)
        (1L..20L).forEach(recorder::recordGeneration)
        recorder.recordUnload(1_100)
        val evidence = recorder.snapshot()
        assertEquals(10, evidence.p50GenerationMillis)
        assertEquals(19, evidence.p95GenerationMillis)
        assertEquals(500, evidence.reclaimedMemoryBytes)
    }

    @Test
    fun incompleteRunCannotProduceEvidence() {
        val recorder = SocialAiDevicePerformanceRecorder()
        recorder.recordColdLoad(400, 1_000, 1_600)
        repeat(19) { recorder.recordGeneration(10) }
        recorder.recordUnload(1_100)
        assertThrows(IllegalArgumentException::class.java) { recorder.snapshot() }
    }
}
