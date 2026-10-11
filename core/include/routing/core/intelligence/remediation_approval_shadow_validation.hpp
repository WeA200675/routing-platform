#pragma once

#include <cstdint>
#include <optional>
#include <string>
#include <string_view>
#include <vector>

#include "routing/core/intelligence/remediation_proposal.hpp"

namespace routing::core::intelligence {

inline constexpr std::uint32_t
kRemediationApprovalShadowValidationSchemaVersion = 1;


enum class RemediationApprovalDecision : std::uint8_t {
  Approve = 0,
  Reject,
};


enum class RemediationValidationEnvironment : std::uint8_t {
  UnitTest = 0,
  RegressionFixture,
  ShadowSandbox,
  Staging,
};


enum class RemediationShadowValidationResult : std::uint8_t {
  Passed = 0,
  Failed,
  Inconclusive,
};


enum class RemediationShadowEvidenceRelation : std::uint8_t {
  Supports = 0,
  Regresses,
  Context,
};


[[nodiscard]]
constexpr std::string_view
remediation_approval_decision_key(
    const RemediationApprovalDecision decision) {
  switch (decision) {
    case RemediationApprovalDecision::Approve:
      return "approve";

    case RemediationApprovalDecision::Reject:
      return "reject";
  }

  return "unknown";
}


[[nodiscard]]
constexpr std::string_view
remediation_validation_environment_key(
    const RemediationValidationEnvironment environment) {
  switch (environment) {
    case RemediationValidationEnvironment::UnitTest:
      return "unit-test";

    case RemediationValidationEnvironment::RegressionFixture:
      return "regression-fixture";

    case RemediationValidationEnvironment::ShadowSandbox:
      return "shadow-sandbox";

    case RemediationValidationEnvironment::Staging:
      return "staging";
  }

  return "unknown";
}


[[nodiscard]]
constexpr std::string_view
remediation_shadow_validation_result_key(
    const RemediationShadowValidationResult result) {
  switch (result) {
    case RemediationShadowValidationResult::Passed:
      return "passed";

    case RemediationShadowValidationResult::Failed:
      return "failed";

    case RemediationShadowValidationResult::Inconclusive:
      return "inconclusive";
  }

  return "unknown";
}


[[nodiscard]]
constexpr std::string_view
remediation_shadow_evidence_relation_key(
    const RemediationShadowEvidenceRelation relation) {
  switch (relation) {
    case RemediationShadowEvidenceRelation::Supports:
      return "supports";

    case RemediationShadowEvidenceRelation::Regresses:
      return "regresses";

    case RemediationShadowEvidenceRelation::Context:
      return "context";
  }

  return "unknown";
}


struct RemediationApprovalRequest {
  // Stable caller-supplied idempotency key.
  std::string approval_id;

  // Explicit human/operator identity.
  std::string approver_ref;

  RemediationApprovalDecision decision =
      RemediationApprovalDecision::Approve;

  std::string rationale;
};


// Approved proposal may enter isolated validation only.
//
// This is deliberately NOT the preference-learning
// ShadowEvaluationCandidate.
struct RemediationShadowValidationCandidate {
  std::uint32_t schema_version =
      kRemediationApprovalShadowValidationSchemaVersion;

  std::string id;

  std::string source_approval_id;
  std::string source_proposal_id;

  RemediationProposalKind kind =
      RemediationProposalKind::Data;

  std::string source_evaluation_id;
  std::uint64_t source_evaluation_revision = 0;

  std::string system_hypothesis_id;
  std::string hypothesis_target_key;

  std::string context_key;
  std::string data_scope_key;
  std::string diagnostic_code;

  std::uint64_t hypothesis_evidence_revision = 0;

  std::string remediation_key;

  std::string proposer_ref;
  std::string approver_ref;
  std::string rationale;

  // Hard environment boundary.
  bool isolated_validation_only = true;

  bool production_traffic_allowed = false;
  bool real_user_impact_allowed = false;

  bool implementation_task_created = false;
  bool deployment_candidate_created = false;

  bool automatic_apply_allowed = false;

  bool data_write_allowed = false;
  bool backend_change_allowed = false;
  bool candidate_pipeline_change_allowed = false;

  bool map_change_allowed = false;
  bool routing_change_allowed = false;
  bool cost_engine_change_allowed = false;

  bool preference_interpretation_allowed = false;
  bool preference_hypothesis_created = false;
  bool learning_gate_invoked = false;

  bool production_application_allowed = false;
  bool evidence_scope_promotion_allowed = false;
};


struct RemediationApprovalRecord {
  std::uint32_t schema_version =
      kRemediationApprovalShadowValidationSchemaVersion;

  std::string approval_id;

  std::string source_proposal_id;

  RemediationProposalKind kind =
      RemediationProposalKind::Data;

  std::string source_evaluation_id;
  std::uint64_t source_evaluation_revision = 0;

  std::string system_hypothesis_id;
  std::string hypothesis_target_key;

  std::string context_key;
  std::string data_scope_key;
  std::string diagnostic_code;

  std::uint64_t hypothesis_evidence_revision = 0;

  std::string remediation_key;

  std::string proposer_ref;
  std::string approver_ref;

  RemediationApprovalDecision decision =
      RemediationApprovalDecision::Approve;

  std::string rationale;

  // Populated only for Approve.
  std::optional<RemediationShadowValidationCandidate>
      shadow_validation_candidate;
};


enum class RemediationApprovalApplyStatus : std::uint8_t {
  Created = 0,
  DuplicateIgnored,
};


struct RemediationApprovalApplyResult {
  RemediationApprovalApplyStatus status =
      RemediationApprovalApplyStatus::Created;

  RemediationApprovalRecord record;
};


// Explicit remediation selection ledger.
//
// Multiple alternatives may have been proposed for one Supported
// evaluation, but at most one may be approved for validation.
//
// Approval freshness rule:
//
// The proposal's source evaluation must still be the latest evaluation
// revision and must still be Supported.
//
// This workflow does NOT execute or validate the remediation.
class RemediationApprovalWorkflow {
 public:
  [[nodiscard]]
  RemediationApprovalApplyResult apply(
      const RemediationProposalRecord& proposal,
      const std::vector<SystemHypothesisEvaluationRecord>&
          evaluation_history,
      const RemediationApprovalRequest& request);

  [[nodiscard]]
  const std::vector<RemediationApprovalRecord>&
  records() const noexcept {
    return records_;
  }

 private:
  [[nodiscard]]
  const RemediationApprovalRecord*
  find_by_approval_id(
      std::string_view approval_id) const;

  [[nodiscard]]
  const RemediationApprovalRecord*
  find_by_proposal_id(
      std::string_view proposal_id) const;

  [[nodiscard]]
  const RemediationApprovalRecord*
  find_approved_for_evaluation(
      std::string_view system_hypothesis_id,
      std::string_view evaluation_id,
      std::uint64_t evaluation_revision) const;

  std::vector<RemediationApprovalRecord>
      records_;
};


struct RemediationShadowEvidenceReference {
  std::string evidence_id;
  std::string source_ref;

  std::string data_scope_key;
  std::string context_key;

  RemediationShadowEvidenceRelation relation =
      RemediationShadowEvidenceRelation::Context;

  std::string detail;

  bool operator==(
      const RemediationShadowEvidenceReference&) const = default;
};


struct RemediationShadowValidationRequest {
  // Stable caller-supplied idempotency key.
  std::string validation_id;

  std::string validator_ref;

  // Per-approved-candidate validation revision.
  //
  // First = 1.
  // Later revisions must increase exactly by one.
  std::uint64_t validation_revision = 0;

  // Production is intentionally not representable by this enum.
  RemediationValidationEnvironment environment =
      RemediationValidationEnvironment::RegressionFixture;

  RemediationShadowValidationResult result =
      RemediationShadowValidationResult::Inconclusive;

  std::vector<RemediationShadowEvidenceReference>
      evidence;

  std::string rationale;
};


struct RemediationShadowValidationRecord {
  std::uint32_t schema_version =
      kRemediationApprovalShadowValidationSchemaVersion;

  std::string validation_id;
  std::uint64_t validation_revision = 0;

  std::string source_approval_id;
  std::string source_proposal_id;
  std::string source_validation_candidate_id;

  RemediationProposalKind kind =
      RemediationProposalKind::Data;

  std::string source_evaluation_id;
  std::uint64_t source_evaluation_revision = 0;

  std::string system_hypothesis_id;
  std::string hypothesis_target_key;

  std::string context_key;
  std::string data_scope_key;
  std::string diagnostic_code;

  std::uint64_t hypothesis_evidence_revision = 0;

  std::string remediation_key;

  std::string approver_ref;
  std::string validator_ref;

  RemediationValidationEnvironment environment =
      RemediationValidationEnvironment::RegressionFixture;

  RemediationShadowValidationResult result =
      RemediationShadowValidationResult::Inconclusive;

  std::vector<RemediationShadowEvidenceReference>
      evidence;

  std::uint32_t supporting_evidence_count = 0;
  std::uint32_t regression_evidence_count = 0;
  std::uint32_t context_evidence_count = 0;

  std::string rationale;

  // Passing validation is still not authorization to implement.
  bool implementation_candidate_created = false;
  bool implementation_task_created = false;
  bool deployment_candidate_created = false;

  bool automatic_apply_allowed = false;

  bool data_write_allowed = false;
  bool backend_change_allowed = false;
  bool candidate_pipeline_change_allowed = false;

  bool map_change_allowed = false;
  bool routing_change_allowed = false;
  bool cost_engine_change_allowed = false;

  bool preference_interpretation_allowed = false;
  bool preference_hypothesis_created = false;
  bool learning_gate_invoked = false;

  bool production_application_allowed = false;
  bool evidence_scope_promotion_allowed = false;
};


enum class RemediationShadowValidationApplyStatus :
    std::uint8_t {
  Created = 0,
  DuplicateIgnored,
};


struct RemediationShadowValidationApplyResult {
  RemediationShadowValidationApplyStatus status =
      RemediationShadowValidationApplyStatus::Created;

  RemediationShadowValidationRecord record;
};


// Versioned isolated validation ledger.
//
// Freshness is checked again at validation time. If a newer system
// hypothesis evaluation appeared after approval, the old remediation
// candidate is stale and cannot be validated further.
//
// A Passed result still creates no implementation or deployment right.
class RemediationShadowValidationWorkflow {
 public:
  [[nodiscard]]
  RemediationShadowValidationApplyResult apply(
      const RemediationApprovalRecord& approval,
      const std::vector<SystemHypothesisEvaluationRecord>&
          evaluation_history,
      const RemediationShadowValidationRequest& request);

  [[nodiscard]]
  const std::vector<RemediationShadowValidationRecord>&
  records() const noexcept {
    return records_;
  }

 private:
  [[nodiscard]]
  const RemediationShadowValidationRecord*
  find_by_validation_id(
      std::string_view validation_id) const;

  [[nodiscard]]
  const RemediationShadowValidationRecord*
  latest_for_candidate(
      std::string_view candidate_id) const;

  std::vector<RemediationShadowValidationRecord>
      records_;
};

}  // namespace routing::core::intelligence
