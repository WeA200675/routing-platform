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
      "regression:approval-pipeline";

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
  // CLUSTER -> INVESTIGATION
  // -------------------------------------------------------------

  const auto candidates =
      build_investigation_candidates(
          tracker.clusters());

  assert(
      candidates.size() == 1);

  const auto candidate =
      candidates.front();


  // -------------------------------------------------------------
  // INVESTIGATION -> JOB
  // -------------------------------------------------------------

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
      "review:approval-pipeline";

  review_request.reviewer_ref =
      "reviewer:alpha";

  review_request.decision =
      ClusterProblemReviewDecision::Acknowledge;

  review_request.rationale =
      "Acknowledge the data-quality issue for review.";


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
  // REVIEW -> REVIEWED OUTCOME
  // -------------------------------------------------------------

  ReviewedAnalysisOutcomeWorkflow outcome_workflow;

  ReviewedAnalysisOutcomeRequest outcome_request;

  outcome_request.outcome_id =
      "outcome:approval-pipeline";

  outcome_request.reviewer_ref =
      "reviewer:beta";

  outcome_request.kind =
      ReviewedAnalysisOutcomeKind::
          DataReviewCandidate;

  outcome_request.rationale =
      "Create a source-data review proposal.";


  const auto outcome =
      outcome_workflow.apply(
          analysis,
          review.record,
          outcome_request);

  assert(
      outcome.record.data_review_candidate.has_value());


  // -------------------------------------------------------------
  // REVIEWED OUTCOME -> EXPLICIT APPROVAL -> MANUAL TASK
  // -------------------------------------------------------------

  ProposalApprovalWorkflow approval_workflow;

  ProposalApprovalRequest approval_request;

  approval_request.approval_id =
      "approval:approval-pipeline";

  approval_request.approver_ref =
      "approver:alpha";

  approval_request.decision =
      ProposalApprovalDecision::Approve;

  approval_request.rationale =
      "Authorize manual data review only.";


  const auto approval =
      approval_workflow.apply(
          outcome.record,
          approval_request);

  assert(
      approval.status ==
      ProposalApprovalApplyStatus::Created);

  assert(
      approval.record.data_review_task.has_value());


  const DataReviewTask& task =
      *approval.record.data_review_task;

  assert(
      task.manual_review_authorized);

  assert(
      task.data_scope_key ==
      "local-only");

  assert(
      task.evidence_revision == 2);

  assert(
      !task.map_change_allowed);

  assert(
      !task.routing_change_allowed);

  assert(
      !task.automatic_publish_allowed);

  assert(
      !task.evidence_scope_promotion_allowed);


  // Approval layer does not create more IntelligenceJobs.
  assert(
      queue.size() == 1);

  const auto* completed_job =
      queue.find(
          claimed->id);

  assert(completed_job != nullptr);

  assert(
      completed_job->state ==
      IntelligenceJobState::Completed);


  // Approval layer does not change investigation state.
  const auto* reviewed_cluster =
      tracker.find(
          candidate.cluster_key);

  assert(reviewed_cluster != nullptr);

  assert(
      reviewed_cluster->state ==
      InvestigationState::Investigating);


  std::cout
      << "Proposal approval pipeline tests passed\n";

  return 0;
}
