#pragma once

#include <sstream>
#include <string>

#include "routing/core/intelligence/remediation_proposal.hpp"

namespace routing::core::intelligence {

inline void
append_remediation_proposal_boundary(
    std::ostringstream& output,
    const RemediationProposalBase& base) {
  output
      << "  explicit approval required: "
      << (base.explicit_approval_required
              ? "yes"
              : "no")
      << "\n"
      << "  shadow validation required: "
      << (base.shadow_validation_required
              ? "yes"
              : "no")
      << "\n"
      << "  approval record created: "
      << (base.approval_record_created
              ? "yes"
              : "no")
      << "\n"
      << "  shadow validation created: "
      << (base.shadow_validation_created
              ? "yes"
              : "no")
      << "\n"
      << "  implementation task created: "
      << (base.implementation_task_created
              ? "yes"
              : "no")
      << "\n"
      << "  preference interpretation allowed: "
      << (base.preference_interpretation_allowed
              ? "yes"
              : "no")
      << "\n"
      << "  PreferenceTarget created: "
      << (base.preference_target_created
              ? "yes"
              : "no")
      << "\n"
      << "  PreferenceHypothesis created: "
      << (base.preference_hypothesis_created
              ? "yes"
              : "no")
      << "\n"
      << "  LearningGate invoked: "
      << (base.learning_gate_invoked
              ? "yes"
              : "no")
      << "\n"
      << "  automatic apply allowed: "
      << (base.automatic_apply_allowed
              ? "yes"
              : "no")
      << "\n"
      << "  data write allowed: "
      << (base.data_write_allowed
              ? "yes"
              : "no")
      << "\n"
      << "  backend change allowed: "
      << (base.backend_change_allowed
              ? "yes"
              : "no")
      << "\n"
      << "  candidate pipeline change allowed: "
      << (base.candidate_pipeline_change_allowed
              ? "yes"
              : "no")
      << "\n"
      << "  map change allowed: "
      << (base.map_change_allowed
              ? "yes"
              : "no")
      << "\n"
      << "  routing change allowed: "
      << (base.routing_change_allowed
              ? "yes"
              : "no")
      << "\n"
      << "  CostEngine change allowed: "
      << (base.cost_engine_change_allowed
              ? "yes"
              : "no")
      << "\n"
      << "  production application allowed: "
      << (base.production_application_allowed
              ? "yes"
              : "no")
      << "\n"
      << "  evidence scope promotion allowed: "
      << (base.evidence_scope_promotion_allowed
              ? "yes"
              : "no")
      << "\n";
}


[[nodiscard]]
inline std::string
format_remediation_proposal_report(
    const RemediationProposalRecord& record) {
  std::ostringstream output;

  output
      << "REMEDIATION PROPOSAL\n"
      << "schema: "
      << record.schema_version
      << "\n"
      << "proposal id: "
      << record.proposal_id
      << "\n"
      << "kind: "
      << remediation_proposal_kind_key(
             record.kind)
      << "\n"
      << "source evaluation: "
      << record.source_evaluation_id
      << "\n"
      << "source evaluation revision: "
      << record.source_evaluation_revision
      << "\n"
      << "system hypothesis: "
      << record.system_hypothesis_id
      << "\n"
      << "target: "
      << record.hypothesis_target_key
      << "\n"
      << "context: "
      << record.context_key
      << "\n"
      << "data scope: "
      << record.data_scope_key
      << "\n"
      << "diagnostic: "
      << record.diagnostic_code
      << "\n"
      << "hypothesis evidence revision: "
      << record.hypothesis_evidence_revision
      << "\n"
      << "proposer: "
      << record.proposer_ref
      << "\n"
      << "remediation key: "
      << record.remediation_key
      << "\n"
      << "rationale: "
      << record.rationale
      << "\n\n";


  if (record.data_remediation_proposal.has_value()) {
    const auto& proposal =
        *record.data_remediation_proposal;

    output
        << "DATA REMEDIATION PROPOSAL\n"
        << "  id: "
        << proposal.base.id
        << "\n"
        << "  data source key: "
        << proposal.data_source_key
        << "\n"
        << "  supporting evidence: "
        << proposal.base.supporting_evidence_count
        << "\n";

    append_remediation_proposal_boundary(
        output,
        proposal.base);
  }


  if (record.backend_remediation_proposal.has_value()) {
    const auto& proposal =
        *record.backend_remediation_proposal;

    output
        << "BACKEND REMEDIATION PROPOSAL\n"
        << "  id: "
        << proposal.base.id
        << "\n"
        << "  backend component key: "
        << proposal.backend_component_key
        << "\n"
        << "  supporting evidence: "
        << proposal.base.supporting_evidence_count
        << "\n";

    append_remediation_proposal_boundary(
        output,
        proposal.base);
  }


  if (record.candidate_pipeline_remediation_proposal.has_value()) {
    const auto& proposal =
        *record.candidate_pipeline_remediation_proposal;

    output
        << "CANDIDATE PIPELINE REMEDIATION PROPOSAL\n"
        << "  id: "
        << proposal.base.id
        << "\n"
        << "  pipeline stage key: "
        << proposal.pipeline_stage_key
        << "\n"
        << "  supporting evidence: "
        << proposal.base.supporting_evidence_count
        << "\n";

    append_remediation_proposal_boundary(
        output,
        proposal.base);
  }

  return output.str();
}

}  // namespace routing::core::intelligence
