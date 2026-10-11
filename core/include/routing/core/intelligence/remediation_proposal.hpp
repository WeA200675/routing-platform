#pragma once

#include <cstdint>
#include <optional>
#include <string>
#include <string_view>
#include <vector>

#include "routing/core/intelligence/system_hypothesis_evaluation.hpp"

namespace routing::core::intelligence {

inline constexpr std::uint32_t
kRemediationProposalSchemaVersion = 1;


enum class RemediationProposalKind : std::uint8_t {
  Data = 0,
  Backend,
  CandidatePipeline,
};


[[nodiscard]]
constexpr std::string_view
remediation_proposal_kind_key(
    const RemediationProposalKind kind) {
  switch (kind) {
    case RemediationProposalKind::Data:
      return "data";

    case RemediationProposalKind::Backend:
      return "backend";

    case RemediationProposalKind::CandidatePipeline:
      return "candidate-pipeline";
  }

  return "unknown";
}


// Explicit proposal request.
//
// The requested remediation action is supplied by the proposer.
// It is never invented from a diagnostic code or evaluation result.
struct RemediationProposalRequest {
  // Caller-supplied stable idempotency key.
  std::string proposal_id;

  // Explicit human/operator identity.
  std::string proposer_ref;

  // Exact latest Supported evaluation this proposal relies on.
  std::string source_evaluation_id;
  std::uint64_t source_evaluation_revision = 0;

  // Stable machine-readable remediation concept.
  //
  // Examples:
  //   remediation.data.review-source-import
  //   remediation.backend.retry-trace-enrichment
  //   remediation.candidate.adjust-generation-stage
  //
  // This is only a proposal identifier. It executes nothing.
  std::string remediation_key;

  std::string rationale;
};


// Shared provenance and safety envelope.
//
// A RemediationProposal describes a possible future change.
// It does NOT authorize or implement that change.
struct RemediationProposalBase {
  std::uint32_t schema_version =
      kRemediationProposalSchemaVersion;

  std::string id;

  std::string source_evaluation_id;
  std::uint64_t source_evaluation_revision = 0;

  std::string system_hypothesis_id;

  SystemHypothesisKind system_hypothesis_kind =
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

  std::uint64_t hypothesis_evidence_revision = 0;

  std::string source_hypothesis_key;

  std::string evaluator_ref;

  std::vector<SystemHypothesisEvidenceReference>
      evaluation_evidence;

  std::uint32_t supporting_evidence_count = 0;
  std::uint32_t refuting_evidence_count = 0;
  std::uint32_t context_evidence_count = 0;

  std::string proposer_ref;
  std::string remediation_key;
  std::string rationale;

  // A later approval stage is mandatory.
  bool explicit_approval_required = true;

  // Any executable remediation will require a separate validation
  // boundary. This sprint creates neither validation nor execution.
  bool shadow_validation_required = true;

  bool approval_record_created = false;
  bool shadow_validation_created = false;
  bool implementation_task_created = false;

  bool preference_interpretation_allowed = false;
  bool preference_target_created = false;
  bool preference_hypothesis_created = false;

  bool learning_gate_invoked = false;

  bool automatic_apply_allowed = false;

  bool data_write_allowed = false;
  bool backend_change_allowed = false;
  bool candidate_pipeline_change_allowed = false;

  bool map_change_allowed = false;
  bool routing_change_allowed = false;
  bool cost_engine_change_allowed = false;

  bool production_application_allowed = false;
  bool evidence_scope_promotion_allowed = false;
};


// Proposed source-data remediation.
//
// Merely creating this object never edits imported, cached or map data.
struct DataRemediationProposal {
  RemediationProposalBase base;

  std::string data_source_key;
};


// Proposed backend/enrichment remediation.
//
// Merely creating this object never changes or deploys backend code.
struct BackendRemediationProposal {
  RemediationProposalBase base;

  std::string backend_component_key;
};


// Proposed candidate-pipeline remediation.
//
// Merely creating this object never changes candidate generation,
// CostEngine scoring or production routing.
struct CandidatePipelineRemediationProposal {
  RemediationProposalBase base;

  std::string pipeline_stage_key;
};


struct RemediationProposalRecord {
  std::uint32_t schema_version =
      kRemediationProposalSchemaVersion;

  std::string proposal_id;

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

  std::string proposer_ref;
  std::string remediation_key;
  std::string rationale;

  std::optional<DataRemediationProposal>
      data_remediation_proposal;

  std::optional<BackendRemediationProposal>
      backend_remediation_proposal;

  std::optional<CandidatePipelineRemediationProposal>
      candidate_pipeline_remediation_proposal;
};


enum class RemediationProposalApplyStatus :
    std::uint8_t {
  Created = 0,
  DuplicateIgnored,
};


struct RemediationProposalApplyResult {
  RemediationProposalApplyStatus status =
      RemediationProposalApplyStatus::Created;

  RemediationProposalRecord record;
};


// Explicit, idempotent proposal ledger.
//
// Multiple distinct remediation alternatives may be proposed from the
// same latest Supported evaluation. The same remediation_key may not be
// duplicated for that evaluation.
//
// Critical freshness rule:
//
//   Only the latest evaluation revision of the system hypothesis may
//   create a proposal, and that latest revision must be Supported.
//
// Therefore:
//
//   rev1 Supported
//   rev2 Refuted
//
// does NOT allow rev1 to create a new remediation proposal.
//
// This workflow does NOT:
//   - mutate the evaluation or hypothesis,
//   - approve a proposal,
//   - create shadow validation,
//   - create implementation work,
//   - write data,
//   - change backend or candidate pipeline,
//   - modify map/routing/CostEngine behavior,
//   - create or interpret preferences,
//   - promote evidence scope,
//   - apply anything to production.
class RemediationProposalWorkflow {
 public:
  [[nodiscard]]
  RemediationProposalApplyResult apply(
      const SemanticHypothesisMappingRecord& mapping,
      const std::vector<SystemHypothesisEvaluationRecord>&
          evaluation_history,
      const RemediationProposalRequest& request);

  [[nodiscard]]
  const std::vector<RemediationProposalRecord>&
  records() const noexcept {
    return records_;
  }

 private:
  [[nodiscard]]
  const RemediationProposalRecord*
  find_by_proposal_id(
      std::string_view proposal_id) const;

  [[nodiscard]]
  const RemediationProposalRecord*
  find_same_remediation(
      std::string_view evaluation_id,
      std::string_view remediation_key) const;

  std::vector<RemediationProposalRecord>
      records_;
};

}  // namespace routing::core::intelligence
