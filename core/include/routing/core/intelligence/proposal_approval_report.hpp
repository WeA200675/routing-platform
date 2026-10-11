#pragma once

#include <sstream>
#include <string>

#include "routing/core/intelligence/proposal_approval.hpp"

namespace routing::core::intelligence {

[[nodiscard]]
inline std::string
format_proposal_approval_report(
    const ProposalApprovalRecord& record) {
  std::ostringstream output;

  output
      << "PROPOSAL APPROVAL\n"
      << "schema: "
      << record.schema_version
      << "\n"
      << "approval id: "
      << record.approval_id
      << "\n"
      << "decision: "
      << proposal_approval_decision_key(
             record.decision)
      << "\n"
      << "source outcome: "
      << record.source_outcome_id
      << "\n"
      << "outcome kind: "
      << reviewed_analysis_outcome_kind_key(
             record.outcome_kind)
      << "\n"
      << "source review: "
      << record.source_review_id
      << "\n"
      << "source analysis: "
      << record.source_analysis_id
      << "\n"
      << "cluster: "
      << record.cluster_key
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
      << "evidence revision: "
      << record.evidence_revision
      << "\n"
      << "outcome reviewer: "
      << record.outcome_reviewer_ref
      << "\n"
      << "approver: "
      << record.approver_ref
      << "\n"
      << "rationale: "
      << record.rationale
      << "\n\n";


  if (record.data_review_task.has_value()) {
    const auto& task =
        *record.data_review_task;

    output
        << "DATA REVIEW TASK\n"
        << "  id: "
        << task.id
        << "\n"
        << "  target: "
        << task.review_target_key
        << "\n"
        << "  manual review authorized: "
        << (task.manual_review_authorized
                ? "yes"
                : "no")
        << "\n"
        << "  map change allowed: "
        << (task.map_change_allowed
                ? "yes"
                : "no")
        << "\n"
        << "  routing change allowed: "
        << (task.routing_change_allowed
                ? "yes"
                : "no")
        << "\n"
        << "  automatic publish allowed: "
        << (task.automatic_publish_allowed
                ? "yes"
                : "no")
        << "\n"
        << "  evidence scope promotion allowed: "
        << (task.evidence_scope_promotion_allowed
                ? "yes"
                : "no")
        << "\n";
  }


  if (record.question_delivery_candidate.has_value()) {
    const auto& candidate =
        *record.question_delivery_candidate;

    output
        << "QUESTION DELIVERY CANDIDATE\n"
        << "  id: "
        << candidate.id
        << "\n"
        << "  prompt key: "
        << candidate.prompt_key
        << "\n"
        << "  post-drive only: "
        << (candidate.post_drive_only
                ? "yes"
                : "no")
        << "\n"
        << "  explicit presentation required: "
        << (candidate.explicit_presentation_required
                ? "yes"
                : "no")
        << "\n"
        << "  automatic presentation allowed: "
        << (candidate.automatic_presentation_allowed
                ? "yes"
                : "no")
        << "\n"
        << "  QuestionCandidate created: "
        << (candidate.question_candidate_created
                ? "yes"
                : "no")
        << "\n"
        << "  production application allowed: "
        << (candidate.production_application_allowed
                ? "yes"
                : "no")
        << "\n"
        << "  evidence scope promotion allowed: "
        << (candidate.evidence_scope_promotion_allowed
                ? "yes"
                : "no")
        << "\n";
  }


  if (record.hypothesis_conversion_candidate.has_value()) {
    const auto& candidate =
        *record.hypothesis_conversion_candidate;

    output
        << "HYPOTHESIS CONVERSION CANDIDATE\n"
        << "  id: "
        << candidate.id
        << "\n"
        << "  hypothesis key: "
        << candidate.hypothesis_key
        << "\n"
        << "  explicit semantic mapping required: "
        << (candidate.explicit_semantic_mapping_required
                ? "yes"
                : "no")
        << "\n"
        << "  PreferenceTarget created: "
        << (candidate.preference_target_created
                ? "yes"
                : "no")
        << "\n"
        << "  PreferenceHypothesis created: "
        << (candidate.preference_hypothesis_created
                ? "yes"
                : "no")
        << "\n"
        << "  LearningGate invoked: "
        << (candidate.learning_gate_invoked
                ? "yes"
                : "no")
        << "\n"
        << "  ShadowEvaluation created: "
        << (candidate.shadow_evaluation_created
                ? "yes"
                : "no")
        << "\n"
        << "  production application allowed: "
        << (candidate.production_application_allowed
                ? "yes"
                : "no")
        << "\n"
        << "  evidence scope promotion allowed: "
        << (candidate.evidence_scope_promotion_allowed
                ? "yes"
                : "no")
        << "\n";
  }


  if (record.decision ==
          ProposalApprovalDecision::Reject &&
      !record.data_review_task.has_value() &&
      !record.question_delivery_candidate.has_value() &&
      !record.hypothesis_conversion_candidate.has_value()) {
    output
        << "DOWNSTREAM ARTIFACT\n"
        << "  created: no\n";
  }

  return output.str();
}

}  // namespace routing::core::intelligence
