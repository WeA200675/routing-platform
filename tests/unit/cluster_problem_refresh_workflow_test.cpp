#include <cassert>
#include <iostream>
#include <string>

#include "routing/core/diagnostics/anomaly_tracker.hpp"
#include "routing/core/diagnostics/investigation_candidate.hpp"
#include "routing/core/intelligence/cluster_problem_analysis.hpp"
#include "routing/core/intelligence/cluster_problem_review.hpp"
#include "routing/core/intelligence/diagnostic_investigation_job.hpp"

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
      "regression:refresh-test";

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
  // Revision 2 becomes eligible for investigation.
  // -------------------------------------------------------------

  AnomalyTracker tracker;

  tracker.ingest(
      make_record(
          "record:1",
          "observation:1",
          "route-a"));

  tracker.ingest(
      make_record(
          "record:2",
          "observation:2",
          "route-b"));

  auto candidates =
      build_investigation_candidates(
          tracker.clusters());

  assert(
      candidates.size() == 1);

  const auto candidate_v2 =
      candidates.front();

  assert(
      candidate_v2.observation_count == 2);


  IntelligenceJobQueue queue;

  const auto initial =
      ensure_cluster_problem_job(
          queue,
          candidate_v2);

  assert(
      initial.status ==
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


  const auto claimed_v2 =
      queue.claim_next(
          resources);

  assert(
      claimed_v2.has_value());

  assert(
      claimed_v2->evidence_revision == 2);

  assert(
      claimed_v2->attempts == 1);


  // -------------------------------------------------------------
  // A third independent observation arrives while v2 is running.
  // -------------------------------------------------------------

  tracker.ingest(
      make_record(
          "record:3",
          "observation:3",
          "route-c"));

  candidates =
      build_investigation_candidates(
          tracker.clusters());

  assert(
      candidates.size() == 1);

  const auto candidate_v3 =
      candidates.front();

  assert(
      candidate_v3.observation_count == 3);


  const auto* cluster_v3 =
      tracker.find(
          candidate_v3.cluster_key);

  assert(cluster_v3 != nullptr);


  const auto stale =
      analyze_cluster_problem(
          *claimed_v2,
          candidate_v3,
          *cluster_v3);

  assert(
      stale.status ==
      ClusterProblemAnalysisStatus::
          StaleEvidence);

  assert(
      stale.evidence_revision == 2);

  assert(
      stale.observed_cluster_revision == 3);


  // Worker explicitly finishes its old revision.
  queue.mark_completed(
      claimed_v2->id);


  // Normal automatic producer path still may NOT reopen terminal work.
  const auto automatic =
      ensure_cluster_problem_job(
          queue,
          candidate_v3);

  assert(
      automatic.status ==
      DiagnosticInvestigationJobStatus::
          ExistingTerminal);

  const auto* terminal =
      queue.find(
          claimed_v2->id);

  assert(terminal != nullptr);

  assert(
      terminal->state ==
      IntelligenceJobState::Completed);

  assert(
      terminal->evidence_revision == 2);


  // -------------------------------------------------------------
  // Explicit review requests re-analysis of the newer revision.
  // -------------------------------------------------------------

  ClusterProblemReviewWorkflow workflow;

  ClusterProblemReviewRequest refresh;

  refresh.review_id =
      "review:refresh:v3";

  refresh.reviewer_ref =
      "tester:alpha";

  refresh.decision =
      ClusterProblemReviewDecision::
          RefreshAnalysis;

  refresh.rationale =
      "Revision 3 contains new independent evidence.";

  const auto refreshed =
      workflow.apply(
          tracker,
          queue,
          stale,
          refresh);

  assert(
      refreshed.status ==
      ClusterProblemReviewApplyStatus::Applied);

  assert(
      refreshed.record.refresh_job_requested);

  assert(
      refreshed.record.refresh_job_id ==
      claimed_v2->id);

  assert(
      refreshed.record.refresh_from_revision == 2);

  assert(
      refreshed.record.refresh_to_revision == 3);

  assert(
      refreshed.record.resulting_state ==
      InvestigationState::Investigating);


  const auto* pending_v3 =
      queue.find(
          claimed_v2->id);

  assert(pending_v3 != nullptr);

  assert(
      pending_v3->state ==
      IntelligenceJobState::Pending);

  assert(
      pending_v3->evidence_revision == 3);

  // Attempt history is preserved.
  assert(
      pending_v3->attempts == 1);


  // -------------------------------------------------------------
  // Revision 3 is separately claimed/analyzed.
  // -------------------------------------------------------------

  const auto claimed_v3 =
      queue.claim_next(
          resources);

  assert(
      claimed_v3.has_value());

  assert(
      claimed_v3->id ==
      claimed_v2->id);

  assert(
      claimed_v3->evidence_revision == 3);

  assert(
      claimed_v3->attempts == 2);


  const auto current_candidates =
      build_investigation_candidates(
          tracker.clusters());

  assert(
      current_candidates.size() == 1);

  const auto* current_cluster =
      tracker.find(
          current_candidates.front().cluster_key);

  assert(current_cluster != nullptr);


  const auto current_analysis =
      analyze_cluster_problem(
          *claimed_v3,
          current_candidates.front(),
          *current_cluster);

  assert(
      current_analysis.status ==
      ClusterProblemAnalysisStatus::Completed);

  assert(
      current_analysis.evidence_revision == 3);

  assert(
      current_analysis.observed_cluster_revision == 3);

  queue.mark_completed(
      claimed_v3->id);


  // Same review request is idempotent and must not reopen again.
  const auto duplicate_refresh =
      workflow.apply(
          tracker,
          queue,
          stale,
          refresh);

  assert(
      duplicate_refresh.status ==
      ClusterProblemReviewApplyStatus::
          DuplicateIgnored);

  const auto* completed_v3 =
      queue.find(
          claimed_v3->id);

  assert(completed_v3 != nullptr);

  assert(
      completed_v3->state ==
      IntelligenceJobState::Completed);

  assert(
      completed_v3->evidence_revision == 3);


  std::cout
      << "Cluster problem refresh workflow tests passed\n";

  return 0;
}
