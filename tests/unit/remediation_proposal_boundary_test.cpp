#include <cassert>
#include <iostream>
#include <stdexcept>
#include <string>
#include <vector>

#include "routing/core/intelligence/remediation_proposal.hpp"
#include "routing/core/intelligence/remediation_proposal_report.hpp"

namespace {

routing::core::intelligence::SemanticHypothesisMappingRecord
make_mapping(
    const routing::core::intelligence::
        SystemHypothesisKind kind,
    const std::string& suffix) {
  using namespace routing::core::intelligence;

  SemanticHypothesisMappingRecord mapping;

  mapping.mapping_id =
      "mapping:" +
      suffix;

  mapping.source_approval_id =
      "approval:" +
      suffix;

  mapping.source_conversion_candidate_id =
      "conversion:" +
      suffix;

  mapping.cluster_key =
      "cluster:" +
      suffix;

  mapping.context_key =
      "context:" +
      suffix;

  mapping.data_scope_key =
      "personal";

  mapping.diagnostic_code =
      "DIAGNOSTIC_" +
      suffix;

  mapping.evidence_revision =
      4;

  mapping.source_hypothesis_key =
      "hypothesis:" +
      suffix;

  mapping.mapper_ref =
      "mapper:alpha";

  mapping.decision =
      SemanticHypothesisMappingDecision::Map;

  mapping.kind =
      kind;

  mapping.rationale =
      "Explicit system mapping.";


  SystemHypothesisBase base;

  base.id =
      "system-hypothesis-v1|" +
      mapping.mapping_id;

  base.source_mapping_id =
      mapping.mapping_id;

  base.source_approval_id =
      mapping.source_approval_id;

  base.source_conversion_candidate_id =
      mapping.source_conversion_candidate_id;

  base.source_outcome_id =
      "outcome:" +
      suffix;

  base.source_review_id =
      "review:" +
      suffix;

  base.source_analysis_id =
      "analysis:" +
      suffix;

  base.cluster_key =
      mapping.cluster_key;

  base.context_key =
      mapping.context_key;

  base.data_scope_key =
      mapping.data_scope_key;

  base.diagnostic_code =
      mapping.diagnostic_code;

  base.evidence_revision =
      mapping.evidence_revision;

  base.source_hypothesis_key =
      mapping.source_hypothesis_key;

  base.mapper_ref =
      mapping.mapper_ref;

  base.rationale =
      mapping.rationale;


  switch (kind) {
    case SystemHypothesisKind::DataSource: {
      mapping.target_key =
          "source:" +
          suffix;

      DataSourceHypothesis hypothesis;

      hypothesis.base =
          base;

      hypothesis.data_source_key =
          mapping.target_key;

      mapping.data_source_hypothesis =
          hypothesis;

      break;
    }


    case SystemHypothesisKind::Backend: {
      mapping.target_key =
          "backend:" +
          suffix;

      BackendHypothesis hypothesis;

      hypothesis.base =
          base;

      hypothesis.backend_component_key =
          mapping.target_key;

      mapping.backend_hypothesis =
          hypothesis;

      break;
    }


    case SystemHypothesisKind::CandidatePipeline: {
      mapping.target_key =
          "candidate-pipeline:" +
          suffix;

      CandidatePipelineHypothesis hypothesis;

      hypothesis.base =
          base;

      hypothesis.pipeline_stage_key =
          mapping.target_key;

      mapping.candidate_pipeline_hypothesis =
          hypothesis;

      break;
    }
  }


  return mapping;
}


const routing::core::intelligence::SystemHypothesisBase&
mapping_base(
    const routing::core::intelligence::
        SemanticHypothesisMappingRecord& mapping) {
  using namespace routing::core::intelligence;

  switch (mapping.kind) {
    case SystemHypothesisKind::DataSource:
      return mapping.data_source_hypothesis->base;

    case SystemHypothesisKind::Backend:
      return mapping.backend_hypothesis->base;

    case SystemHypothesisKind::CandidatePipeline:
      return mapping.candidate_pipeline_hypothesis->base;
  }

  throw std::logic_error(
      "Unexpected system hypothesis kind.");
}


routing::core::intelligence::SystemHypothesisEvaluationRecord
make_evaluation(
    const routing::core::intelligence::
        SemanticHypothesisMappingRecord& mapping,
    const std::uint64_t revision,
    const routing::core::intelligence::
        SystemHypothesisEvaluationResult result,
    const std::string& id) {
  using namespace routing::core::intelligence;

  const auto& base =
      mapping_base(
          mapping);

  SystemHypothesisEvaluationRecord evaluation;

  evaluation.evaluation_id =
      id;

  evaluation.evaluation_revision =
      revision;

  evaluation.system_hypothesis_id =
      base.id;

  evaluation.hypothesis_kind =
      mapping.kind;

  evaluation.hypothesis_target_key =
      mapping.target_key;

  evaluation.source_mapping_id =
      base.source_mapping_id;

  evaluation.source_approval_id =
      base.source_approval_id;

  evaluation.source_conversion_candidate_id =
      base.source_conversion_candidate_id;

  evaluation.source_outcome_id =
      base.source_outcome_id;

  evaluation.source_review_id =
      base.source_review_id;

  evaluation.source_analysis_id =
      base.source_analysis_id;

  evaluation.cluster_key =
      base.cluster_key;

  evaluation.context_key =
      base.context_key;

  evaluation.data_scope_key =
      base.data_scope_key;

  evaluation.diagnostic_code =
      base.diagnostic_code;

  evaluation.hypothesis_evidence_revision =
      base.evidence_revision;

  evaluation.source_hypothesis_key =
      base.source_hypothesis_key;

  evaluation.evaluator_ref =
      "evaluator:" +
      std::to_string(
          revision);

  evaluation.result =
      result;


  SystemHypothesisEvidenceReference evidence;

  evidence.evidence_id =
      id +
      ":evidence";

  evidence.source_ref =
      "regression:remediation-boundary";

  evidence.data_scope_key =
      base.data_scope_key;

  evidence.context_key =
      base.context_key;

  evidence.detail =
      "Explicit reviewed system-hypothesis evidence.";


  if (result ==
      SystemHypothesisEvaluationResult::Supported) {
    evidence.relation =
        SystemHypothesisEvidenceRelation::Supports;

    evaluation.supporting_evidence_count =
        1;
  } else if (result ==
             SystemHypothesisEvaluationResult::Refuted) {
    evidence.relation =
        SystemHypothesisEvidenceRelation::Refutes;

    evaluation.refuting_evidence_count =
        1;
  } else {
    evidence.relation =
        SystemHypothesisEvidenceRelation::Context;

    evaluation.context_evidence_count =
        1;
  }


  evaluation.evidence = {
      evidence,
  };

  evaluation.rationale =
      "Explicit evaluation revision.";

  return evaluation;
}


routing::core::intelligence::RemediationProposalRequest
make_request(
    const routing::core::intelligence::
        SystemHypothesisEvaluationRecord& evaluation,
    const std::string& suffix) {
  using namespace routing::core::intelligence;

  RemediationProposalRequest request;

  request.proposal_id =
      "proposal:" +
      suffix;

  request.proposer_ref =
      "proposer:" +
      suffix;

  request.source_evaluation_id =
      evaluation.evaluation_id;

  request.source_evaluation_revision =
      evaluation.evaluation_revision;

  request.remediation_key =
      "remediation:" +
      suffix;

  request.rationale =
      "Explicit remediation proposal for later approval.";

  return request;
}

}  // namespace


int main() {
  using namespace routing::core::intelligence;


  // -------------------------------------------------------------
  // BACKEND -> TYPED PROPOSAL
  // -------------------------------------------------------------

  const auto backend_mapping =
      make_mapping(
          SystemHypothesisKind::Backend,
          "backend");

  const auto backend_evaluation =
      make_evaluation(
          backend_mapping,
          1,
          SystemHypothesisEvaluationResult::Supported,
          "evaluation:backend:1");

  const std::vector<SystemHypothesisEvaluationRecord>
      backend_history = {
          backend_evaluation,
      };


  RemediationProposalWorkflow workflow;

  const auto backend_result =
      workflow.apply(
          backend_mapping,
          backend_history,
          make_request(
              backend_evaluation,
              "backend"));

  assert(
      backend_result.record.
          backend_remediation_proposal.has_value());

  const BackendRemediationProposal& backend =
      *backend_result.record.
          backend_remediation_proposal;

  assert(
      backend.backend_component_key ==
      "backend:backend");

  assert(
      backend.base.explicit_approval_required);

  assert(
      !backend.base.backend_change_allowed);

  assert(
      !backend.base.production_application_allowed);


  // -------------------------------------------------------------
  // CANDIDATE PIPELINE -> TYPED PROPOSAL
  // -------------------------------------------------------------

  const auto pipeline_mapping =
      make_mapping(
          SystemHypothesisKind::CandidatePipeline,
          "pipeline");

  const auto pipeline_evaluation =
      make_evaluation(
          pipeline_mapping,
          1,
          SystemHypothesisEvaluationResult::Supported,
          "evaluation:pipeline:1");

  const std::vector<SystemHypothesisEvaluationRecord>
      pipeline_history = {
          pipeline_evaluation,
      };


  const auto pipeline_result =
      workflow.apply(
          pipeline_mapping,
          pipeline_history,
          make_request(
              pipeline_evaluation,
              "pipeline"));

  assert(
      pipeline_result.record.
          candidate_pipeline_remediation_proposal.has_value());

  const CandidatePipelineRemediationProposal& pipeline =
      *pipeline_result.record.
          candidate_pipeline_remediation_proposal;

  assert(
      pipeline.pipeline_stage_key ==
      "candidate-pipeline:pipeline");

  assert(
      !pipeline.base.candidate_pipeline_change_allowed);

  assert(
      !pipeline.base.routing_change_allowed);

  assert(
      !pipeline.base.cost_engine_change_allowed);


  // -------------------------------------------------------------
  // OLD SUPPORTED REVISION MAY NOT OVERRIDE A NEWER REFUTATION
  // -------------------------------------------------------------

  const auto stale_mapping =
      make_mapping(
          SystemHypothesisKind::DataSource,
          "stale");

  const auto stale_supported =
      make_evaluation(
          stale_mapping,
          1,
          SystemHypothesisEvaluationResult::Supported,
          "evaluation:stale:1");

  const auto stale_refuted =
      make_evaluation(
          stale_mapping,
          2,
          SystemHypothesisEvaluationResult::Refuted,
          "evaluation:stale:2");

  const std::vector<SystemHypothesisEvaluationRecord>
      stale_history = {
          stale_supported,
          stale_refuted,
      };


  bool old_supported_rejected =
      false;

  try {
    (void)workflow.apply(
        stale_mapping,
        stale_history,
        make_request(
            stale_supported,
            "stale-old"));
  } catch (const std::logic_error&) {
    old_supported_rejected =
        true;
  }

  assert(
      old_supported_rejected);


  bool latest_refuted_rejected =
      false;

  try {
    (void)workflow.apply(
        stale_mapping,
        stale_history,
        make_request(
            stale_refuted,
            "stale-latest"));
  } catch (const std::logic_error&) {
    latest_refuted_rejected =
        true;
  }

  assert(
      latest_refuted_rejected);


  // -------------------------------------------------------------
  // INCONCLUSIVE LATEST MAY NOT CREATE A PROPOSAL
  // -------------------------------------------------------------

  const auto inconclusive_mapping =
      make_mapping(
          SystemHypothesisKind::DataSource,
          "inconclusive");

  const auto inconclusive_evaluation =
      make_evaluation(
          inconclusive_mapping,
          1,
          SystemHypothesisEvaluationResult::Inconclusive,
          "evaluation:inconclusive:1");

  const std::vector<SystemHypothesisEvaluationRecord>
      inconclusive_history = {
          inconclusive_evaluation,
      };


  bool inconclusive_rejected =
      false;

  try {
    (void)workflow.apply(
        inconclusive_mapping,
        inconclusive_history,
        make_request(
            inconclusive_evaluation,
            "inconclusive"));
  } catch (const std::logic_error&) {
    inconclusive_rejected =
        true;
  }

  assert(
      inconclusive_rejected);


  // -------------------------------------------------------------
  // HISTORY GAPS ARE NOT ACCEPTED
  // -------------------------------------------------------------

  const auto gap_mapping =
      make_mapping(
          SystemHypothesisKind::Backend,
          "gap");

  const auto gap_one =
      make_evaluation(
          gap_mapping,
          1,
          SystemHypothesisEvaluationResult::Supported,
          "evaluation:gap:1");

  const auto gap_three =
      make_evaluation(
          gap_mapping,
          3,
          SystemHypothesisEvaluationResult::Supported,
          "evaluation:gap:3");

  const std::vector<SystemHypothesisEvaluationRecord>
      gap_history = {
          gap_one,
          gap_three,
      };


  bool gap_rejected =
      false;

  try {
    (void)workflow.apply(
        gap_mapping,
        gap_history,
        make_request(
            gap_three,
            "gap"));
  } catch (const std::logic_error&) {
    gap_rejected =
        true;
  }

  assert(
      gap_rejected);


  // -------------------------------------------------------------
  // UNSAFE EVALUATION CANNOT CROSS THE BOUNDARY
  // -------------------------------------------------------------

  const auto unsafe_mapping =
      make_mapping(
          SystemHypothesisKind::Backend,
          "unsafe");

  auto unsafe_evaluation =
      make_evaluation(
          unsafe_mapping,
          1,
          SystemHypothesisEvaluationResult::Supported,
          "evaluation:unsafe:1");

  unsafe_evaluation.routing_change_allowed =
      true;

  const std::vector<SystemHypothesisEvaluationRecord>
      unsafe_history = {
          unsafe_evaluation,
      };


  bool unsafe_rejected =
      false;

  try {
    (void)workflow.apply(
        unsafe_mapping,
        unsafe_history,
        make_request(
            unsafe_evaluation,
            "unsafe"));
  } catch (const std::logic_error&) {
    unsafe_rejected =
        true;
  }

  assert(
      unsafe_rejected);


  const std::string report =
      format_remediation_proposal_report(
          pipeline_result.record);

  assert(
      report.find(
          "CANDIDATE PIPELINE REMEDIATION PROPOSAL") !=
      std::string::npos);

  assert(
      report.find(
          "shadow validation required: yes") !=
      std::string::npos);

  assert(
      report.find(
          "candidate pipeline change allowed: no") !=
      std::string::npos);

  assert(
      report.find(
          "CostEngine change allowed: no") !=
      std::string::npos);


  std::cout
      << "Remediation proposal boundary tests passed\n";

  return 0;
}
