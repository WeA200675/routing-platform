#include <cassert>
#include <iostream>
#include <stdexcept>
#include <string>

#include "routing/core/intelligence/system_hypothesis_evaluation.hpp"
#include "routing/core/intelligence/system_hypothesis_evaluation_report.hpp"

namespace {

routing::core::intelligence::SemanticHypothesisMappingRecord
make_mapping(
    const routing::core::intelligence::SystemHypothesisKind kind,
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


  if (kind ==
      SystemHypothesisKind::Backend) {
    mapping.target_key =
        "backend:route-enrichment";

    BackendHypothesis hypothesis;

    hypothesis.base =
        base;

    hypothesis.backend_component_key =
        mapping.target_key;

    mapping.backend_hypothesis =
        hypothesis;
  } else if (kind ==
             SystemHypothesisKind::CandidatePipeline) {
    mapping.target_key =
        "candidate-pipeline:representative-selection";

    CandidatePipelineHypothesis hypothesis;

    hypothesis.base =
        base;

    hypothesis.pipeline_stage_key =
        mapping.target_key;

    mapping.candidate_pipeline_hypothesis =
        hypothesis;
  } else {
    mapping.target_key =
        "source:data";

    DataSourceHypothesis hypothesis;

    hypothesis.base =
        base;

    hypothesis.data_source_key =
        mapping.target_key;

    mapping.data_source_hypothesis =
        hypothesis;
  }

  return mapping;
}


routing::core::intelligence::SystemHypothesisEvidenceReference
make_evidence(
    const std::string& id,
    const std::string& context,
    const routing::core::intelligence::
        SystemHypothesisEvidenceRelation relation) {
  using namespace routing::core::intelligence;

  SystemHypothesisEvidenceReference evidence;

  evidence.evidence_id =
      id;

  evidence.source_ref =
      "regression:evaluation-boundary";

  evidence.data_scope_key =
      "personal";

  evidence.context_key =
      context;

  evidence.relation =
      relation;

  evidence.detail =
      "Explicit reviewed evaluation evidence.";

  return evidence;
}

}  // namespace


int main() {
  using namespace routing::core::intelligence;

  SystemHypothesisEvaluationWorkflow workflow;


  // -------------------------------------------------------------
  // BACKEND -> REFUTED
  // -------------------------------------------------------------

  const auto backend_mapping =
      make_mapping(
          SystemHypothesisKind::Backend,
          "backend");

  SystemHypothesisEvaluationRequest refuted;

  refuted.evaluation_id =
      "evaluation:backend:1";

  refuted.evaluator_ref =
      "evaluator:alpha";

  refuted.evaluation_revision =
      1;

  refuted.result =
      SystemHypothesisEvaluationResult::Refuted;

  refuted.evidence = {
      make_evidence(
          "evidence:backend:1",
          "context:backend",
          SystemHypothesisEvidenceRelation::Refutes),
  };

  refuted.rationale =
      "Controlled backend replay does not reproduce the proposed failure.";


  const auto backend_result =
      workflow.apply(
          backend_mapping,
          refuted);

  assert(
      backend_result.record.result ==
      SystemHypothesisEvaluationResult::Refuted);

  assert(
      backend_result.record.refuting_evidence_count == 1);

  assert(
      !backend_result.record.remediation_proposal_created);

  assert(
      !backend_result.record.routing_change_allowed);


  // -------------------------------------------------------------
  // PIPELINE -> INCONCLUSIVE WITH MIXED EVIDENCE
  // -------------------------------------------------------------

  const auto pipeline_mapping =
      make_mapping(
          SystemHypothesisKind::CandidatePipeline,
          "pipeline");

  SystemHypothesisEvaluationRequest inconclusive;

  inconclusive.evaluation_id =
      "evaluation:pipeline:1";

  inconclusive.evaluator_ref =
      "evaluator:beta";

  inconclusive.evaluation_revision =
      1;

  inconclusive.result =
      SystemHypothesisEvaluationResult::Inconclusive;

  inconclusive.evidence = {
      make_evidence(
          "evidence:pipeline:support",
          "context:pipeline",
          SystemHypothesisEvidenceRelation::Supports),

      make_evidence(
          "evidence:pipeline:refute",
          "context:pipeline",
          SystemHypothesisEvidenceRelation::Refutes),
  };

  inconclusive.rationale =
      "Reviewed evidence is mixed.";


  const auto pipeline_result =
      workflow.apply(
          pipeline_mapping,
          inconclusive);

  assert(
      pipeline_result.record.result ==
      SystemHypothesisEvaluationResult::Inconclusive);

  assert(
      pipeline_result.record.supporting_evidence_count == 1);

  assert(
      pipeline_result.record.refuting_evidence_count == 1);

  assert(
      !pipeline_result.record.cost_engine_change_allowed);

  assert(
      !pipeline_result.record.production_application_allowed);


  // -------------------------------------------------------------
  // SUPPORTED MUST ACTUALLY REFERENCE SUPPORTING EVIDENCE
  // -------------------------------------------------------------

  const auto unsupported_mapping =
      make_mapping(
          SystemHypothesisKind::Backend,
          "no-support");

  SystemHypothesisEvaluationRequest invalid_supported;

  invalid_supported.evaluation_id =
      "evaluation:no-support:1";

  invalid_supported.evaluator_ref =
      "evaluator:gamma";

  invalid_supported.evaluation_revision =
      1;

  invalid_supported.result =
      SystemHypothesisEvaluationResult::Supported;

  invalid_supported.evidence = {
      make_evidence(
          "evidence:no-support:1",
          "context:no-support",
          SystemHypothesisEvidenceRelation::Context),
  };

  invalid_supported.rationale =
      "Must fail without supporting evidence.";


  bool supported_without_evidence_rejected =
      false;

  try {
    (void)workflow.apply(
        unsupported_mapping,
        invalid_supported);
  } catch (const std::logic_error&) {
    supported_without_evidence_rejected =
        true;
  }

  assert(
      supported_without_evidence_rejected);


  // -------------------------------------------------------------
  // SCOPE BOUNDARY
  // -------------------------------------------------------------

  const auto scope_mapping =
      make_mapping(
          SystemHypothesisKind::DataSource,
          "scope");

  SystemHypothesisEvaluationRequest scope_request;

  scope_request.evaluation_id =
      "evaluation:scope:1";

  scope_request.evaluator_ref =
      "evaluator:delta";

  scope_request.evaluation_revision =
      1;

  scope_request.result =
      SystemHypothesisEvaluationResult::Inconclusive;

  auto wrong_scope =
      make_evidence(
          "evidence:scope:1",
          "context:scope",
          SystemHypothesisEvidenceRelation::Context);

  wrong_scope.data_scope_key =
      "global-reference";

  scope_request.evidence = {
      wrong_scope,
  };

  scope_request.rationale =
      "Cross-scope evidence must not silently enter evaluation.";


  bool scope_rejected =
      false;

  try {
    (void)workflow.apply(
        scope_mapping,
        scope_request);
  } catch (const std::logic_error&) {
    scope_rejected =
        true;
  }

  assert(
      scope_rejected);


  // -------------------------------------------------------------
  // REJECTED MAPPING HAS NO HYPOTHESIS TO EVALUATE
  // -------------------------------------------------------------

  auto rejected_mapping =
      make_mapping(
          SystemHypothesisKind::Backend,
          "rejected");

  rejected_mapping.decision =
      SemanticHypothesisMappingDecision::Reject;

  rejected_mapping.target_key.clear();

  rejected_mapping.backend_hypothesis.reset();

  SystemHypothesisEvaluationRequest rejected_request;

  rejected_request.evaluation_id =
      "evaluation:rejected:1";

  rejected_request.evaluator_ref =
      "evaluator:epsilon";

  rejected_request.evaluation_revision =
      1;

  rejected_request.result =
      SystemHypothesisEvaluationResult::Inconclusive;

  rejected_request.evidence = {
      make_evidence(
          "evidence:rejected:1",
          "context:rejected",
          SystemHypothesisEvidenceRelation::Context),
  };

  rejected_request.rationale =
      "Rejected mapping must not evaluate.";


  bool rejected_mapping_rejected =
      false;

  try {
    (void)workflow.apply(
        rejected_mapping,
        rejected_request);
  } catch (const std::logic_error&) {
    rejected_mapping_rejected =
        true;
  }

  assert(
      rejected_mapping_rejected);


  const std::string report =
      format_system_hypothesis_evaluation_report(
          backend_result.record);

  assert(
      report.find(
          "result: refuted") !=
      std::string::npos);

  assert(
      report.find(
          "CostEngine change allowed: no") !=
      std::string::npos);

  assert(
      report.find(
          "evidence scope promotion allowed: no") !=
      std::string::npos);


  std::cout
      << "System hypothesis evaluation boundary tests passed\n";

  return 0;
}
