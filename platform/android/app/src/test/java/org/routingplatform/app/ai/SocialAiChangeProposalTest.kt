package org.routingplatform.app.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class SocialAiChangeProposalTest {
    private fun proposal(risk: SocialAiProposalRisk = SocialAiProposalRisk.Medium) =
        SocialAiChangeProposal("p1", "Improve fallback", "Evidence shows a repeatable failure.", SocialAiChangeLayer.NavigationPolicy, risk, listOf("fallback"), "Use deterministic fallback.", listOf("fallback-test"), listOf("case-1"))

    @Test fun proposalNeedsReviewBeforePromotion() {
        val accepted = SocialAiChangeProposalPolicy.transition(proposal(), SocialAiProposalStatus.Accepted)
        val simulated = SocialAiChangeProposalPolicy.transition(accepted, SocialAiProposalStatus.Simulated)
        val device = SocialAiChangeProposalPolicy.transition(simulated, SocialAiProposalStatus.DeviceVerified)
        assertEquals(SocialAiProposalStatus.Promoted, SocialAiChangeProposalPolicy.transition(device, SocialAiProposalStatus.Promoted).status)
    }

    @Test(expected = IllegalArgumentException::class)
    fun directPromotionIsRejected() {
        SocialAiChangeProposalPolicy.transition(proposal(), SocialAiProposalStatus.Promoted)
    }

    @Test(expected = IllegalArgumentException::class)
    fun safetyCriticalProposalCannotBePromotedByNormalTransition() {
        val p = proposal(SocialAiProposalRisk.SafetyCritical)
        val accepted = SocialAiChangeProposalPolicy.transition(p, SocialAiProposalStatus.Accepted)
        val simulated = SocialAiChangeProposalPolicy.transition(accepted, SocialAiProposalStatus.Simulated)
        val device = SocialAiChangeProposalPolicy.transition(simulated, SocialAiProposalStatus.DeviceVerified)
        SocialAiChangeProposalPolicy.transition(device, SocialAiProposalStatus.Promoted)
    }
}
