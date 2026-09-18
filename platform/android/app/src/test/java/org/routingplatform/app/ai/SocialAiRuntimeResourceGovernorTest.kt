package org.routingplatform.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SocialAiRuntimeResourceGovernorTest {
    @Test
    fun lowMemoryFailsClosed() {
        val result = SocialAiRuntimeResourceGovernor.admit(
            requestedOutputTokens = 192,
            resources = SocialAiRuntimeResources(availableMemoryBytes = 128L * 1024 * 1024),
        )
        assertTrue(result is SocialAiRuntimeAdmission.Rejected)
    }

    @Test
    fun thermalPressureFailsClosed() {
        val result = SocialAiRuntimeResourceGovernor.admit(
            requestedOutputTokens = 192,
            resources = SocialAiRuntimeResources(
                availableMemoryBytes = Long.MAX_VALUE,
                thermalStatus = 4,
            ),
        )
        assertTrue(result is SocialAiRuntimeAdmission.Rejected)
    }

    @Test
    fun powerSaveClampsGenerationWithoutChangingSafetyPolicy() {
        val result = SocialAiRuntimeResourceGovernor.admit(
            requestedOutputTokens = 512,
            resources = SocialAiRuntimeResources(
                availableMemoryBytes = Long.MAX_VALUE,
                powerSaveMode = true,
            ),
        ) as SocialAiRuntimeAdmission.Allowed
        assertEquals(96, result.maximumOutputTokens)
    }

    @Test
    fun circuitBreakerRequiresExplicitSuccessOrReset() {
        val breaker = SocialAiRuntimeCircuitBreaker(maximumConsecutiveFailures = 2)
        assertTrue(breaker.allowAttempt())
        breaker.recordFailure()
        assertTrue(breaker.allowAttempt())
        breaker.recordFailure()
        assertFalse(breaker.allowAttempt())
        breaker.recordSuccess()
        assertTrue(breaker.allowAttempt())
        assertEquals(0, breaker.failureCount())
    }
}
