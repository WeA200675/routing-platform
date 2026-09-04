#pragma once

#include <cstdint>
#include <string>
#include <string_view>
#include <vector>

#include "routing/core/intelligence/semantic_hypothesis_mapping.hpp"

namespace routing::core::intelligence {

inline constexpr std::uint32_t
kSystemHypothesisEvaluationSchemaVersion = 1;


// Explicit evaluator conclusion.
//
// The workflow never derives this result automatically from counts,
// diagnostic codes or AI output.
enum class SystemHypothesisEvaluationResult : std::uint8_t {
  Supported = 0,
  Refuted,
  Inconclusive,
};


enum class SystemHypothesisEvidenceRelation : std::uint8_t {
  Supports = 0,
  Refutes,
  Context,
};


[[nodiscard]]
constexpr std::string_view
system_hypothesis_evaluation_result_key(
    const SystemHypothesisEvaluationResult result) {
  switch (result) {
    case SystemHypothesisEvaluationResult::Supported:
      return "supported";

    case SystemHypothesisEvaluationResult::Refuted:
      return "refuted";

    case SystemHypothesisEvaluationResult::Inconclusive:
      return "inconclusive";
  }

  return "unknown";
}


[[nodiscard]]
constexpr std::string_view
system_hypothesis_evidence_relation_key(
    const SystemHypothesisEvidenceRelation relation) {
  switch (relation) {
    case SystemHypothesisEvidenceRelation::Supports:
      return "supports";

    case SystemHypothesisEvidenceRelation::Refutes:
      return "refutes";

    case SystemHypothesisEvidenceRelation::Context:
      return "context";
  }

  return "unknown";
}


// Explicit provenance reference used by an evaluator.
//
// This does not claim the referenced source is true. It only records
// which reviewed material was considered and how the evaluator
// classified its relationship to the hypothesis.
struct SystemHypothesisEvidenceReference {
  std::string evidence_id;
  std::string source_ref;

  // Hard evidence boundary.
  std::string data_scope_key;
  std::string context_key;

  SystemHypothesisEvidenceRelation relation =
      SystemHypothesisEvidenceRelation::Context;

  std::string detail;

  bool operator==(
      const SystemHypothesisEvidenceReference&) const = default;
};


struct SystemHypothesisEvaluationRequest {
  // Caller-supplied stable idempotency key.
  std::string evaluation_id;

  // Explicit human/operator identity.
  std::string evaluator_ref;

  // Per-system-hypothesis evaluation revision.
  //
  // First evaluation must be 1.
  // Later evaluations must increase exactly by one.
  std::uint64_t evaluation_revision = 0;

  SystemHypothesisEvaluationResult result =
      SystemHypothesisEvaluationResult::Inconclusive;

  std::vector<SystemHypothesisEvidenceReference>
      evidence;

  std::string rationale;
};


struct SystemHypothesisEvaluationRecord {
  std::uint32_t schema_version =
      kSystemHypothesisEvaluationSchemaVersion;

  std::string evaluation_id;

  std::uint64_t evaluation_revision = 0;

  std::string system_hypothesis_id;

  SystemHypothesisKind hypothesis_kind =
      SystemHypothesisKind::DataSource;

  std::string hypothesis_target_key;

  std::string source_mapping_id;
  std::string source_approval_id;
  std::string source_conversion_candidate_id;
  std::string source_outcome_id;
  std::string source_review_id;
  std::string source_analysis_id;

  std::string cluster_key;
  std::string context_key;
  std::string data_scope_key;
  std::string diagnostic_code;

  // Revision of the diagnostic evidence from which the system
  // hypothesis originated.
  std::uint64_t hypothesis_evidence_revision = 0;

  std::string source_hypothesis_key;

  std::string evaluator_ref;

  SystemHypothesisEvaluationResult result =
      SystemHypothesisEvaluationResult::Inconclusive;

  std::vector<SystemHypothesisEvidenceReference>
      evidence;

  std::uint32_t supporting_evidence_count = 0;
  std::uint32_t refuting_evidence_count = 0;
  std::uint32_t context_evidence_count = 0;

  std::string rationale;

  // Hard downstream boundary.
  bool remediation_proposal_created = false;

  bool preference_interpretation_allowed = false;
  bool preference_target_created = false;
  bool preference_hypothesis_created = false;

  bool learning_gate_invoked = false;
  bool shadow_evaluation_created = false;

  bool automatic_fix_allowed = false;
  bool map_change_allowed = false;
  bool routing_change_allowed = false;
  bool cost_engine_change_allowed = false;

  bool production_application_allowed = false;
  bool evidence_scope_promotion_allowed = false;
};


enum class SystemHypothesisEvaluationApplyStatus :
    std::uint8_t {
  Created = 0,
  DuplicateIgnored,
};


struct SystemHypothesisEvaluationApplyResult {
  SystemHypothesisEvaluationApplyStatus status =
      SystemHypothesisEvaluationApplyStatus::Created;

  SystemHypothesisEvaluationRecord record;
};


// Versioned explicit evaluation ledger.
//
// Unlike mapping/approval, evaluation is not permanently terminal:
// new evidence may justify a later explicit evaluation revision.
//
// Revision rules:
//   first = 1
//   next  = previous + 1
//
// This workflow does NOT:
//   - infer Supported/Refuted automatically,
//   - mutate the source SystemHypothesis,
//   - create a remediation proposal,
//   - mutate anomaly/review/job state,
//   - create PreferenceTarget/PreferenceHypothesis,
//   - invoke LearningGate,
//   - create ShadowEvaluationCandidate,
//   - modify map/routing/CostEngine behavior,
//   - promote evidence scope.
class SystemHypothesisEvaluationWorkflow {
 public:
  [[nodiscard]]
  SystemHypothesisEvaluationApplyResult apply(
      const SemanticHypothesisMappingRecord& mapping,
      const SystemHypothesisEvaluationRequest& request);

  [[nodiscard]]
  const std::vector<SystemHypothesisEvaluationRecord>&
  records() const noexcept {
    return records_;
  }

 private:
  [[nodiscard]]
  const SystemHypothesisEvaluationRecord*
  find_by_evaluation_id(
      std::string_view evaluation_id) const;

  [[nodiscard]]
  const SystemHypothesisEvaluationRecord*
  latest_for_system_hypothesis(
      std::string_view system_hypothesis_id) const;

  std::vector<SystemHypothesisEvaluationRecord>
      records_;
};

}  // namespace routing::core::intelligence
