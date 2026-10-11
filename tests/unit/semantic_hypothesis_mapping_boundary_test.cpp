#include <cassert>
#include <iostream>
#include <stdexcept>
#include <string>

#include "routing/core/intelligence/semantic_hypothesis_mapping.hpp"
#include "routing/core/intelligence/semantic_hypothesis_mapping_report.hpp"

namespace {

routing::core::intelligence::ProposalApprovalRecord
make_approval(
    const std::string& suffix,
    const std::string& diagnostic_code =
        "BACKEND_ENRICHMENT_FAILED") {
  using namespace routing::core::intelligence;

  ProposalApprovalRecord approval;

  approval.approval_id =
      "approval:" +
      suffix;

  approval.source_outcome_id =
      "outcome:" +
      suffix;

  approval.source_review_id =
      "review:" +
      suffix;

  approval.source_analysis_id =
      "analysis:" +
      suffix;

  approval.outcome_kind =
      ReviewedAnalysisOutcomeKind::
          HypothesisProposal;

  approval.outcome_reviewer_ref =
      "reviewer:alpha";

  approval.approver_ref =
      "approver:alpha";

  approval.cluster_key =
      "cluster:" +
      suffix;

  approval.context_key =
      "context:" +
      suffix;

  approval.data_scope_key =
      "personal";

  approval.diagnostic_code =
      diagnostic_code;

  approval.evidence_revision =
      3;

  approval.decision =
      ProposalApprovalDecision::Approve;

  approval.rationale =
      "Approved for explicit system semantic mapping.";


  HypothesisConversionCandidate candidate;

  candidate.id =
      "hypothesis-conversion-v1|" +
      approval.source_outcome_id;

  candidate.source_outcome_id =
      approval.source_outcome_id;

  candidate.source_review_id =
      approval.source_review_id;

  candidate.source_analysis_id =
      approval.source_analysis_id;

  candidate.cluster_key =
      approval.cluster_key;

  candidate.context_key =
      approval.context_key;

  candidate.data_scope_key =
      approval.data_scope_key;

  candidate.diagnostic_code =
      approval.diagnostic_code;

  candidate.evidence_revision =
      approval.evidence_revision;

  candidate.hypothesis_key =
      "hypothesis:" +
      suffix;

  candidate.approver_ref =
      approval.approver_ref;

  candidate.rationale =
      approval.rationale;

  approval.hypothesis_conversion_candidate =
      candidate;

  return approval;
}

}  // namespace


int main() {
  using namespace routing::core::intelligence;

  SemanticHypothesisMappingWorkflow workflow;


  // -------------------------------------------------------------
  // BACKEND
  // -------------------------------------------------------------

  const auto backend_approval =
      make_approval(
          "backend");

  SemanticHypothesisMappingRequest backend_request;

  backend_request.mapping_id =
      "mapping:backend";

  backend_request.mapper_ref =
      "mapper:beta";

  backend_request.decision =
      SemanticHypothesisMappingDecision::Map;

  backend_request.kind =
      SystemHypothesisKind::Backend;

  backend_request.target_key =
      "backend:route-enrichment";

  backend_request.rationale =
      "Map explicitly to backend enrichment investigation.";


  const auto backend_result =
      workflow.apply(
          backend_approval,
          backend_request);

  assert(
      backend_result.record.backend_hypothesis.has_value());

  const BackendHypothesis& backend =
      *backend_result.record.backend_hypothesis;

  assert(
      backend.backend_component_key ==
      "backend:route-enrichment");

  assert(
      backend.base.data_scope_key ==
      "personal");

  assert(
      !backend.base.preference_interpretation_allowed);

  assert(
      !backend.base.production_application_allowed);


  // -------------------------------------------------------------
  // CANDIDATE PIPELINE
  // -------------------------------------------------------------

  const auto pipeline_approval =
      make_approval(
          "candidate-pipeline",
          "CANDIDATE_NO_USABLE_ENRICHED_ROUTE");

  SemanticHypothesisMappingRequest pipeline_request;

  pipeline_request.mapping_id =
      "mapping:candidate-pipeline";

  pipeline_request.mapper_ref =
      "mapper:gamma";

  pipeline_request.decision =
      SemanticHypothesisMappingDecision::Map;

  pipeline_request.kind =
      SystemHypothesisKind::CandidatePipeline;

  pipeline_request.target_key =
      "candidate-pipeline:enriched-representative-selection";

  pipeline_request.rationale =
      "Map explicitly to candidate-pipeline investigation.";


  const auto pipeline_result =
      workflow.apply(
          pipeline_approval,
          pipeline_request);

  assert(
      pipeline_result.record.
          candidate_pipeline_hypothesis.has_value());

  const CandidatePipelineHypothesis& pipeline =
      *pipeline_result.record.
          candidate_pipeline_hypothesis;

  assert(
      pipeline.pipeline_stage_key ==
      "candidate-pipeline:enriched-representative-selection");

  assert(
      !pipeline.base.preference_target_created);

  assert(
      !pipeline.base.preference_hypothesis_created);

  assert(
      !pipeline.base.learning_gate_invoked);

  assert(
      !pipeline.base.shadow_evaluation_created);

  assert(
      !pipeline.base.routing_change_allowed);


  // -------------------------------------------------------------
  // REJECT
  // -------------------------------------------------------------

  const auto reject_approval =
      make_approval(
          "reject");

  SemanticHypothesisMappingRequest reject;

  reject.mapping_id =
      "mapping:reject";

  reject.mapper_ref =
      "mapper:delta";

  reject.decision =
      SemanticHypothesisMappingDecision::Reject;

  reject.kind =
      SystemHypothesisKind::Backend;

  reject.rationale =
      "Approved proposal does not support a defensible system mapping.";


  const auto rejected =
      workflow.apply(
          reject_approval,
          reject);

  assert(
      rejected.record.decision ==
      SemanticHypothesisMappingDecision::Reject);

  assert(
      !rejected.record.data_source_hypothesis.has_value());

  assert(
      !rejected.record.backend_hypothesis.has_value());

  assert(
      !rejected.record.candidate_pipeline_hypothesis.has_value());


  // Reject may not smuggle in a target.
  auto invalid_reject_approval =
      make_approval(
          "invalid-reject");

  auto invalid_reject =
      reject;

  invalid_reject.mapping_id =
      "mapping:invalid-reject";

  invalid_reject.target_key =
      "backend:should-not-exist";

  bool invalid_reject_rejected =
      false;

  try {
    (void)workflow.apply(
        invalid_reject_approval,
        invalid_reject);
  } catch (const std::invalid_argument&) {
    invalid_reject_rejected =
        true;
  }

  assert(
      invalid_reject_rejected);


  // -------------------------------------------------------------
  // UNSAFE SOURCE CANDIDATE
  // -------------------------------------------------------------

  auto unsafe_approval =
      make_approval(
          "unsafe");

  unsafe_approval.hypothesis_conversion_candidate->
      preference_target_created =
          true;

  SemanticHypothesisMappingRequest unsafe_request;

  unsafe_request.mapping_id =
      "mapping:unsafe";

  unsafe_request.mapper_ref =
      "mapper:epsilon";

  unsafe_request.decision =
      SemanticHypothesisMappingDecision::Map;

  unsafe_request.kind =
      SystemHypothesisKind::DataSource;

  unsafe_request.target_key =
      "source:unsafe";

  unsafe_request.rationale =
      "Unsafe source candidate must not map.";


  bool unsafe_rejected =
      false;

  try {
    (void)workflow.apply(
        unsafe_approval,
        unsafe_request);
  } catch (const std::logic_error&) {
    unsafe_rejected =
        true;
  }

  assert(
      unsafe_rejected);


  const std::string report =
      format_semantic_hypothesis_mapping_report(
          pipeline_result.record);

  assert(
      report.find(
          "CANDIDATE PIPELINE HYPOTHESIS") !=
      std::string::npos);

  assert(
      report.find(
          "PreferenceHypothesis created: no") !=
      std::string::npos);

  assert(
      report.find(
          "LearningGate invoked: no") !=
      std::string::npos);

  assert(
      report.find(
          "routing change allowed: no") !=
      std::string::npos);


  std::cout
      << "Semantic hypothesis mapping boundary tests passed\n";

  return 0;
}
