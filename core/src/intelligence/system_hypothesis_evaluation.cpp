#include "routing/core/intelligence/system_hypothesis_evaluation.hpp"

#include <stdexcept>
#include <string>
#include <unordered_set>
#include <utility>

namespace routing::core::intelligence {

namespace {

struct SelectedSystemHypothesis {
  const SystemHypothesisBase* base = nullptr;
  std::string target_key;
};


void validate_system_hypothesis_base(
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
        "System hypothesis identity is incomplete.");
  }

  if (base.evidence_revision == 0) {
    throw std::invalid_argument(
        "System hypothesis requires evidence revision.");
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
        "Unsafe system hypothesis cannot be evaluated.");
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
      mapping.rationale.empty()) {
    throw std::invalid_argument(
        "System hypothesis evaluation requires complete mapping identity.");
  }

  if (mapping.evidence_revision == 0) {
    throw std::invalid_argument(
        "System hypothesis evaluation requires mapping evidence revision.");
  }

  if (mapping.decision !=
      SemanticHypothesisMappingDecision::Map) {
    throw std::logic_error(
        "Rejected semantic mapping has no system hypothesis to evaluate.");
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
        "Mapped record must contain exactly one system hypothesis.");
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

      validate_system_hypothesis_base(
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

      validate_system_hypothesis_base(
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

      validate_system_hypothesis_base(
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
    const SystemHypothesisBase& hypothesis,
    const SystemHypothesisEvaluationRequest& request) {
  if (request.evaluation_id.empty()) {
    throw std::invalid_argument(
        "System hypothesis evaluation requires evaluation_id.");
  }

  if (request.evaluator_ref.empty()) {
    throw std::invalid_argument(
        "System hypothesis evaluation requires evaluator_ref.");
  }

  if (request.evaluation_revision == 0) {
    throw std::invalid_argument(
        "System hypothesis evaluation requires non-zero revision.");
  }

  if (request.evidence.empty()) {
    throw std::invalid_argument(
        "System hypothesis evaluation requires explicit evidence.");
  }

  if (request.rationale.empty()) {
    throw std::invalid_argument(
        "System hypothesis evaluation requires rationale.");
  }


  std::unordered_set<std::string>
      evidence_ids;

  bool has_supporting =
      false;

  bool has_refuting =
      false;


  for (const auto& evidence :
       request.evidence) {
    if (evidence.evidence_id.empty() ||
        evidence.source_ref.empty() ||
        evidence.data_scope_key.empty() ||
        evidence.context_key.empty() ||
        evidence.detail.empty()) {
      throw std::invalid_argument(
          "System hypothesis evidence reference is incomplete.");
    }

    if (!evidence_ids.insert(
            evidence.evidence_id).second) {
      throw std::invalid_argument(
          "System hypothesis evaluation contains duplicate evidence_id.");
    }

    if (evidence.data_scope_key !=
        hypothesis.data_scope_key) {
      throw std::logic_error(
          "System hypothesis evaluation must preserve evidence scope.");
    }

    if (evidence.context_key !=
        hypothesis.context_key) {
      throw std::logic_error(
          "System hypothesis evaluation must preserve context.");
    }

    if (evidence.relation ==
        SystemHypothesisEvidenceRelation::Supports) {
      has_supporting =
          true;
    }

    if (evidence.relation ==
        SystemHypothesisEvidenceRelation::Refutes) {
      has_refuting =
          true;
    }
  }


  // This is a consistency guard, not result inference.
  //
  // The evaluator still chooses the final result explicitly.
  if (request.result ==
          SystemHypothesisEvaluationResult::Supported &&
      !has_supporting) {
    throw std::logic_error(
        "Supported evaluation requires supporting evidence.");
  }

  if (request.result ==
          SystemHypothesisEvaluationResult::Refuted &&
      !has_refuting) {
    throw std::logic_error(
        "Refuted evaluation requires refuting evidence.");
  }
}


bool same_request_identity(
    const SystemHypothesisEvaluationRecord& existing,
    const SystemHypothesisBase& hypothesis,
    const SystemHypothesisEvaluationRequest& request) {
  return
      existing.system_hypothesis_id ==
          hypothesis.id &&
      existing.evaluator_ref ==
          request.evaluator_ref &&
      existing.evaluation_revision ==
          request.evaluation_revision &&
      existing.result ==
          request.result &&
      existing.evidence ==
          request.evidence &&
      existing.rationale ==
          request.rationale;
}


SystemHypothesisEvaluationRecord
make_record(
    const SemanticHypothesisMappingRecord& mapping,
    const SelectedSystemHypothesis& selected,
    const SystemHypothesisEvaluationRequest& request) {
  const auto& hypothesis =
      *selected.base;

  SystemHypothesisEvaluationRecord record;

  record.evaluation_id =
      request.evaluation_id;

  record.evaluation_revision =
      request.evaluation_revision;

  record.system_hypothesis_id =
      hypothesis.id;

  record.hypothesis_kind =
      mapping.kind;

  record.hypothesis_target_key =
      selected.target_key;

  record.source_mapping_id =
      hypothesis.source_mapping_id;

  record.source_approval_id =
      hypothesis.source_approval_id;

  record.source_conversion_candidate_id =
      hypothesis.source_conversion_candidate_id;

  record.source_outcome_id =
      hypothesis.source_outcome_id;

  record.source_review_id =
      hypothesis.source_review_id;

  record.source_analysis_id =
      hypothesis.source_analysis_id;

  record.cluster_key =
      hypothesis.cluster_key;

  record.context_key =
      hypothesis.context_key;

  record.data_scope_key =
      hypothesis.data_scope_key;

  record.diagnostic_code =
      hypothesis.diagnostic_code;

  record.hypothesis_evidence_revision =
      hypothesis.evidence_revision;

  record.source_hypothesis_key =
      hypothesis.source_hypothesis_key;

  record.evaluator_ref =
      request.evaluator_ref;

  record.result =
      request.result;

  record.evidence =
      request.evidence;

  record.rationale =
      request.rationale;


  for (const auto& evidence :
       record.evidence) {
    switch (evidence.relation) {
      case SystemHypothesisEvidenceRelation::Supports:
        ++record.supporting_evidence_count;
        break;

      case SystemHypothesisEvidenceRelation::Refutes:
        ++record.refuting_evidence_count;
        break;

      case SystemHypothesisEvidenceRelation::Context:
        ++record.context_evidence_count;
        break;
    }
  }

  return record;
}


}  // namespace


const SystemHypothesisEvaluationRecord*
SystemHypothesisEvaluationWorkflow::find_by_evaluation_id(
    const std::string_view evaluation_id) const {
  for (const auto& record :
       records_) {
    if (record.evaluation_id ==
        evaluation_id) {
      return &record;
    }
  }

  return nullptr;
}


const SystemHypothesisEvaluationRecord*
SystemHypothesisEvaluationWorkflow::latest_for_system_hypothesis(
    const std::string_view system_hypothesis_id) const {
  const SystemHypothesisEvaluationRecord*
      latest = nullptr;

  for (const auto& record :
       records_) {
    if (record.system_hypothesis_id !=
        system_hypothesis_id) {
      continue;
    }

    if (latest == nullptr ||
        record.evaluation_revision >
            latest->evaluation_revision) {
      latest =
          &record;
    }
  }

  return latest;
}


SystemHypothesisEvaluationApplyResult
SystemHypothesisEvaluationWorkflow::apply(
    const SemanticHypothesisMappingRecord& mapping,
    const SystemHypothesisEvaluationRequest& request) {
  const auto selected =
      select_system_hypothesis(
          mapping);

  validate_request(
      *selected.base,
      request);


  // Exact repeated request is idempotent.
  if (const auto* existing =
          find_by_evaluation_id(
              request.evaluation_id);
      existing != nullptr) {
    if (!same_request_identity(
            *existing,
            *selected.base,
            request)) {
      throw std::invalid_argument(
          "Evaluation id collision with different evaluation identity.");
    }

    return {
        SystemHypothesisEvaluationApplyStatus::
            DuplicateIgnored,
        *existing,
    };
  }


  const auto* latest =
      latest_for_system_hypothesis(
          selected.base->id);

  const std::uint64_t expected_revision =
      latest == nullptr
          ? 1
          : latest->evaluation_revision + 1;


  if (request.evaluation_revision !=
      expected_revision) {
    throw std::logic_error(
        "System hypothesis evaluation revision must advance exactly by one.");
  }


  auto record =
      make_record(
          mapping,
          selected,
          request);

  records_.push_back(
      record);

  return {
      SystemHypothesisEvaluationApplyStatus::Created,
      std::move(record),
  };
}

}  // namespace routing::core::intelligence
