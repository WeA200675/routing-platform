package org.routingplatform.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SocialAiDeviceSafetyRecorderTest {
    @Test
    fun recordsOnlyAcceptanceCounters() {
        val recorder = SocialAiDeviceSafetyRecorder()
        repeat(20) { recorder.recordOfflineRequest(succeeded = true) }
        repeat(10) { recorder.recordNavigationStatusCheck(authorityMutated = false) }
        repeat(10) { recorder.recordCancellation(unverifiedPartialOutputPresented = false) }
        repeat(5) { recorder.recordBackgroundForegroundCycle(succeeded = true) }

        val evidence = recorder.snapshot()
        assertEquals(20, evidence.offlineRequests)
        assertTrue(SocialAiDeviceSafetyGate.validate(evidence).isEmpty())
    }

    @Test
    fun violationsArePreservedForFailClosedEvaluation() {
        val recorder = SocialAiDeviceSafetyRecorder()
        recorder.recordOfflineRequest(false)
        repeat(10) { recorder.recordNavigationStatusCheck(it == 0) }
        repeat(10) { recorder.recordCancellation(it == 0) }
        repeat(5) { recorder.recordBackgroundForegroundCycle(it != 0) }

        assertEquals(4, SocialAiDeviceSafetyGate.validate(recorder.snapshot()).size)
    }
}
