#pragma once

#include <cstdint>
#include <optional>
#include <string>
#include <string_view>
#include <vector>

#include "routing/core/intelligence/proposal_approval.hpp"

namespace routing::core::intelligence {

inline constexpr std::uint32_t
kSemanticHypothesisMappingSchemaVersion = 1;


// Explicit operator decision for one approved
// HypothesisConversionCandidate.
//
// Mapping is terminal workflow evidence. It is still not application.
enum class SemanticHypothesisMappingDecision : std::uint8_t {
  Map = 0,
  Reject,
};


enum class SystemHypothesisKind : std::uint8_t {
  DataSource = 0,
  Backend,
  CandidatePipeline,
};


[[nodiscard]]
constexpr std::string_view
semantic_hypothesis_mapping_decision_key(
    const SemanticHypothesisMappingDecision decision) {
  switch (decision) {
    case SemanticHypothesisMappingDecision::Map:
      return "map";

    case SemanticHypothesisMappingDecision::Reject:
      return "reject";
  }

  return "unknown";
}


[[nodiscard]]
constexpr std::string_view
system_hypothesis_kind_key(
    const SystemHypothesisKind kind) {
  switch (kind) {
    case SystemHypothesisKind::DataSource:
      return "data-source";

    case SystemHypothesisKind::Backend:
      return "backend";

    case SystemHypothesisKind::CandidatePipeline:
      return "candidate-pipeline";
  }

  return "unknown";
}


struct SemanticHypothesisMappingRequest {
  // Caller-supplied stable idempotency key.
  std::string mapping_id;

  // Explicit human/operator identity reference.
  std::string mapper_ref;

  SemanticHypothesisMappingDecision decision =
      SemanticHypothesisMappingDecision::Map;

  // Explicit system domain. Never inferred from a diagnostic code.
  SystemHypothesisKind kind =
      SystemHypothesisKind::DataSource;

  // Required for Map.
  //
  // Meaning depends on kind:
  //   DataSource         -> data_source_key
  //   Backend            -> backend_component_key
  //   CandidatePipeline  -> pipeline_stage_key
  //
  // Reject must leave this empty.
  std::string target_key;

  std::string rationale;
};


// Shared immutable provenance/safety envelope.
//
// System hypotheses describe a possible system/data explanation only.
// They do not express driver preference.
struct SystemHypothesisBase {
  std::uint32_t schema_version =
      kSemanticHypothesisMappingSchemaVersion;

  std::string id;

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

  std::uint64_t evidence_revision = 0;

  // Preserves the explicit proposition supplied earlier.
  std::string source_hypothesis_key;

  std::string mapper_ref;
  std::string rationale;

  // Hard semantic/application boundary.
  bool preference_interpretation_allowed = false;
  bool preference_target_created = false;
  bool preference_hypothesis_created = false;

  bool learning_gate_invoked = false;
  bool shadow_evaluation_created = false;

  bool automatic_fix_allowed = false;
  bool map_change_allowed = false;
  bool routing_change_allowed = false;
  bool production_application_allowed = false;
  bool evidence_scope_promotion_allowed = false;
};


// Hypothesis about source-data semantics/coverage/quality.
//
// This is not proof that source data is wrong.
struct DataSourceHypothesis {
  SystemHypothesisBase base;

  std::string data_source_key;
};


// Hypothesis about backend or enrichment behavior.
//
// This is not proof of backend failure.
struct BackendHypothesis {
  SystemHypothesisBase base;

  std::string backend_component_key;
};


// Hypothesis about candidate generation/orchestration behavior.
//
// This is not a new scoring rule or CostEngine contribution.
struct CandidatePipelineHypothesis {
  SystemHypothesisBase base;

  std::string pipeline_stage_key;
};


struct SemanticHypothesisMappingRecord {
  std::uint32_t schema_version =
      kSemanticHypothesisMappingSchemaVersion;

  std::string mapping_id;

  std::string source_approval_id;
  std::string source_conversion_candidate_id;

  std::string cluster_key;
  std::string context_key;
  std::string data_scope_key;
  std::string diagnostic_code;

  std::uint64_t evidence_revision = 0;

  std::string source_hypothesis_key;

  std::string mapper_ref;

  SemanticHypothesisMappingDecision decision =
      SemanticHypothesisMappingDecision::Map;

  SystemHypothesisKind kind =
      SystemHypothesisKind::DataSource;

  std::string target_key;
  std::string rationale;

  // Exactly one populated for Map.
  // None populated for Reject.
  std::optional<DataSourceHypothesis>
      data_source_hypothesis;

  std::optional<BackendHypothesis>
      backend_hypothesis;

  std::optional<CandidatePipelineHypothesis>
      candidate_pipeline_hypothesis;
};


enum class SemanticHypothesisMappingApplyStatus :
    std::uint8_t {
  Created = 0,
  DuplicateIgnored,
};


struct SemanticHypothesisMappingApplyResult {
  SemanticHypothesisMappingApplyStatus status =
      SemanticHypothesisMappingApplyStatus::Created;

  SemanticHypothesisMappingRecord record;
};


// Explicit, idempotent system-hypothesis mapping ledger.
//
// One approved HypothesisConversionCandidate receives one terminal
// semantic mapping record.
//
// This workflow does NOT:
//   - create PreferenceTarget,
//   - create PreferenceHypothesis,
//   - invoke LearningGate,
//   - create ShadowEvaluationCandidate,
//   - change investigation/review/job state,
//   - modify map data,
//   - modify routing,
//   - add CostEngine semantics,
//   - promote evidence scope.
class SemanticHypothesisMappingWorkflow {
 public:
  [[nodiscard]]
  SemanticHypothesisMappingApplyResult apply(
      const ProposalApprovalRecord& approval,
      const SemanticHypothesisMappingRequest& request);

  [[nodiscard]]
  const std::vector<SemanticHypothesisMappingRecord>&
  records() const noexcept {
    return records_;
  }

 private:
  [[nodiscard]]
  const SemanticHypothesisMappingRecord*
  find_by_mapping_id(
      std::string_view mapping_id) const;

  [[nodiscard]]
  const SemanticHypothesisMappingRecord*
  find_by_source_approval_id(
      std::string_view approval_id) const;

  std::vector<SemanticHypothesisMappingRecord>
      records_;
};

}  // namespace routing::core::intelligence
