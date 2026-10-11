#include "routing/core/intelligence/semantic_hypothesis_mapping.hpp"

#include <stdexcept>
#include <string>
#include <utility>

namespace routing::core::intelligence {

namespace {

void validate_request(
    const SemanticHypothesisMappingRequest& request) {
  if (request.mapping_id.empty()) {
    throw std::invalid_argument(
        "Semantic hypothesis mapping requires mapping_id.");
  }

  if (request.mapper_ref.empty()) {
    throw std::invalid_argument(
        "Semantic hypothesis mapping requires mapper_ref.");
  }

  if (request.rationale.empty()) {
    throw std::invalid_argument(
        "Semantic hypothesis mapping requires rationale.");
  }

  if (request.decision ==
      SemanticHypothesisMappingDecision::Map) {
    if (request.target_key.empty()) {
      throw std::invalid_argument(
          "Mapped system hypothesis requires explicit target_key.");
    }
  } else {
    if (!request.target_key.empty()) {
      throw std::invalid_argument(
          "Rejected semantic mapping must not define target_key.");
    }
  }
}


void validate_conversion_candidate(
    const ProposalApprovalRecord& approval,
    const HypothesisConversionCandidate& candidate) {
  if (candidate.schema_version !=
      kProposalApprovalSchemaVersion) {
    throw std::invalid_argument(
        "Unsupported HypothesisConversionCandidate schema.");
  }

  if (candidate.id.empty() ||
      candidate.hypothesis_key.empty()) {
    throw std::invalid_argument(
        "HypothesisConversionCandidate identity is incomplete.");
  }

  if (candidate.source_outcome_id !=
          approval.source_outcome_id ||
      candidate.source_review_id !=
          approval.source_review_id ||
      candidate.source_analysis_id !=
          approval.source_analysis_id ||
      candidate.cluster_key !=
          approval.cluster_key ||
      candidate.context_key !=
          approval.context_key ||
      candidate.data_scope_key !=
          approval.data_scope_key ||
      candidate.diagnostic_code !=
          approval.diagnostic_code ||
      candidate.evidence_revision !=
          approval.evidence_revision ||
      candidate.approver_ref !=
          approval.approver_ref) {
    throw std::invalid_argument(
        "HypothesisConversionCandidate/approval identity mismatch.");
  }

  if (!candidate.explicit_semantic_mapping_required) {
    throw std::logic_error(
        "HypothesisConversionCandidate must require explicit semantic mapping.");
  }

  if (candidate.preference_target_created ||
      candidate.preference_hypothesis_created ||
      candidate.learning_gate_invoked ||
      candidate.shadow_evaluation_created ||
      candidate.production_application_allowed ||
      candidate.evidence_scope_promotion_allowed) {
    throw std::logic_error(
        "Unsafe HypothesisConversionCandidate cannot be semantically mapped.");
  }
}


void validate_approval(
    const ProposalApprovalRecord& approval) {
  if (approval.schema_version !=
      kProposalApprovalSchemaVersion) {
    throw std::invalid_argument(
        "Unsupported proposal approval schema.");
  }

  if (approval.approval_id.empty() ||
      approval.source_outcome_id.empty() ||
      approval.source_review_id.empty() ||
      approval.source_analysis_id.empty() ||
      approval.approver_ref.empty() ||
      approval.cluster_key.empty() ||
      approval.context_key.empty() ||
      approval.data_scope_key.empty() ||
      approval.diagnostic_code.empty()) {
    throw std::invalid_argument(
        "Semantic mapping requires complete approval identity.");
  }

  if (approval.evidence_revision == 0) {
    throw std::invalid_argument(
        "Semantic mapping requires evidence revision.");
  }

  if (approval.decision !=
      ProposalApprovalDecision::Approve) {
    throw std::logic_error(
        "Rejected proposal cannot be semantically mapped.");
  }

  if (approval.outcome_kind !=
      ReviewedAnalysisOutcomeKind::
          HypothesisProposal) {
    throw std::logic_error(
        "Semantic hypothesis mapping requires HypothesisProposal approval.");
  }

  if (approval.data_review_task.has_value() ||
      approval.question_delivery_candidate.has_value() ||
      !approval.hypothesis_conversion_candidate.has_value()) {
    throw std::invalid_argument(
        "Proposal approval artifact does not match hypothesis mapping.");
  }

  validate_conversion_candidate(
      approval,
      *approval.hypothesis_conversion_candidate);
}


bool same_request_identity(
    const SemanticHypothesisMappingRecord& existing,
    const ProposalApprovalRecord& approval,
    const SemanticHypothesisMappingRequest& request) {
  return
      existing.source_approval_id ==
          approval.approval_id &&
      existing.mapper_ref ==
          request.mapper_ref &&
      existing.decision ==
          request.decision &&
      existing.kind ==
          request.kind &&
      existing.target_key ==
          request.target_key &&
      existing.rationale ==
          request.rationale;
}


SystemHypothesisBase
make_base(
    const ProposalApprovalRecord& approval,
    const SemanticHypothesisMappingRequest& request) {
  const auto& source =
      *approval.hypothesis_conversion_candidate;

  SystemHypothesisBase base;

  base.id =
      std::string(
          "system-hypothesis-v1|") +
      request.mapping_id;

  base.source_mapping_id =
      request.mapping_id;

  base.source_approval_id =
      approval.approval_id;

  base.source_conversion_candidate_id =
      source.id;

  base.source_outcome_id =
      approval.source_outcome_id;

  base.source_review_id =
      approval.source_review_id;

  base.source_analysis_id =
      approval.source_analysis_id;

  base.cluster_key =
      approval.cluster_key;

  base.context_key =
      approval.context_key;

  base.data_scope_key =
      approval.data_scope_key;

  base.diagnostic_code =
      approval.diagnostic_code;

  base.evidence_revision =
      approval.evidence_revision;

  base.source_hypothesis_key =
      source.hypothesis_key;

  base.mapper_ref =
      request.mapper_ref;

  base.rationale =
      request.rationale;

  return base;
}


DataSourceHypothesis
make_data_source_hypothesis(
    const ProposalApprovalRecord& approval,
    const SemanticHypothesisMappingRequest& request) {
  DataSourceHypothesis hypothesis;

  hypothesis.base =
      make_base(
          approval,
          request);

  hypothesis.data_source_key =
      request.target_key;

  return hypothesis;
}


BackendHypothesis
make_backend_hypothesis(
    const ProposalApprovalRecord& approval,
    const SemanticHypothesisMappingRequest& request) {
  BackendHypothesis hypothesis;

  hypothesis.base =
      make_base(
          approval,
          request);

  hypothesis.backend_component_key =
      request.target_key;

  return hypothesis;
}


CandidatePipelineHypothesis
make_candidate_pipeline_hypothesis(
    const ProposalApprovalRecord& approval,
    const SemanticHypothesisMappingRequest& request) {
  CandidatePipelineHypothesis hypothesis;

  hypothesis.base =
      make_base(
          approval,
          request);

  hypothesis.pipeline_stage_key =
      request.target_key;

  return hypothesis;
}


SemanticHypothesisMappingRecord
make_base_record(
    const ProposalApprovalRecord& approval,
    const SemanticHypothesisMappingRequest& request) {
  const auto& source =
      *approval.hypothesis_conversion_candidate;

  SemanticHypothesisMappingRecord record;

  record.mapping_id =
      request.mapping_id;

  record.source_approval_id =
      approval.approval_id;

  record.source_conversion_candidate_id =
      source.id;

  record.cluster_key =
      approval.cluster_key;

  record.context_key =
      approval.context_key;

  record.data_scope_key =
      approval.data_scope_key;

  record.diagnostic_code =
      approval.diagnostic_code;

  record.evidence_revision =
      approval.evidence_revision;

  record.source_hypothesis_key =
      source.hypothesis_key;

  record.mapper_ref =
      request.mapper_ref;

  record.decision =
      request.decision;

  record.kind =
      request.kind;

  record.target_key =
      request.target_key;

  record.rationale =
      request.rationale;

  return record;
}


}  // namespace


const SemanticHypothesisMappingRecord*
SemanticHypothesisMappingWorkflow::find_by_mapping_id(
    const std::string_view mapping_id) const {
  for (const auto& record :
       records_) {
    if (record.mapping_id ==
        mapping_id) {
      return &record;
    }
  }

  return nullptr;
}


const SemanticHypothesisMappingRecord*
SemanticHypothesisMappingWorkflow::find_by_source_approval_id(
    const std::string_view approval_id) const {
  for (const auto& record :
       records_) {
    if (record.source_approval_id ==
        approval_id) {
      return &record;
    }
  }

  return nullptr;
}


SemanticHypothesisMappingApplyResult
SemanticHypothesisMappingWorkflow::apply(
    const ProposalApprovalRecord& approval,
    const SemanticHypothesisMappingRequest& request) {
  validate_request(
      request);

  validate_approval(
      approval);


  if (const auto* existing =
          find_by_mapping_id(
              request.mapping_id);
      existing != nullptr) {
    if (!same_request_identity(
            *existing,
            approval,
            request)) {
      throw std::invalid_argument(
          "Mapping id collision with different mapping identity.");
    }

    return {
        SemanticHypothesisMappingApplyStatus::
            DuplicateIgnored,
        *existing,
    };
  }


  // One approved conversion candidate receives one terminal mapping.
  if (find_by_source_approval_id(
          approval.approval_id) != nullptr) {
    throw std::logic_error(
        "Approved hypothesis already has a semantic mapping record.");
  }


  auto record =
      make_base_record(
          approval,
          request);


  // Rejection is durable terminal workflow evidence and creates no
  // system-hypothesis artifact.
  if (request.decision ==
      SemanticHypothesisMappingDecision::Reject) {
    records_.push_back(
        record);

    return {
        SemanticHypothesisMappingApplyStatus::Created,
        std::move(record),
    };
  }


  // The mapper selects the system domain explicitly.
  //
  // No diagnostic code, explanation key or AI component infers this
  // mapping automatically.
  switch (request.kind) {
    case SystemHypothesisKind::DataSource:
      record.data_source_hypothesis =
          make_data_source_hypothesis(
              approval,
              request);
      break;


    case SystemHypothesisKind::Backend:
      record.backend_hypothesis =
          make_backend_hypothesis(
              approval,
              request);
      break;


    case SystemHypothesisKind::CandidatePipeline:
      record.candidate_pipeline_hypothesis =
          make_candidate_pipeline_hypothesis(
              approval,
              request);
      break;
  }


  records_.push_back(
      record);

  return {
      SemanticHypothesisMappingApplyStatus::Created,
      std::move(record),
  };
}

}  // namespace routing::core::intelligence
