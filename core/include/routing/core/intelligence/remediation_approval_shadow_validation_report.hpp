#pragma once

#include <sstream>
#include <string>

#include "routing/core/intelligence/remediation_approval_shadow_validation.hpp"

namespace routing::core::intelligence {

[[nodiscard]]
inline std::string
format_remediation_approval_report(
    const RemediationApprovalRecord& record) {
  std::ostringstream output;

  output
      << "REMEDIATION APPROVAL\n"
      << "schema: "
      << record.schema_version
      << "\n"
      << "approval id: "
      << record.approval_id
      << "\n"
      << "decision: "
      << remediation_approval_decision_key(
             record.decision)
      << "\n"
      << "source proposal: "
      << record.source_proposal_id
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
      << "scope: "
      << record.data_scope_key
      << "\n"
      << "context: "
      << record.context_key
      << "\n"
      << "remediation key: "
      << record.remediation_key
      << "\n"
      << "proposer: "
      << record.proposer_ref
      << "\n"
      << "approver: "
      << record.approver_ref
      << "\n"
      << "rationale: "
      << record.rationale
      << "\n\n";


  if (record.shadow_validation_candidate.has_value()) {
    const auto& candidate =
        *record.shadow_validation_candidate;

    output
        << "REMEDIATION SHADOW VALIDATION CANDIDATE\n"
        << "  id: "
        << candidate.id
        << "\n"
        << "  isolated validation only: "
        << (candidate.isolated_validation_only
                ? "yes"
                : "no")
        << "\n"
        << "  production traffic allowed: "
        << (candidate.production_traffic_allowed
                ? "yes"
                : "no")
        << "\n"
        << "  real user impact allowed: "
        << (candidate.real_user_impact_allowed
                ? "yes"
                : "no")
        << "\n"
        << "  implementation task created: "
        << (candidate.implementation_task_created
                ? "yes"
                : "no")
        << "\n"
        << "  automatic apply allowed: "
        << (candidate.automatic_apply_allowed
                ? "yes"
                : "no")
        << "\n"
        << "  routing change allowed: "
        << (candidate.routing_change_allowed
                ? "yes"
                : "no")
        << "\n"
        << "  CostEngine change allowed: "
        << (candidate.cost_engine_change_allowed
                ? "yes"
                : "no")
        << "\n"
        << "  production application allowed: "
        << (candidate.production_application_allowed
                ? "yes"
                : "no")
        << "\n";
  } else {
    output
        << "REMEDIATION SHADOW VALIDATION CANDIDATE\n"
        << "  created: no\n";
  }


  return output.str();
}


[[nodiscard]]
inline std::string
format_remediation_shadow_validation_report(
    const RemediationShadowValidationRecord& record) {
  std::ostringstream output;

  output
      << "REMEDIATION SHADOW VALIDATION\n"
      << "schema: "
      << record.schema_version
      << "\n"
      << "validation id: "
      << record.validation_id
      << "\n"
      << "validation revision: "
      << record.validation_revision
      << "\n"
      << "environment: "
      << remediation_validation_environment_key(
             record.environment)
      << "\n"
      << "result: "
      << remediation_shadow_validation_result_key(
             record.result)
      << "\n"
      << "source approval: "
      << record.source_approval_id
      << "\n"
      << "source proposal: "
      << record.source_proposal_id
      << "\n"
      << "system hypothesis: "
      << record.system_hypothesis_id
      << "\n"
      << "remediation key: "
      << record.remediation_key
      << "\n"
      << "scope: "
      << record.data_scope_key
      << "\n"
      << "context: "
      << record.context_key
      << "\n"
      << "validator: "
      << record.validator_ref
      << "\n"
      << "supporting evidence: "
      << record.supporting_evidence_count
      << "\n"
      << "regression evidence: "
      << record.regression_evidence_count
      << "\n"
      << "context evidence: "
      << record.context_evidence_count
      << "\n"
      << "rationale: "
      << record.rationale
      << "\n\n"
      << "BOUNDARY\n"
      << "  implementation candidate created: "
      << (record.implementation_candidate_created
              ? "yes"
              : "no")
      << "\n"
      << "  implementation task created: "
      << (record.implementation_task_created
              ? "yes"
              : "no")
      << "\n"
      << "  deployment candidate created: "
      << (record.deployment_candidate_created
              ? "yes"
              : "no")
      << "\n"
      << "  automatic apply allowed: "
      << (record.automatic_apply_allowed
              ? "yes"
              : "no")
      << "\n"
      << "  data write allowed: "
      << (record.data_write_allowed
              ? "yes"
              : "no")
      << "\n"
      << "  backend change allowed: "
      << (record.backend_change_allowed
              ? "yes"
              : "no")
      << "\n"
      << "  candidate pipeline change allowed: "
      << (record.candidate_pipeline_change_allowed
              ? "yes"
              : "no")
      << "\n"
      << "  map change allowed: "
      << (record.map_change_allowed
              ? "yes"
              : "no")
      << "\n"
      << "  routing change allowed: "
      << (record.routing_change_allowed
              ? "yes"
              : "no")
      << "\n"
      << "  CostEngine change allowed: "
      << (record.cost_engine_change_allowed
              ? "yes"
              : "no")
      << "\n"
      << "  PreferenceHypothesis created: "
      << (record.preference_hypothesis_created
              ? "yes"
              : "no")
      << "\n"
      << "  production application allowed: "
      << (record.production_application_allowed
              ? "yes"
              : "no")
      << "\n"
      << "  evidence scope promotion allowed: "
      << (record.evidence_scope_promotion_allowed
              ? "yes"
              : "no")
      << "\n";

  return output.str();
}

}  // namespace routing::core::intelligence
