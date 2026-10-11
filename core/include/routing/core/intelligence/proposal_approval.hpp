#pragma once

#include <cstdint>
#include <optional>
#include <string>
#include <string_view>
#include <vector>

#include "routing/core/intelligence/reviewed_analysis_outcome.hpp"

namespace routing::core::intelligence {

inline constexpr std::uint32_t
kProposalApprovalSchemaVersion = 1;


// Explicit terminal review of one ReviewedAnalysisOutcome.
//
// Approval is not application.
// Rejection is retained as durable workflow evidence.
enum class ProposalApprovalDecision : std::uint8_t {
  Approve = 0,
  Reject,
};


[[nodiscard]]
constexpr std::string_view
proposal_approval_decision_key(
    const ProposalApprovalDecision decision) {
  switch (decision) {
    case ProposalApprovalDecision::Approve:
      return "approve";

    case ProposalApprovalDecision::Reject:
      return "reject";
  }

  return "unknown";
}


struct ProposalApprovalRequest {
  // Caller-supplied stable idempotency key.
  std::string approval_id;

  // Stable human/operator identity reference.
  std::string approver_ref;

  ProposalApprovalDecision decision =
      ProposalApprovalDecision::Approve;

  // Explicit review rationale.
  std::string rationale;
};


// Approved manual data-review work.
//
// Approval creates the task, but grants no map/routing mutation.
struct DataReviewTask {
  std::uint32_t schema_version =
      kProposalApprovalSchemaVersion;

  std::string id;

  std::string source_outcome_id;
  std::string source_review_id;
  std::string source_analysis_id;

  std::string cluster_key;
  std::string context_key;
  std::string data_scope_key;
  std::string diagnostic_code;

  std::uint64_t evidence_revision = 0;

  std::string review_target_key;

  std::string approver_ref;
  std::string rationale;

  // The only permission this type represents.
  bool manual_review_authorized = true;

  bool map_change_allowed = false;
  bool routing_change_allowed = false;
  bool automatic_publish_allowed = false;
  bool evidence_scope_promotion_allowed = false;
};


// Approved candidate for later question-delivery handling.
//
// This still is NOT the existing DriveSession QuestionCandidate.
// Nothing is displayed by merely creating this object.
struct QuestionDeliveryCandidate {
  std::uint32_t schema_version =
      kProposalApprovalSchemaVersion;

  std::string id;

  std::string source_outcome_id;
  std::string source_review_id;
  std::string source_analysis_id;

  std::string cluster_key;
  std::string context_key;
  std::string data_scope_key;
  std::string diagnostic_code;

  std::uint64_t evidence_revision = 0;

  std::string prompt_key;

  std::string approver_ref;
  std::string rationale;

  bool post_drive_only = true;

  // A later explicit delivery decision is still required.
  bool explicit_presentation_required = true;

  bool automatic_presentation_allowed = false;
  bool question_candidate_created = false;

  bool answer_application_allowed = false;
  bool production_application_allowed = false;
  bool evidence_scope_promotion_allowed = false;
};


// Approved candidate for a future semantic hypothesis conversion.
//
// This is deliberately NOT PreferenceHypothesis.
//
// In particular this type still has:
//   - no PreferenceTarget,
//   - no PreferenceDirection,
//   - no strength,
//   - no confidence,
//   - no learning permission,
//   - no shadow permission,
//   - no production permission.
struct HypothesisConversionCandidate {
  std::uint32_t schema_version =
      kProposalApprovalSchemaVersion;

  std::string id;

  std::string source_outcome_id;
  std::string source_review_id;
  std::string source_analysis_id;

  std::string cluster_key;
  std::string context_key;
  std::string data_scope_key;
  std::string diagnostic_code;

  std::uint64_t evidence_revision = 0;

  std::string hypothesis_key;

  std::string approver_ref;
  std::string rationale;

  // A later domain-specific semantic mapping is mandatory before any
  // conversion into another hypothesis type could even be considered.
  bool explicit_semantic_mapping_required = true;

  bool preference_target_created = false;
  bool preference_hypothesis_created = false;
  bool learning_gate_invoked = false;
  bool shadow_evaluation_created = false;

  bool production_application_allowed = false;
  bool evidence_scope_promotion_allowed = false;
};


struct ProposalApprovalRecord {
  std::uint32_t schema_version =
      kProposalApprovalSchemaVersion;

  std::string approval_id;

  std::string source_outcome_id;
  std::string source_review_id;
  std::string source_analysis_id;

  ReviewedAnalysisOutcomeKind outcome_kind =
      ReviewedAnalysisOutcomeKind::
          DataReviewCandidate;

  std::string outcome_reviewer_ref;
  std::string approver_ref;

  std::string cluster_key;
  std::string context_key;
  std::string data_scope_key;
  std::string diagnostic_code;

  std::uint64_t evidence_revision = 0;

  ProposalApprovalDecision decision =
      ProposalApprovalDecision::Approve;

  std::string rationale;

  // Exactly one may be populated for an approved record.
  // None are populated for a rejected record.
  std::optional<DataReviewTask>
      data_review_task;

  std::optional<QuestionDeliveryCandidate>
      question_delivery_candidate;

  std::optional<HypothesisConversionCandidate>
      hypothesis_conversion_candidate;
};


enum class ProposalApprovalApplyStatus : std::uint8_t {
  Created = 0,
  DuplicateIgnored,
};


struct ProposalApprovalApplyResult {
  ProposalApprovalApplyStatus status =
      ProposalApprovalApplyStatus::Created;

  ProposalApprovalRecord record;
};


// Explicit, idempotent approval ledger.
//
// One ReviewedAnalysisOutcome may receive exactly one terminal approval
// record. A duplicate identical approval_id is idempotent; conflicting
// or second approvals are rejected.
//
// This workflow does NOT:
//   - mutate anomaly/review state,
//   - enqueue IntelligenceJobs,
//   - present questions,
//   - create QuestionCandidate,
//   - create PreferenceTarget,
//   - create PreferenceHypothesis,
//   - invoke LearningGate,
//   - create ShadowEvaluationCandidate,
//   - mutate map/routing data,
//   - promote evidence scope.
class ProposalApprovalWorkflow {
 public:
  [[nodiscard]]
  ProposalApprovalApplyResult apply(
      const ReviewedAnalysisOutcomeRecord& outcome,
      const ProposalApprovalRequest& request);

  [[nodiscard]]
  const std::vector<ProposalApprovalRecord>&
  records() const noexcept {
    return records_;
  }

 private:
  [[nodiscard]]
  const ProposalApprovalRecord*
  find_by_approval_id(
      std::string_view approval_id) const;

  [[nodiscard]]
  const ProposalApprovalRecord*
  find_by_outcome_id(
      std::string_view outcome_id) const;

  std::vector<ProposalApprovalRecord>
      records_;
};

}  // namespace routing::core::intelligence
