package org.routingplatform.app.training

import org.junit.Assert.assertEquals
import org.junit.Test

class TrainingServiceHealthMonitorTest {
    @Test
    fun reportsHealthyWhenAllServicesAreAvailable() {
        val health = TrainingServiceHealthMonitor().evaluate(true, true, true, true, true)
        assertEquals(TrainingServiceStatus.HEALTHY, health.overall)
    }

    @Test
    fun reportsOfflineWhenCoreServiceIsUnavailable() {
        val health = TrainingServiceHealthMonitor().evaluate(false, true, true, true, true)
        assertEquals(TrainingServiceStatus.OFFLINE, health.overall)
    }

    @Test
    fun reportsDegradedForOptionalServiceFailure() {
        val health = TrainingServiceHealthMonitor().evaluate(true, true, false, true, true)
        assertEquals(TrainingServiceStatus.DEGRADED, health.overall)
    }
}
