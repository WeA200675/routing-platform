package org.routingplatform.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SocialAiRuntimeCandidateCatalogTest {
    @Test
    fun candidateUsesReviewedPermissiveLicensesAndExplicitArtifactName() {
        val candidate = SocialAiRuntimeCandidateCatalog.smolLm2_360mInstructQ4Km
        assertEquals("MIT", candidate.runtimeLicenseSpdx)
        assertEquals("Apache-2.0", candidate.modelLicenseSpdx)
        assertEquals("Q4_K_M", candidate.quantization)
        assertFalse(candidate.runtimeSourceUrl.contains("/tree/main"))
        assertFalse(candidate.modelSourceUrl.endsWith("/main"))
    }
}
