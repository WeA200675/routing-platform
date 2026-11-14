package org.routingplatform.app.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeCapabilityMatrixTest {
    @Test
    fun navigationRequiresRoutingAndNonBlockedGps() {
        var now = 100L
        val matrix = RuntimeCapabilityMatrix { now }
        matrix.record(RuntimeCapability.ROUTING_NATIVE, RuntimeCapabilityState.READY, "native-ok")
        matrix.record(RuntimeCapability.GPS, RuntimeCapabilityState.READY, "gps-ok")
        assertTrue(matrix.snapshot().canNavigate())

        now = 200L
        matrix.record(RuntimeCapability.GPS, RuntimeCapabilityState.BLOCKED, "permission-denied")
        assertTrue(!matrix.snapshot().canNavigate())
    }

    @Test
    fun failedProbeIsRecordedWithMeasuredFailure() {
        val matrix = RuntimeCapabilityMatrix { 100L }
        val snapshot = matrix.recordProbe(RuntimeCapability.LOCAL_AI) {
            error("runtime-missing")
        }
        val status = snapshot.status(RuntimeCapability.LOCAL_AI)
        assertEquals(RuntimeCapabilityState.UNAVAILABLE, status.state)
        assertEquals("runtime-missing", status.detail)
    }

    @Test
    fun localAiIsOnlyReadyWhenExplicitlyRecordedReady() {
        val matrix = RuntimeCapabilityMatrix { 100L }
        assertTrue(!matrix.snapshot().canUseLocalAi())
        matrix.record(RuntimeCapability.LOCAL_AI, RuntimeCapabilityState.READY, "verified")
        assertTrue(matrix.snapshot().canUseLocalAi())
    }
}
