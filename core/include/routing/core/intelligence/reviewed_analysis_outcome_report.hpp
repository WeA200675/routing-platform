#pragma once

#include <sstream>
#include <string>

#include "routing/core/intelligence/reviewed_analysis_outcome.hpp"

namespace routing::core::intelligence {

[[nodiscard]]
inline std::string
format_reviewed_analysis_outcome_report(
    const ReviewedAnalysisOutcomeRecord& record) {
  std::ostringstream output;

  output
      << "REVIEWED ANALYSIS OUTCOME\n"
      << "schema: "
      << record.schema_version
      << "\n"
      << "outcome id: "
      << record.outcome_id
      << "\n"
      << "kind: "
      << reviewed_analysis_outcome_kind_key(
             record.kind)
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
      << "reviewer: "
      << record.outcome_reviewer_ref
      << "\n"
      << "rationale: "
      << record.rationale
      << "\n\n";


  if (record.data_review_candidate.has_value()) {
    const auto& candidate =
        *record.data_review_candidate;

    output
        << "DATA REVIEW CANDIDATE\n"
        << "  id: "
        << candidate.id
        << "\n"
        << "  target: "
        << candidate.review_target_key
        << "\n"
        << "  map change allowed: "
        << (candidate.map_change_allowed
                ? "yes"
                : "no")
        << "\n"
        << "  routing change allowed: "
        << (candidate.routing_change_allowed
                ? "yes"
                : "no")
        << "\n"
        << "  automatic publish allowed: "
        << (candidate.automatic_publish_allowed
                ? "yes"
                : "no")
        << "\n"
        << "  evidence scope promotion allowed: "
        << (candidate.evidence_scope_promotion_allowed
                ? "yes"
                : "no")
        << "\n";
  }


  if (record.tester_question_proposal.has_value()) {
    const auto& proposal =
        *record.tester_question_proposal;

    output
        << "TESTER QUESTION PROPOSAL\n"
        << "  id: "
        << proposal.id
        << "\n"
        << "  prompt key: "
        << proposal.prompt_key
        << "\n"
        << "  post-drive only: "
        << (proposal.post_drive_only
                ? "yes"
                : "no")
        << "\n"
        << "  automatic presentation allowed: "
        << (proposal.automatic_presentation_allowed
                ? "yes"
                : "no")
        << "\n"
        << "  QuestionCandidate created: "
        << (proposal.question_candidate_created
                ? "yes"
                : "no")
        << "\n"
        << "  production application allowed: "
        << (proposal.production_application_allowed
                ? "yes"
                : "no")
        << "\n"
        << "  evidence scope promotion allowed: "
        << (proposal.evidence_scope_promotion_allowed
                ? "yes"
                : "no")
        << "\n";
  }


  if (record.hypothesis_proposal.has_value()) {
    const auto& proposal =
        *record.hypothesis_proposal;

    output
        << "HYPOTHESIS PROPOSAL\n"
        << "  id: "
        << proposal.id
        << "\n"
        << "  hypothesis key: "
        << proposal.hypothesis_key
        << "\n"
        << "  explicit conversion required: "
        << (proposal.explicit_conversion_required
                ? "yes"
                : "no")
        << "\n"
        << "  PreferenceHypothesis created: "
        << (proposal.preference_hypothesis_created
                ? "yes"
                : "no")
        << "\n"
        << "  LearningGate invoked: "
        << (proposal.learning_gate_invoked
                ? "yes"
                : "no")
        << "\n"
        << "  ShadowEvaluation created: "
        << (proposal.shadow_evaluation_created
                ? "yes"
                : "no")
        << "\n"
        << "  production application allowed: "
        << (proposal.production_application_allowed
                ? "yes"
                : "no")
        << "\n"
        << "  evidence scope promotion allowed: "
        << (proposal.evidence_scope_promotion_allowed
                ? "yes"
                : "no")
        << "\n";
  }

  return output.str();
}

}  // namespace routing::core::intelligence
