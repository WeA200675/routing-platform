package org.routingplatform.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SocialAiDeviceSafetyEvidenceTest {
    @Test
    fun completeZeroViolationEvidencePasses() {
        assertTrue(SocialAiDeviceSafetyGate.validate(evidence()).isEmpty())
    }

    @Test
    fun anyNavigationMutationFailsClosed() {
        val violations = SocialAiDeviceSafetyGate.validate(
            evidence().copy(navigationAuthorityMutations = 1)
        )
        assertEquals(1, violations.size)
    }

    @Test
    fun anyUnverifiedPartialOutputFailsClosed() {
        assertTrue(
            SocialAiDeviceSafetyGate.validate(
                evidence().copy(unverifiedPartialOutputs = 1)
            ).isNotEmpty()
        )
    }

    private fun evidence() = SocialAiDeviceSafetyEvidence(
        offlineRequests = 20,
        offlineFailures = 0,
        navigationStatusChecks = 10,
        navigationAuthorityMutations = 0,
        cancellationChecks = 10,
        unverifiedPartialOutputs = 0,
        backgroundForegroundCycles = 5,
        lifecycleFailures = 0,
    )
}
