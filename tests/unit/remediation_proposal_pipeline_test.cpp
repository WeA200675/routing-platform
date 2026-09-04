#include <cassert>
#include <iostream>
#include <string>

#include "routing/core/diagnostics/anomaly_tracker.hpp"
#include "routing/core/diagnostics/investigation_candidate.hpp"
#include "routing/core/intelligence/cluster_problem_analysis.hpp"
#include "routing/core/intelligence/cluster_problem_review.hpp"
#include "routing/core/intelligence/diagnostic_investigation_job.hpp"
#include "routing/core/intelligence/proposal_approval.hpp"
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
      "regression:remediation-proposal-pipeline";

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
      "review:remediation-pipeline";

  review_request.reviewer_ref =
      "reviewer:alpha";

  review_request.decision =
      ClusterProblemReviewDecision::Acknowledge;

  review_request.rationale =
      "Acknowledge system issue for explicit downstream review.";


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
      "outcome:remediation-pipeline";

  outcome_request.reviewer_ref =
      "reviewer:beta";

  outcome_request.kind =
      ReviewedAnalysisOutcomeKind::
          HypothesisProposal;

  outcome_request.semantic_key =
      "hypothesis.data.urban_coverage_source_gap";

  outcome_request.rationale =
      "Propose a reviewable system explanation.";


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
      "approval:remediation-pipeline";

  approval_request.approver_ref =
      "approver:alpha";

  approval_request.decision =
      ProposalApprovalDecision::Approve;

  approval_request.rationale =
      "Approve explicit system semantic mapping only.";


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
      "mapping:remediation-pipeline";

  mapping_request.mapper_ref =
      "mapper:alpha";

  mapping_request.decision =
      SemanticHypothesisMappingDecision::Map;

  mapping_request.kind =
      SystemHypothesisKind::DataSource;

  mapping_request.target_key =
      "source:urban-coverage";

  mapping_request.rationale =
      "Explicitly classify as data-source hypothesis.";


  const auto mapping =
      mapping_workflow.apply(
          approval.record,
          mapping_request);

  assert(
      mapping.record.data_source_hypothesis.has_value());


  // -------------------------------------------------------------
  // SYSTEM HYPOTHESIS -> SUPPORTED EVALUATION
  // -------------------------------------------------------------

  SystemHypothesisEvaluationWorkflow evaluation_workflow;

  SystemHypothesisEvaluationRequest evaluation_request;

  evaluation_request.evaluation_id =
      "evaluation:remediation-pipeline:1";

  evaluation_request.evaluator_ref =
      "evaluator:alpha";

  evaluation_request.evaluation_revision =
      1;

  evaluation_request.result =
      SystemHypothesisEvaluationResult::Supported;


  SystemHypothesisEvidenceReference evidence;

  evidence.evidence_id =
      "evidence:remediation-pipeline:1";

  evidence.source_ref =
      "regression:urban-source-review";

  evidence.data_scope_key =
      "local-only";

  evidence.context_key =
      "li:vaduz-ruggell";

  evidence.relation =
      SystemHypothesisEvidenceRelation::Supports;

  evidence.detail =
      "Independent source review reproduces the data coverage problem.";

  evaluation_request.evidence = {
      evidence,
  };

  evaluation_request.rationale =
      "Explicit reviewed evidence supports the current hypothesis.";


  const auto evaluation =
      evaluation_workflow.apply(
          mapping.record,
          evaluation_request);

  assert(
      evaluation.record.result ==
      SystemHypothesisEvaluationResult::Supported);


  // -------------------------------------------------------------
  // LATEST SUPPORTED EVALUATION -> REMEDIATION PROPOSAL
  // -------------------------------------------------------------

  RemediationProposalWorkflow remediation_workflow;

  RemediationProposalRequest remediation_request;

  remediation_request.proposal_id =
      "proposal:remediation-pipeline";

  remediation_request.proposer_ref =
      "proposer:alpha";

  remediation_request.source_evaluation_id =
      evaluation.record.evaluation_id;

  remediation_request.source_evaluation_revision =
      evaluation.record.evaluation_revision;

  remediation_request.remediation_key =
      "remediation.data.review-urban-source-import";

  remediation_request.rationale =
      "Propose a source-data remediation for explicit later approval.";


  const auto remediation =
      remediation_workflow.apply(
          mapping.record,
          evaluation_workflow.records(),
          remediation_request);

  assert(
      remediation.status ==
      RemediationProposalApplyStatus::Created);

  assert(
      remediation.record.
          data_remediation_proposal.has_value());


  const DataRemediationProposal& proposal =
      *remediation.record.
          data_remediation_proposal;

  assert(
      proposal.data_source_key ==
      "source:urban-coverage");

  assert(
      proposal.base.explicit_approval_required);

  assert(
      proposal.base.shadow_validation_required);

  assert(
      !proposal.base.approval_record_created);

  assert(
      !proposal.base.shadow_validation_created);

  assert(
      !proposal.base.implementation_task_created);

  assert(
      !proposal.base.automatic_apply_allowed);

  assert(
      !proposal.base.data_write_allowed);

  assert(
      !proposal.base.backend_change_allowed);

  assert(
      !proposal.base.candidate_pipeline_change_allowed);

  assert(
      !proposal.base.map_change_allowed);

  assert(
      !proposal.base.routing_change_allowed);

  assert(
      !proposal.base.cost_engine_change_allowed);

  assert(
      !proposal.base.production_application_allowed);

  assert(
      !proposal.base.evidence_scope_promotion_allowed);


  // No extra intelligence job is created.
  assert(
      queue.size() == 1);

  const auto* completed_job =
      queue.find(
          claimed->id);

  assert(completed_job != nullptr);

  assert(
      completed_job->state ==
      IntelligenceJobState::Completed);


  // No lifecycle mutation happens here.
  const auto* proposal_cluster =
      tracker.find(
          candidate.cluster_key);

  assert(proposal_cluster != nullptr);

  assert(
      proposal_cluster->state ==
      InvestigationState::Investigating);


  std::cout
      << "Remediation proposal pipeline tests passed\n";

  return 0;
}
