#include <cassert>
#include <iostream>
#include <string>

#include "routing/core/diagnostics/anomaly_tracker.hpp"
#include "routing/core/diagnostics/investigation_candidate.hpp"
#include "routing/core/intelligence/cluster_problem_analysis.hpp"
#include "routing/core/intelligence/cluster_problem_review.hpp"
#include "routing/core/intelligence/diagnostic_investigation_job.hpp"
#include "routing/core/intelligence/proposal_approval.hpp"
#include "routing/core/intelligence/remediation_approval_shadow_validation.hpp"
#include "routing/core/intelligence/remediation_proposal.hpp"
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
      "regression:remediation-shadow-pipeline";

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
  // DIAGNOSTIC EVIDENCE -> CLUSTER
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
      "review:shadow-pipeline";

  review_request.reviewer_ref =
      "reviewer:alpha";

  review_request.decision =
      ClusterProblemReviewDecision::Acknowledge;

  review_request.rationale =
      "Acknowledge issue for system hypothesis workflow.";


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
      "outcome:shadow-pipeline";

  outcome_request.reviewer_ref =
      "reviewer:beta";

  outcome_request.kind =
      ReviewedAnalysisOutcomeKind::
          HypothesisProposal;

  outcome_request.semantic_key =
      "hypothesis.data.urban_coverage_source_gap";

  outcome_request.rationale =
      "Propose an explicitly reviewed system explanation.";


  const auto outcome =
      outcome_workflow.apply(
          analysis,
          review.record,
          outcome_request);

  assert(
      outcome.record.hypothesis_proposal.has_value());


  // -------------------------------------------------------------
  // HYPOTHESIS PROPOSAL -> APPROVED CONVERSION CANDIDATE
  // -------------------------------------------------------------

  ProposalApprovalWorkflow proposal_approval_workflow;

  ProposalApprovalRequest proposal_approval_request;

  proposal_approval_request.approval_id =
      "proposal-approval:shadow-pipeline";

  proposal_approval_request.approver_ref =
      "approver:semantic";

  proposal_approval_request.decision =
      ProposalApprovalDecision::Approve;

  proposal_approval_request.rationale =
      "Approve system semantic mapping only.";


  const auto proposal_approval =
      proposal_approval_workflow.apply(
          outcome.record,
          proposal_approval_request);

  assert(
      proposal_approval.record.
          hypothesis_conversion_candidate.has_value());


  // -------------------------------------------------------------
  // CONVERSION -> SYSTEM HYPOTHESIS
  // -------------------------------------------------------------

  SemanticHypothesisMappingWorkflow mapping_workflow;

  SemanticHypothesisMappingRequest mapping_request;

  mapping_request.mapping_id =
      "mapping:shadow-pipeline";

  mapping_request.mapper_ref =
      "mapper:alpha";

  mapping_request.decision =
      SemanticHypothesisMappingDecision::Map;

  mapping_request.kind =
      SystemHypothesisKind::DataSource;

  mapping_request.target_key =
      "source:urban-coverage";

  mapping_request.rationale =
      "Explicitly map to data-source system hypothesis.";


  const auto mapping =
      mapping_workflow.apply(
          proposal_approval.record,
          mapping_request);

  assert(
      mapping.record.data_source_hypothesis.has_value());


  // -------------------------------------------------------------
  // SYSTEM HYPOTHESIS -> SUPPORTED EVALUATION
  // -------------------------------------------------------------

  SystemHypothesisEvaluationWorkflow evaluation_workflow;

  SystemHypothesisEvaluationRequest evaluation_request;

  evaluation_request.evaluation_id =
      "evaluation:shadow-pipeline:1";

  evaluation_request.evaluator_ref =
      "evaluator:alpha";

  evaluation_request.evaluation_revision =
      1;

  evaluation_request.result =
      SystemHypothesisEvaluationResult::Supported;


  SystemHypothesisEvidenceReference evaluation_evidence;

  evaluation_evidence.evidence_id =
      "evaluation-evidence:shadow-pipeline";

  evaluation_evidence.source_ref =
      "regression:source-review";

  evaluation_evidence.data_scope_key =
      "local-only";

  evaluation_evidence.context_key =
      "li:vaduz-ruggell";

  evaluation_evidence.relation =
      SystemHypothesisEvidenceRelation::Supports;

  evaluation_evidence.detail =
      "Independent source review reproduces the issue.";

  evaluation_request.evidence = {
      evaluation_evidence,
  };

  evaluation_request.rationale =
      "Reviewed evidence supports the current system hypothesis.";


  const auto evaluation =
      evaluation_workflow.apply(
          mapping.record,
          evaluation_request);

  assert(
      evaluation.record.result ==
      SystemHypothesisEvaluationResult::Supported);


  // -------------------------------------------------------------
  // SUPPORTED EVALUATION -> REMEDIATION PROPOSAL
  // -------------------------------------------------------------

  RemediationProposalWorkflow remediation_workflow;

  RemediationProposalRequest remediation_request;

  remediation_request.proposal_id =
      "remediation-proposal:shadow-pipeline";

  remediation_request.proposer_ref =
      "proposer:alpha";

  remediation_request.source_evaluation_id =
      evaluation.record.evaluation_id;

  remediation_request.source_evaluation_revision =
      evaluation.record.evaluation_revision;

  remediation_request.remediation_key =
      "remediation.data.review-urban-source-import";

  remediation_request.rationale =
      "Propose isolated source-data remediation validation.";


  const auto remediation =
      remediation_workflow.apply(
          mapping.record,
          evaluation_workflow.records(),
          remediation_request);

  assert(
      remediation.record.
          data_remediation_proposal.has_value());


  // -------------------------------------------------------------
  // REMEDIATION PROPOSAL -> EXPLICIT APPROVAL
  // -------------------------------------------------------------

  RemediationApprovalWorkflow remediation_approval_workflow;

  RemediationApprovalRequest remediation_approval_request;

  remediation_approval_request.approval_id =
      "remediation-approval:shadow-pipeline";

  remediation_approval_request.approver_ref =
      "approver:remediation";

  remediation_approval_request.decision =
      RemediationApprovalDecision::Approve;

  remediation_approval_request.rationale =
      "Approve isolated shadow validation, not implementation.";


  const auto remediation_approval =
      remediation_approval_workflow.apply(
          remediation.record,
          evaluation_workflow.records(),
          remediation_approval_request);

  assert(
      remediation_approval.record.
          shadow_validation_candidate.has_value());


  const RemediationShadowValidationCandidate& shadow_candidate =
      *remediation_approval.record.
          shadow_validation_candidate;

  assert(
      shadow_candidate.isolated_validation_only);

  assert(
      !shadow_candidate.production_traffic_allowed);

  assert(
      !shadow_candidate.production_application_allowed);


  // -------------------------------------------------------------
  // APPROVED REMEDIATION -> ISOLATED SHADOW VALIDATION
  // -------------------------------------------------------------

  RemediationShadowValidationWorkflow shadow_workflow;

  RemediationShadowValidationRequest shadow_request;

  shadow_request.validation_id =
      "shadow-validation:shadow-pipeline:1";

  shadow_request.validator_ref =
      "validator:alpha";

  shadow_request.validation_revision =
      1;

  shadow_request.environment =
      RemediationValidationEnvironment::RegressionFixture;

  shadow_request.result =
      RemediationShadowValidationResult::Passed;


  RemediationShadowEvidenceReference shadow_evidence;

  shadow_evidence.evidence_id =
      "shadow-evidence:shadow-pipeline";

  shadow_evidence.source_ref =
      "regression:isolated-source-remediation";

  shadow_evidence.data_scope_key =
      "local-only";

  shadow_evidence.context_key =
      "li:vaduz-ruggell";

  shadow_evidence.relation =
      RemediationShadowEvidenceRelation::Supports;

  shadow_evidence.detail =
      "Isolated fixture supports the remediation direction.";

  shadow_request.evidence = {
      shadow_evidence,
  };

  shadow_request.rationale =
      "Isolated validation passes without production application.";


  const auto shadow =
      shadow_workflow.apply(
          remediation_approval.record,
          evaluation_workflow.records(),
          shadow_request);

  assert(
      shadow.status ==
      RemediationShadowValidationApplyStatus::Created);

  assert(
      shadow.record.result ==
      RemediationShadowValidationResult::Passed);

  assert(
      shadow.record.supporting_evidence_count == 1);

  assert(
      shadow.record.regression_evidence_count == 0);

  assert(
      !shadow.record.implementation_candidate_created);

  assert(
      !shadow.record.implementation_task_created);

  assert(
      !shadow.record.deployment_candidate_created);

  assert(
      !shadow.record.automatic_apply_allowed);

  assert(
      !shadow.record.data_write_allowed);

  assert(
      !shadow.record.backend_change_allowed);

  assert(
      !shadow.record.candidate_pipeline_change_allowed);

  assert(
      !shadow.record.map_change_allowed);

  assert(
      !shadow.record.routing_change_allowed);

  assert(
      !shadow.record.cost_engine_change_allowed);

  assert(
      !shadow.record.preference_hypothesis_created);

  assert(
      !shadow.record.learning_gate_invoked);

  assert(
      !shadow.record.production_application_allowed);

  assert(
      !shadow.record.evidence_scope_promotion_allowed);


  // No extra IntelligenceJob has been created.
  assert(
      queue.size() == 1);

  const auto* completed_job =
      queue.find(
          claimed->id);

  assert(completed_job != nullptr);

  assert(
      completed_job->state ==
      IntelligenceJobState::Completed);


  // Investigation lifecycle remains untouched.
  const auto* final_cluster =
      tracker.find(
          candidate.cluster_key);

  assert(final_cluster != nullptr);

  assert(
      final_cluster->state ==
      InvestigationState::Investigating);


  std::cout
      << "Remediation approval/shadow validation pipeline tests passed\n";

  return 0;
}
