#include <cassert>
#include <iostream>
#include <string>

#include "routing/core/diagnostics/anomaly_tracker.hpp"
#include "routing/core/diagnostics/investigation_candidate.hpp"
#include "routing/core/intelligence/cluster_problem_analysis.hpp"
#include "routing/core/intelligence/cluster_problem_review.hpp"
#include "routing/core/intelligence/diagnostic_investigation_job.hpp"
#include "routing/core/intelligence/proposal_approval.hpp"
#include "routing/core/intelligence/reviewed_analysis_outcome.hpp"
#include "routing/core/intelligence/semantic_hypothesis_mapping.hpp"
#include "routing/core/intelligence/system_hypothesis_evaluation.hpp"

namespace {

routing::core::diagnostics::DiagnosticEvidenceRecord
make_record(
    const std::string& record_id,
    const std::string& observation_id,
    const std::string& route_id) {
  using namespace routing::core;
  using namespace routing::core::diagnostics;

  DiagnosticEvidenceRecord record;

  record.record_id =
      record_id;

  record.source =
      DiagnosticEvidenceSource::RegressionCase;

  record.evidence_scope =
      DiagnosticEvidenceScope::LocalOnly;

  record.observation_id =
      observation_id;

  record.source_ref =
      "regression:system-hypothesis-evaluation";

  record.context_key =
      "li:vaduz-ruggell";

  record.version_ref =
      "fixture:v1";

  record.diagnostic.code =
      "DATA_COVERAGE_URBAN_LOW";

  record.diagnostic.severity =
      DiagnosticSeverity::Warning;

  record.diagnostic.category =
      DiagnosticCategory::DataCoverage;

  record.diagnostic.scope =
      DiagnosticScope::Route;

  record.diagnostic.family =
      CandidateFamily::ProfileOptimal;

  record.diagnostic.route_id =
      route_id;

  record.diagnostic.explanation_key =
      "diagnostic.data.coverage.urban_low";

  return record;
}

}  // namespace


int main() {
  using namespace routing::core::diagnostics;
  using namespace routing::core::intelligence;


  // -------------------------------------------------------------
  // EVIDENCE -> CLUSTER
  // -------------------------------------------------------------

  AnomalyTracker tracker;

  (void)tracker.ingest(
      make_record(
          "record:1",
          "observation:1",
          "route-a"));

  (void)tracker.ingest(
      make_record(
          "record:2",
          "observation:2",
          "route-b"));

  assert(
      tracker.size() == 1);


  // -------------------------------------------------------------
  // CLUSTER -> INVESTIGATION -> JOB
  // -------------------------------------------------------------

  const auto candidates =
      build_investigation_candidates(
          tracker.clusters());

  assert(
      candidates.size() == 1);

  const auto candidate =
      candidates.front();


  IntelligenceJobQueue queue;

  const auto bridge =
      ensure_cluster_problem_job(
          queue,
          candidate);

  assert(
      bridge.status ==
      DiagnosticInvestigationJobStatus::Added);


  ResourceSnapshot resources;

  resources.navigation_active =
      false;

  resources.device_charging =
      false;

  resources.network_available =
      false;

  resources.battery_percent =
      80;

  resources.thermal_state =
      ThermalState::Nominal;


  const auto claimed =
      queue.claim_next(
          resources);

  assert(
      claimed.has_value());


  // -------------------------------------------------------------
  // JOB -> ANALYSIS
  // -------------------------------------------------------------

  const auto* cluster =
      tracker.find(
          candidate.cluster_key);

  assert(cluster != nullptr);


  const auto analysis =
      analyze_cluster_problem(
          *claimed,
          candidate,
          *cluster);

  assert(
      analysis.status ==
      ClusterProblemAnalysisStatus::Completed);

  assert(
      analysis.domain ==
      ClusterProblemDomain::DataQuality);


  queue.mark_completed(
      claimed->id);


  // -------------------------------------------------------------
  // ANALYSIS -> REVIEW
  // -------------------------------------------------------------

  ClusterProblemReviewWorkflow review_workflow;

  ClusterProblemReviewRequest review_request;

  review_request.review_id =
      "review:evaluation-pipeline";

  review_request.reviewer_ref =
      "reviewer:alpha";

  review_request.decision =
      ClusterProblemReviewDecision::Acknowledge;

  review_request.rationale =
      "Acknowledge the system issue for hypothesis review.";


  const auto review =
      review_workflow.apply(
          tracker,
          queue,
          analysis,
          review_request);

  assert(
      review.record.resulting_state ==
      InvestigationState::Investigating);


  // -------------------------------------------------------------
  // REVIEW -> HYPOTHESIS PROPOSAL
  // -------------------------------------------------------------

  ReviewedAnalysisOutcomeWorkflow outcome_workflow;

  ReviewedAnalysisOutcomeRequest outcome_request;

  outcome_request.outcome_id =
      "outcome:evaluation-pipeline";

  outcome_request.reviewer_ref =
      "reviewer:beta";

  outcome_request.kind =
      ReviewedAnalysisOutcomeKind::
          HypothesisProposal;

  outcome_request.semantic_key =
      "hypothesis.data.urban_coverage_source_gap";

  outcome_request.rationale =
      "Propose an explicitly reviewable system explanation.";


  const auto outcome =
      outcome_workflow.apply(
          analysis,
          review.record,
          outcome_request);

  assert(
      outcome.record.hypothesis_proposal.has_value());


  // -------------------------------------------------------------
  // PROPOSAL -> APPROVED CONVERSION CANDIDATE
  // -------------------------------------------------------------

  ProposalApprovalWorkflow approval_workflow;

  ProposalApprovalRequest approval_request;

  approval_request.approval_id =
      "approval:evaluation-pipeline";

  approval_request.approver_ref =
      "approver:alpha";

  approval_request.decision =
      ProposalApprovalDecision::Approve;

  approval_request.rationale =
      "Approve explicit system mapping only.";


  const auto approval =
      approval_workflow.apply(
          outcome.record,
          approval_request);

  assert(
      approval.record.
          hypothesis_conversion_candidate.has_value());


  // -------------------------------------------------------------
  // CONVERSION CANDIDATE -> SYSTEM HYPOTHESIS
  // -------------------------------------------------------------

  SemanticHypothesisMappingWorkflow mapping_workflow;

  SemanticHypothesisMappingRequest mapping_request;

  mapping_request.mapping_id =
      "mapping:evaluation-pipeline";

  mapping_request.mapper_ref =
      "mapper:alpha";

  mapping_request.decision =
      SemanticHypothesisMappingDecision::Map;

  mapping_request.kind =
      SystemHypothesisKind::DataSource;

  mapping_request.target_key =
      "source:urban-coverage";

  mapping_request.rationale =
      "Explicitly classify as a data-source hypothesis.";


  const auto mapping =
      mapping_workflow.apply(
          approval.record,
          mapping_request);

  assert(
      mapping.record.data_source_hypothesis.has_value());


  // -------------------------------------------------------------
  // SYSTEM HYPOTHESIS -> EXPLICIT EVALUATION
  // -------------------------------------------------------------

  SystemHypothesisEvaluationWorkflow evaluation_workflow;

  SystemHypothesisEvaluationRequest evaluation_request;

  evaluation_request.evaluation_id =
      "evaluation:evaluation-pipeline:1";

  evaluation_request.evaluator_ref =
      "evaluator:alpha";

  evaluation_request.evaluation_revision =
      1;

  evaluation_request.result =
      SystemHypothesisEvaluationResult::Supported;


  SystemHypothesisEvidenceReference evidence;

  evidence.evidence_id =
      "evidence:evaluation-pipeline:1";

  evidence.source_ref =
      "regression:urban-coverage-source-review";

  evidence.data_scope_key =
      "local-only";

  evidence.context_key =
      "li:vaduz-ruggell";

  evidence.relation =
      SystemHypothesisEvidenceRelation::Supports;

  evidence.detail =
      "Independent source review reproduces the urban coverage gap.";

  evaluation_request.evidence = {
      evidence,
  };

  evaluation_request.rationale =
      "Reviewed independent evidence currently supports the system hypothesis.";


  const auto evaluation =
      evaluation_workflow.apply(
          mapping.record,
          evaluation_request);

  assert(
      evaluation.status ==
      SystemHypothesisEvaluationApplyStatus::Created);

  assert(
      evaluation.record.result ==
      SystemHypothesisEvaluationResult::Supported);

  assert(
      evaluation.record.evaluation_revision == 1);

  assert(
      evaluation.record.supporting_evidence_count == 1);

  assert(
      evaluation.record.data_scope_key ==
      "local-only");

  assert(
      evaluation.record.hypothesis_evidence_revision == 2);

  assert(
      !evaluation.record.remediation_proposal_created);

  assert(
      !evaluation.record.preference_interpretation_allowed);

  assert(
      !evaluation.record.preference_hypothesis_created);

  assert(
      !evaluation.record.learning_gate_invoked);

  assert(
      !evaluation.record.shadow_evaluation_created);

  assert(
      !evaluation.record.automatic_fix_allowed);

  assert(
      !evaluation.record.map_change_allowed);

  assert(
      !evaluation.record.routing_change_allowed);

  assert(
      !evaluation.record.cost_engine_change_allowed);

  assert(
      !evaluation.record.production_application_allowed);

  assert(
      !evaluation.record.evidence_scope_promotion_allowed);


  // Evaluation does not create or mutate intelligence jobs.
  assert(
      queue.size() == 1);

  const auto* completed_job =
      queue.find(
          claimed->id);

  assert(completed_job != nullptr);

  assert(
      completed_job->state ==
      IntelligenceJobState::Completed);


  // Evaluation does not advance the investigation lifecycle.
  const auto* evaluated_cluster =
      tracker.find(
          candidate.cluster_key);

  assert(evaluated_cluster != nullptr);

  assert(
      evaluated_cluster->state ==
      InvestigationState::Investigating);


  std::cout
      << "System hypothesis evaluation pipeline tests passed\n";

  return 0;
}
