#include <cassert>
#include <iostream>
#include <string>

#include "routing/core/diagnostics/anomaly_tracker.hpp"
#include "routing/core/diagnostics/investigation_candidate.hpp"
#include "routing/core/intelligence/cluster_problem_analysis.hpp"
#include "routing/core/intelligence/cluster_problem_review.hpp"
#include "routing/core/intelligence/diagnostic_investigation_job.hpp"
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
      "regression:reviewed-outcome";

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
  // CLUSTER -> INVESTIGATION CANDIDATE
  // -------------------------------------------------------------

  const auto candidates =
      build_investigation_candidates(
          tracker.clusters());

  assert(
      candidates.size() == 1);

  const auto candidate =
      candidates.front();

  assert(
      candidate.observation_count == 2);


  // -------------------------------------------------------------
  // INVESTIGATION -> CLUSTER PROBLEM JOB
  // -------------------------------------------------------------

  IntelligenceJobQueue queue;

  const auto bridge =
      ensure_cluster_problem_job(
          queue,
          candidate);

  assert(
      bridge.status ==
      DiagnosticInvestigationJobStatus::Added);

  assert(
      queue.size() == 1);


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

  assert(
      claimed->type ==
      IntelligenceJobType::ClusterProblem);


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
  // ANALYSIS -> EXPLICIT REVIEW
  // -------------------------------------------------------------

  ClusterProblemReviewWorkflow review_workflow;

  ClusterProblemReviewRequest review_request;

  review_request.review_id =
      "review:pipeline:1";

  review_request.reviewer_ref =
      "tester:alpha";

  review_request.decision =
      ClusterProblemReviewDecision::Acknowledge;

  review_request.rationale =
      "Repeated data coverage problem accepted for investigation.";


  const auto review_result =
      review_workflow.apply(
          tracker,
          queue,
          analysis,
          review_request);

  assert(
      review_result.status ==
      ClusterProblemReviewApplyStatus::Applied);

  assert(
      review_result.record.resulting_state ==
      InvestigationState::Investigating);


  // -------------------------------------------------------------
  // REVIEW -> EXPLICIT DATA REVIEW CANDIDATE
  // -------------------------------------------------------------

  ReviewedAnalysisOutcomeWorkflow outcome_workflow;

  ReviewedAnalysisOutcomeRequest outcome_request;

  outcome_request.outcome_id =
      "outcome:pipeline:data-review:1";

  outcome_request.reviewer_ref =
      "tester:alpha";

  outcome_request.kind =
      ReviewedAnalysisOutcomeKind::
          DataReviewCandidate;

  outcome_request.rationale =
      "Inspect source data before any map or routing change.";


  const auto outcome =
      outcome_workflow.apply(
          analysis,
          review_result.record,
          outcome_request);

  assert(
      outcome.status ==
      ReviewedAnalysisOutcomeApplyStatus::Created);

  assert(
      outcome.record.data_review_candidate.has_value());


  const auto& data_review =
      *outcome.record.data_review_candidate;

  assert(
      data_review.data_scope_key ==
      "local-only");

  assert(
      data_review.evidence_revision == 2);

  assert(
      !data_review.map_change_allowed);

  assert(
      !data_review.routing_change_allowed);

  assert(
      !data_review.automatic_publish_allowed);

  assert(
      !data_review.evidence_scope_promotion_allowed);


  // Outcome creation does not create or modify an intelligence job.
  assert(
      queue.size() == 1);

  const auto* completed_job =
      queue.find(
          claimed->id);

  assert(completed_job != nullptr);

  assert(
      completed_job->state ==
      IntelligenceJobState::Completed);


  // Outcome creation does not advance the coarse investigation state.
  const auto* reviewed_cluster =
      tracker.find(
          candidate.cluster_key);

  assert(reviewed_cluster != nullptr);

  assert(
      reviewed_cluster->state ==
      InvestigationState::Investigating);


  std::cout
      << "Reviewed analysis outcome pipeline tests passed\n";

  return 0;
}
