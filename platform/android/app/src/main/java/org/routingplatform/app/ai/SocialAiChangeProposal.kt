package org.routingplatform.app.ai

enum class SocialAiChangeLayer {
    Prompt,
    TrainingCase,
    EvaluationPolicy,
    NavigationPolicy,
    RoutingAdapter,
    UI,
    Test,
    Documentation,
}

enum class SocialAiProposalRisk {
    Low,
    Medium,
    High,
    SafetyCritical,
}

enum class SocialAiProposalStatus {
    Proposed,
    NeedsEvidence,
    Accepted,
    Rejected,
    Simulated,
    DeviceVerified,
    Promoted,
    RolledBack,
}

/** A broad but non-authoritative change request produced by the local AI. */
data class SocialAiChangeProposal(
    val id: String,
    val title: String,
    val rationale: String,
    val layer: SocialAiChangeLayer,
    val risk: SocialAiProposalRisk,
    val affectedCapabilities: List<String>,
    val expectedBehavior: String,
    val regressionTests: List<String>,
    val evidenceCaseIds: List<String>,
    val status: SocialAiProposalStatus = SocialAiProposalStatus.Proposed,
) {
    init {
        require(ID.matches(id))
        require(title.isNotBlank() && title.length <= 160)
        require(rationale.isNotBlank() && rationale.length <= 2_000)
        require(affectedCapabilities.isNotEmpty())
        require(expectedBehavior.isNotBlank())
        require(regressionTests.isNotEmpty())
        require(evidenceCaseIds.isNotEmpty())
        if (risk >= SocialAiProposalRisk.High) {
            require(layer != SocialAiChangeLayer.Prompt || regressionTests.size >= 2)
        }
    }

    val requiresUserApproval: Boolean
        get() = status != SocialAiProposalStatus.Promoted

    val canEnterSimulation: Boolean
        get() =
            status == SocialAiProposalStatus.Accepted &&
                regressionTests.isNotEmpty() &&
                evidenceCaseIds.isNotEmpty()

    companion object {
        private val ID = Regex("[A-Za-z0-9._:-]{1,96}")
    }
}

object SocialAiChangeProposalPolicy {
    fun transition(
        proposal: SocialAiChangeProposal,
        target: SocialAiProposalStatus,
    ): SocialAiChangeProposal {
        require(isAllowed(proposal.status, target)) {
            "Invalid AI proposal transition: ${proposal.status} -> $target"
        }
        if (target == SocialAiProposalStatus.Promoted) {
            require(proposal.status == SocialAiProposalStatus.DeviceVerified) {
                "Only device-verified proposals may be promoted."
            }
            require(proposal.risk != SocialAiProposalRisk.SafetyCritical) {
                "Safety-critical proposals require an explicit release process."
            }
        }
        return proposal.copy(status = target)
    }

    private fun isAllowed(from: SocialAiProposalStatus, to: SocialAiProposalStatus): Boolean =
        when (from) {
            SocialAiProposalStatus.Proposed -> to == SocialAiProposalStatus.Accepted || to == SocialAiProposalStatus.Rejected || to == SocialAiProposalStatus.NeedsEvidence
            SocialAiProposalStatus.NeedsEvidence -> to == SocialAiProposalStatus.Accepted || to == SocialAiProposalStatus.Rejected
            SocialAiProposalStatus.Accepted -> to == SocialAiProposalStatus.Simulated || to == SocialAiProposalStatus.Rejected
            SocialAiProposalStatus.Simulated -> to == SocialAiProposalStatus.DeviceVerified || to == SocialAiProposalStatus.RolledBack
            SocialAiProposalStatus.DeviceVerified -> to == SocialAiProposalStatus.Promoted || to == SocialAiProposalStatus.RolledBack
            SocialAiProposalStatus.Promoted -> to == SocialAiProposalStatus.RolledBack
            SocialAiProposalStatus.Rejected, SocialAiProposalStatus.RolledBack -> false
        }
}
