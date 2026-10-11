#include "routing/core/intelligence/remediation_proposal.hpp"

#include <algorithm>
#include <stdexcept>
#include <string>
#include <unordered_set>
#include <utility>

namespace routing::core::intelligence {

namespace {

struct SelectedSystemHypothesis {
  const SystemHypothesisBase* base = nullptr;

  RemediationProposalKind remediation_kind =
      RemediationProposalKind::Data;

  std::string target_key;
};


void validate_mapping_base(
    const SemanticHypothesisMappingRecord& mapping,
    const SystemHypothesisBase& base) {
  if (base.schema_version !=
      kSemanticHypothesisMappingSchemaVersion) {
    throw std::invalid_argument(
        "Unsupported system hypothesis schema.");
  }

  if (base.id.empty() ||
      base.source_mapping_id.empty() ||
      base.source_approval_id.empty() ||
      base.source_conversion_candidate_id.empty() ||
      base.source_outcome_id.empty() ||
      base.source_review_id.empty() ||
      base.source_analysis_id.empty() ||
      base.cluster_key.empty() ||
      base.context_key.empty() ||
      base.data_scope_key.empty() ||
      base.diagnostic_code.empty() ||
      base.source_hypothesis_key.empty() ||
      base.mapper_ref.empty() ||
      base.rationale.empty()) {
    throw std::invalid_argument(
        "Remediation proposal requires complete system hypothesis identity.");
  }

  if (base.evidence_revision == 0) {
    throw std::invalid_argument(
        "Remediation proposal requires hypothesis evidence revision.");
  }

  if (base.source_mapping_id !=
          mapping.mapping_id ||
      base.source_approval_id !=
          mapping.source_approval_id ||
      base.source_conversion_candidate_id !=
          mapping.source_conversion_candidate_id ||
      base.cluster_key !=
          mapping.cluster_key ||
      base.context_key !=
          mapping.context_key ||
      base.data_scope_key !=
          mapping.data_scope_key ||
      base.diagnostic_code !=
          mapping.diagnostic_code ||
      base.evidence_revision !=
          mapping.evidence_revision ||
      base.source_hypothesis_key !=
          mapping.source_hypothesis_key ||
      base.mapper_ref !=
          mapping.mapper_ref ||
      base.rationale !=
          mapping.rationale) {
    throw std::invalid_argument(
        "System hypothesis/mapping identity mismatch.");
  }

  if (base.preference_interpretation_allowed ||
      base.preference_target_created ||
      base.preference_hypothesis_created ||
      base.learning_gate_invoked ||
      base.shadow_evaluation_created ||
      base.automatic_fix_allowed ||
      base.map_change_allowed ||
      base.routing_change_allowed ||
      base.production_application_allowed ||
      base.evidence_scope_promotion_allowed) {
    throw std::logic_error(
        "Unsafe system hypothesis cannot create remediation proposal.");
  }
}


SelectedSystemHypothesis
select_system_hypothesis(
    const SemanticHypothesisMappingRecord& mapping) {
  if (mapping.schema_version !=
      kSemanticHypothesisMappingSchemaVersion) {
    throw std::invalid_argument(
        "Unsupported semantic hypothesis mapping schema.");
  }

  if (mapping.mapping_id.empty() ||
      mapping.source_approval_id.empty() ||
      mapping.source_conversion_candidate_id.empty() ||
      mapping.cluster_key.empty() ||
      mapping.context_key.empty() ||
      mapping.data_scope_key.empty() ||
      mapping.diagnostic_code.empty() ||
      mapping.source_hypothesis_key.empty() ||
      mapping.mapper_ref.empty() ||
      mapping.rationale.empty() ||
      mapping.target_key.empty()) {
    throw std::invalid_argument(
        "Remediation proposal requires complete semantic mapping identity.");
  }

  if (mapping.evidence_revision == 0) {
    throw std::invalid_argument(
        "Remediation proposal requires mapping evidence revision.");
  }

  if (mapping.decision !=
      SemanticHypothesisMappingDecision::Map) {
    throw std::logic_error(
        "Rejected semantic mapping cannot create remediation proposal.");
  }

  const unsigned int populated =
      (mapping.data_source_hypothesis.has_value()
           ? 1U
           : 0U) +
      (mapping.backend_hypothesis.has_value()
           ? 1U
           : 0U) +
      (mapping.candidate_pipeline_hypothesis.has_value()
           ? 1U
           : 0U);

  if (populated != 1U) {
    throw std::invalid_argument(
        "Semantic mapping must contain exactly one system hypothesis.");
  }


  SelectedSystemHypothesis selected;

  switch (mapping.kind) {
    case SystemHypothesisKind::DataSource: {
      if (!mapping.data_source_hypothesis.has_value() ||
          mapping.backend_hypothesis.has_value() ||
          mapping.candidate_pipeline_hypothesis.has_value()) {
        throw std::invalid_argument(
            "DataSource mapping/artifact mismatch.");
      }

      const auto& hypothesis =
          *mapping.data_source_hypothesis;

      validate_mapping_base(
          mapping,
          hypothesis.base);

      if (hypothesis.data_source_key.empty() ||
          hypothesis.data_source_key !=
              mapping.target_key) {
        throw std::invalid_argument(
            "DataSource hypothesis target mismatch.");
      }

      selected.base =
          &hypothesis.base;

      selected.remediation_kind =
          RemediationProposalKind::Data;

      selected.target_key =
          hypothesis.data_source_key;

      break;
    }


    case SystemHypothesisKind::Backend: {
      if (mapping.data_source_hypothesis.has_value() ||
          !mapping.backend_hypothesis.has_value() ||
          mapping.candidate_pipeline_hypothesis.has_value()) {
        throw std::invalid_argument(
            "Backend mapping/artifact mismatch.");
      }

      const auto& hypothesis =
          *mapping.backend_hypothesis;

      validate_mapping_base(
          mapping,
          hypothesis.base);

      if (hypothesis.backend_component_key.empty() ||
          hypothesis.backend_component_key !=
              mapping.target_key) {
        throw std::invalid_argument(
            "Backend hypothesis target mismatch.");
      }

      selected.base =
          &hypothesis.base;

      selected.remediation_kind =
          RemediationProposalKind::Backend;

      selected.target_key =
          hypothesis.backend_component_key;

      break;
    }


    case SystemHypothesisKind::CandidatePipeline: {
      if (mapping.data_source_hypothesis.has_value() ||
          mapping.backend_hypothesis.has_value() ||
          !mapping.candidate_pipeline_hypothesis.has_value()) {
        throw std::invalid_argument(
            "CandidatePipeline mapping/artifact mismatch.");
      }

      const auto& hypothesis =
          *mapping.candidate_pipeline_hypothesis;

      validate_mapping_base(
          mapping,
          hypothesis.base);

      if (hypothesis.pipeline_stage_key.empty() ||
          hypothesis.pipeline_stage_key !=
              mapping.target_key) {
        throw std::invalid_argument(
            "CandidatePipeline hypothesis target mismatch.");
      }

      selected.base =
          &hypothesis.base;

      selected.remediation_kind =
          RemediationProposalKind::CandidatePipeline;

      selected.target_key =
          hypothesis.pipeline_stage_key;

      break;
    }
  }


  if (selected.base == nullptr) {
    throw std::logic_error(
        "System hypothesis selection failed.");
  }

  return selected;
}


void validate_request(
    const RemediationProposalRequest& request) {
  if (request.proposal_id.empty()) {
    throw std::invalid_argument(
        "Remediation proposal requires proposal_id.");
  }

  if (request.proposer_ref.empty()) {
    throw std::invalid_argument(
        "Remediation proposal requires proposer_ref.");
  }

  if (request.source_evaluation_id.empty()) {
    throw std::invalid_argument(
        "Remediation proposal requires source_evaluation_id.");
  }

  if (request.source_evaluation_revision == 0) {
    throw std::invalid_argument(
        "Remediation proposal requires source evaluation revision.");
  }

  if (request.remediation_key.empty()) {
    throw std::invalid_argument(
        "Remediation proposal requires explicit remediation_key.");
  }

  if (request.rationale.empty()) {
    throw std::invalid_argument(
        "Remediation proposal requires rationale.");
  }
}


void validate_evaluation_identity(
    const SemanticHypothesisMappingRecord& mapping,
    const SelectedSystemHypothesis& selected,
    const SystemHypothesisEvaluationRecord& evaluation) {
  const auto& hypothesis =
      *selected.base;

  if (evaluation.schema_version !=
      kSystemHypothesisEvaluationSchemaVersion) {
    throw std::invalid_argument(
        "Unsupported system hypothesis evaluation schema.");
  }

  if (evaluation.evaluation_id.empty() ||
      evaluation.evaluation_revision == 0 ||
      evaluation.system_hypothesis_id.empty() ||
      evaluation.hypothesis_target_key.empty() ||
      evaluation.source_mapping_id.empty() ||
      evaluation.source_approval_id.empty() ||
      evaluation.source_conversion_candidate_id.empty() ||
      evaluation.source_outcome_id.empty() ||
      evaluation.source_review_id.empty() ||
      evaluation.source_analysis_id.empty() ||
      evaluation.cluster_key.empty() ||
      evaluation.context_key.empty() ||
      evaluation.data_scope_key.empty() ||
      evaluation.diagnostic_code.empty() ||
      evaluation.hypothesis_evidence_revision == 0 ||
      evaluation.source_hypothesis_key.empty() ||
      evaluation.evaluator_ref.empty() ||
      evaluation.evidence.empty() ||
      evaluation.rationale.empty()) {
    throw std::invalid_argument(
        "Remediation proposal requires complete evaluation identity.");
  }

  if (evaluation.system_hypothesis_id !=
          hypothesis.id ||
      evaluation.hypothesis_kind !=
          mapping.kind ||
      evaluation.hypothesis_target_key !=
          selected.target_key ||
      evaluation.source_mapping_id !=
          hypothesis.source_mapping_id ||
      evaluation.source_approval_id !=
          hypothesis.source_approval_id ||
      evaluation.source_conversion_candidate_id !=
          hypothesis.source_conversion_candidate_id ||
      evaluation.source_outcome_id !=
          hypothesis.source_outcome_id ||
      evaluation.source_review_id !=
          hypothesis.source_review_id ||
      evaluation.source_analysis_id !=
          hypothesis.source_analysis_id ||
      evaluation.cluster_key !=
          hypothesis.cluster_key ||
      evaluation.context_key !=
          hypothesis.context_key ||
      evaluation.data_scope_key !=
          hypothesis.data_scope_key ||
      evaluation.diagnostic_code !=
          hypothesis.diagnostic_code ||
      evaluation.hypothesis_evidence_revision !=
          hypothesis.evidence_revision ||
      evaluation.source_hypothesis_key !=
          hypothesis.source_hypothesis_key) {
    throw std::invalid_argument(
        "Evaluation/system hypothesis identity mismatch.");
  }


  std::unordered_set<std::string>
      evidence_ids;

  std::uint32_t supporting =
      0;

  std::uint32_t refuting =
      0;

  std::uint32_t context =
      0;


  for (const auto& evidence :
       evaluation.evidence) {
    if (evidence.evidence_id.empty() ||
        evidence.source_ref.empty() ||
        evidence.data_scope_key.empty() ||
        evidence.context_key.empty() ||
        evidence.detail.empty()) {
      throw std::invalid_argument(
          "Evaluation evidence reference is incomplete.");
    }

    if (!evidence_ids.insert(
            evidence.evidence_id).second) {
      throw std::invalid_argument(
          "Evaluation contains duplicate evidence_id.");
    }

    if (evidence.data_scope_key !=
        hypothesis.data_scope_key) {
      throw std::logic_error(
          "Remediation proposal must preserve evaluation evidence scope.");
    }

    if (evidence.context_key !=
        hypothesis.context_key) {
      throw std::logic_error(
          "Remediation proposal must preserve evaluation context.");
    }

    switch (evidence.relation) {
      case SystemHypothesisEvidenceRelation::Supports:
        ++supporting;
        break;

      case SystemHypothesisEvidenceRelation::Refutes:
        ++refuting;
        break;

      case SystemHypothesisEvidenceRelation::Context:
        ++context;
        break;
    }
  }


  if (supporting !=
          evaluation.supporting_evidence_count ||
      refuting !=
          evaluation.refuting_evidence_count ||
      context !=
          evaluation.context_evidence_count) {
    throw std::invalid_argument(
        "Evaluation evidence counters do not match evidence.");
  }


  if (evaluation.result ==
          SystemHypothesisEvaluationResult::Supported &&
      supporting == 0) {
    throw std::logic_error(
        "Supported evaluation requires supporting evidence.");
  }

  if (evaluation.result ==
          SystemHypothesisEvaluationResult::Refuted &&
      refuting == 0) {
    throw std::logic_error(
        "Refuted evaluation requires refuting evidence.");
  }


  if (evaluation.remediation_proposal_created ||
      evaluation.preference_interpretation_allowed ||
      evaluation.preference_target_created ||
      evaluation.preference_hypothesis_created ||
      evaluation.learning_gate_invoked ||
      evaluation.shadow_evaluation_created ||
      evaluation.automatic_fix_allowed ||
      evaluation.map_change_allowed ||
      evaluation.routing_change_allowed ||
      evaluation.cost_engine_change_allowed ||
      evaluation.production_application_allowed ||
      evaluation.evidence_scope_promotion_allowed) {
    throw std::logic_error(
        "Unsafe evaluation cannot create remediation proposal.");
  }
}


const SystemHypothesisEvaluationRecord*
select_latest_evaluation(
    const SemanticHypothesisMappingRecord& mapping,
    const SelectedSystemHypothesis& selected,
    const std::vector<SystemHypothesisEvaluationRecord>&
        history) {
  if (history.empty()) {
    throw std::logic_error(
        "Remediation proposal requires evaluation history.");
  }


  std::unordered_set<std::uint64_t>
      revisions;

  std::unordered_set<std::string>
      evaluation_ids;

  const SystemHypothesisEvaluationRecord*
      latest = nullptr;

  std::uint64_t max_revision =
      0;


  for (const auto& evaluation :
       history) {
    if (evaluation.system_hypothesis_id !=
        selected.base->id) {
      continue;
    }

    validate_evaluation_identity(
        mapping,
        selected,
        evaluation);


    if (!revisions.insert(
            evaluation.evaluation_revision).second) {
      throw std::invalid_argument(
          "Evaluation history contains duplicate revision.");
    }

    if (!evaluation_ids.insert(
            evaluation.evaluation_id).second) {
      throw std::invalid_argument(
          "Evaluation history contains duplicate evaluation_id.");
    }


    if (evaluation.evaluation_revision >
        max_revision) {
      max_revision =
          evaluation.evaluation_revision;

      latest =
          &evaluation;
    }
  }


  if (latest == nullptr) {
    throw std::logic_error(
        "No evaluation exists for the system hypothesis.");
  }


  // Revisions must be exactly 1..N.
  //
  // With unique positive revisions:
  //
  //   count == max_revision
  //
  // proves that no revision is missing.
  if (revisions.size() !=
      max_revision) {
    throw std::logic_error(
        "Evaluation history revisions are not contiguous.");
  }


  return latest;
}


bool same_request_identity(
    const RemediationProposalRecord& existing,
    const RemediationProposalRequest& request) {
  return
      existing.source_evaluation_id ==
          request.source_evaluation_id &&
      existing.source_evaluation_revision ==
          request.source_evaluation_revision &&
      existing.proposer_ref ==
          request.proposer_ref &&
      existing.remediation_key ==
          request.remediation_key &&
      existing.rationale ==
          request.rationale;
}


RemediationProposalBase
make_base(
    const SelectedSystemHypothesis& selected,
    const SystemHypothesisEvaluationRecord& evaluation,
    const RemediationProposalRequest& request) {
  const auto& hypothesis =
      *selected.base;

  RemediationProposalBase base;

  base.id =
      std::string(
          "remediation-proposal-v1|") +
      request.proposal_id;

  base.source_evaluation_id =
      evaluation.evaluation_id;

  base.source_evaluation_revision =
      evaluation.evaluation_revision;

  base.system_hypothesis_id =
      hypothesis.id;

  base.system_hypothesis_kind =
      evaluation.hypothesis_kind;

  base.hypothesis_target_key =
      evaluation.hypothesis_target_key;

  base.source_mapping_id =
      evaluation.source_mapping_id;

  base.source_approval_id =
      evaluation.source_approval_id;

  base.source_conversion_candidate_id =
      evaluation.source_conversion_candidate_id;

  base.source_outcome_id =
      evaluation.source_outcome_id;

  base.source_review_id =
      evaluation.source_review_id;

  base.source_analysis_id =
      evaluation.source_analysis_id;

  base.cluster_key =
      evaluation.cluster_key;

  base.context_key =
      evaluation.context_key;

  base.data_scope_key =
      evaluation.data_scope_key;

  base.diagnostic_code =
      evaluation.diagnostic_code;

  base.hypothesis_evidence_revision =
      evaluation.hypothesis_evidence_revision;

  base.source_hypothesis_key =
      evaluation.source_hypothesis_key;

  base.evaluator_ref =
      evaluation.evaluator_ref;

  base.evaluation_evidence =
      evaluation.evidence;

  base.supporting_evidence_count =
      evaluation.supporting_evidence_count;

  base.refuting_evidence_count =
      evaluation.refuting_evidence_count;

  base.context_evidence_count =
      evaluation.context_evidence_count;

  base.proposer_ref =
      request.proposer_ref;

  base.remediation_key =
      request.remediation_key;

  base.rationale =
      request.rationale;

  return base;
}


DataRemediationProposal
make_data_remediation_proposal(
    const SelectedSystemHypothesis& selected,
    const SystemHypothesisEvaluationRecord& evaluation,
    const RemediationProposalRequest& request) {
  DataRemediationProposal proposal;

  proposal.base =
      make_base(
          selected,
          evaluation,
          request);

  proposal.data_source_key =
      selected.target_key;

  return proposal;
}


BackendRemediationProposal
make_backend_remediation_proposal(
    const SelectedSystemHypothesis& selected,
    const SystemHypothesisEvaluationRecord& evaluation,
    const RemediationProposalRequest& request) {
  BackendRemediationProposal proposal;

  proposal.base =
      make_base(
          selected,
          evaluation,
          request);

  proposal.backend_component_key =
      selected.target_key;

  return proposal;
}


CandidatePipelineRemediationProposal
make_candidate_pipeline_remediation_proposal(
    const SelectedSystemHypothesis& selected,
    const SystemHypothesisEvaluationRecord& evaluation,
    const RemediationProposalRequest& request) {
  CandidatePipelineRemediationProposal proposal;

  proposal.base =
      make_base(
          selected,
          evaluation,
          request);

  proposal.pipeline_stage_key =
      selected.target_key;

  return proposal;
}


RemediationProposalRecord
make_base_record(
    const SelectedSystemHypothesis& selected,
    const SystemHypothesisEvaluationRecord& evaluation,
    const RemediationProposalRequest& request) {
  RemediationProposalRecord record;

  record.proposal_id =
      request.proposal_id;

  record.kind =
      selected.remediation_kind;

  record.source_evaluation_id =
      evaluation.evaluation_id;

  record.source_evaluation_revision =
      evaluation.evaluation_revision;

  record.system_hypothesis_id =
      selected.base->id;

  record.hypothesis_target_key =
      selected.target_key;

  record.context_key =
      selected.base->context_key;

  record.data_scope_key =
      selected.base->data_scope_key;

  record.diagnostic_code =
      selected.base->diagnostic_code;

  record.hypothesis_evidence_revision =
      selected.base->evidence_revision;

  record.proposer_ref =
      request.proposer_ref;

  record.remediation_key =
      request.remediation_key;

  record.rationale =
      request.rationale;

  return record;
}


}  // namespace


const RemediationProposalRecord*
RemediationProposalWorkflow::find_by_proposal_id(
    const std::string_view proposal_id) const {
  for (const auto& record :
       records_) {
    if (record.proposal_id ==
        proposal_id) {
      return &record;
    }
  }

  return nullptr;
}


const RemediationProposalRecord*
RemediationProposalWorkflow::find_same_remediation(
    const std::string_view evaluation_id,
    const std::string_view remediation_key) const {
  for (const auto& record :
       records_) {
    if (record.source_evaluation_id ==
            evaluation_id &&
        record.remediation_key ==
            remediation_key) {
      return &record;
    }
  }

  return nullptr;
}


RemediationProposalApplyResult
RemediationProposalWorkflow::apply(
    const SemanticHypothesisMappingRecord& mapping,
    const std::vector<SystemHypothesisEvaluationRecord>&
        evaluation_history,
    const RemediationProposalRequest& request) {
  validate_request(
      request);


  const auto selected =
      select_system_hypothesis(
          mapping);


  const auto* latest =
      select_latest_evaluation(
          mapping,
          selected,
          evaluation_history);


  // The caller must bind the proposal to the actual latest revision.
  if (request.source_evaluation_id !=
          latest->evaluation_id ||
      request.source_evaluation_revision !=
          latest->evaluation_revision) {
    throw std::logic_error(
        "Remediation proposal must reference latest evaluation revision.");
  }


  if (latest->result !=
      SystemHypothesisEvaluationResult::Supported) {
    throw std::logic_error(
        "Only latest Supported evaluation may create remediation proposal.");
  }


  // Exact repeated request is idempotent.
  if (const auto* existing =
          find_by_proposal_id(
              request.proposal_id);
      existing != nullptr) {
    if (!same_request_identity(
            *existing,
            request)) {
      throw std::invalid_argument(
          "Proposal id collision with different remediation identity.");
    }

    return {
        RemediationProposalApplyStatus::
            DuplicateIgnored,
        *existing,
    };
  }


  // Multiple alternatives may exist, but the exact same remediation
  // concept must not be duplicated under another proposal id.
  if (find_same_remediation(
          latest->evaluation_id,
          request.remediation_key) != nullptr) {
    throw std::logic_error(
        "Evaluation already has this remediation key proposal.");
  }


  auto record =
      make_base_record(
          selected,
          *latest,
          request);


  switch (selected.remediation_kind) {
    case RemediationProposalKind::Data:
      record.data_remediation_proposal =
          make_data_remediation_proposal(
              selected,
              *latest,
              request);
      break;


    case RemediationProposalKind::Backend:
      record.backend_remediation_proposal =
          make_backend_remediation_proposal(
              selected,
              *latest,
              request);
      break;


    case RemediationProposalKind::CandidatePipeline:
      record.candidate_pipeline_remediation_proposal =
          make_candidate_pipeline_remediation_proposal(
              selected,
              *latest,
              request);
      break;
  }


  records_.push_back(
      record);

  return {
      RemediationProposalApplyStatus::Created,
      std::move(record),
  };
}

}  // namespace routing::core::intelligence
