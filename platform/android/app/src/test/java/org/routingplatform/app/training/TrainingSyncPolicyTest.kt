package org.routingplatform.app.training

import org.junit.Assert.assertEquals
import org.junit.Test

class TrainingSyncPolicyTest {
    @Test
    fun buffersOfflineAndUploadsAfterReconnect() {
        assertEquals(
            false,
            TrainingSyncPolicy.decide(false, true, 10, 80).shouldUpload,
        )
        assertEquals(
            10,
            TrainingSyncPolicy.decide(true, true, 10, 80).maxEvents,
        )
    }

    @Test
    fun protectsBatteryDuringActiveNavigation() {
        val result = TrainingSyncPolicy.decide(true, true, 10, 10)
        assertEquals(false, result.shouldUpload)
    }
}
