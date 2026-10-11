#include <algorithm>
#include <cassert>
#include <iostream>
#include <string>
#include <vector>

#include "routing/core/diagnostics/anomaly_tracker.hpp"
#include "routing/core/diagnostics/investigation_candidate.hpp"
#include "routing/core/intelligence/cluster_problem_analysis.hpp"
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
      "regression:urban-data-watch";

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
  // Evidence -> cluster.
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

  assert(
      tracker.size() == 1);


  // -------------------------------------------------------------
  // Cluster -> investigation candidate.
  // -------------------------------------------------------------

  const auto candidates =
      build_investigation_candidates(
          tracker.clusters());

  assert(
      candidates.size() == 1);

  const auto& candidate =
      candidates.front();

  assert(
      candidate.observation_count == 2);


  // -------------------------------------------------------------
  // Investigation candidate -> ClusterProblem job.
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


  // -------------------------------------------------------------
  // ResourceGovernor protects active navigation.
  // -------------------------------------------------------------

  ResourceSnapshot resources;

  resources.navigation_active =
      true;

  resources.device_charging =
      false;

  resources.network_available =
      false;

  resources.battery_percent =
      80;

  resources.thermal_state =
      ThermalState::Nominal;

  const auto during_navigation =
      queue.claim_next(
          resources);

  assert(
      !during_navigation.has_value());

  const auto* deferred =
      queue.find(
          bridge.job_id);

  assert(
      deferred != nullptr);

  assert(
      deferred->state ==
      IntelligenceJobState::Deferred);


  // -------------------------------------------------------------
  // Post-drive local analysis may run offline.
  // -------------------------------------------------------------

  resources.navigation_active =
      false;

  const auto claimed =
      queue.claim_next(
          resources);

  assert(
      claimed.has_value());

  assert(
      claimed->type ==
      IntelligenceJobType::ClusterProblem);

  assert(
      claimed->state ==
      IntelligenceJobState::Running);

  assert(
      !claimed->requires_network);


  const auto* cluster =
      tracker.find(
          candidate.cluster_key);

  assert(
      cluster != nullptr);


  // -------------------------------------------------------------
  // Claimed work -> versioned analysis result.
  // -------------------------------------------------------------

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

  assert(
      analysis.evidence_revision == 2);

  assert(
      analysis.observed_cluster_revision == 2);

  assert(
      analysis.analysis_id.find(
          "|revision=2") !=
      std::string::npos);


  // The complete pipeline still ends at analysis.
  assert(
      !analysis.preference_hypothesis_created);

  assert(
      !analysis.learning_gate_invoked);

  assert(
      !analysis.question_candidate_created);

  assert(
      !analysis.production_application_allowed);

  assert(
      !analysis.evidence_scope_promotion_allowed);


  // Analysis completion is an explicit queue lifecycle action.
  queue.mark_completed(
      claimed->id);

  const auto* completed =
      queue.find(
          claimed->id);

  assert(
      completed != nullptr);

  assert(
      completed->state ==
      IntelligenceJobState::Completed);


  std::cout
      << "Cluster problem analysis pipeline tests passed\n";

  return 0;
}
