#include <algorithm>
#include <cassert>
#include <iostream>
#include <stdexcept>
#include <string>

#include "routing/core/intelligence/cluster_problem_analysis.hpp"
#include "routing/core/intelligence/diagnostic_investigation_job.hpp"

namespace {

struct Fixture {
  routing::core::diagnostics::InvestigationCandidate
      candidate;

  routing::core::diagnostics::AnomalyCluster
      cluster;

  routing::core::intelligence::IntelligenceJob
      job;
};


Fixture make_fixture(
    const routing::core::diagnostics::
        DiagnosticCategory category,
    const std::string& code,
    const std::size_t observations = 2) {
  using namespace routing::core::diagnostics;
  using namespace routing::core::intelligence;

  Fixture fixture;

  fixture.candidate.cluster_key =
      "diagnostic-cluster:test:" +
      code;

  fixture.candidate.evidence_scope =
      DiagnosticEvidenceScope::LocalOnly;

  fixture.candidate.context_key =
      "context:test";

  fixture.candidate.diagnostic_code =
      code;

  fixture.candidate.severity =
      DiagnosticSeverity::Warning;

  fixture.candidate.state =
      InvestigationState::Observed;

  fixture.candidate.occurrence_count =
      observations + 3;

  fixture.candidate.observation_count =
      observations;

  fixture.candidate.reason_key =
      "diagnostic.investigation."
      "repeated_warning_observation";


  fixture.cluster.cluster_key =
      fixture.candidate.cluster_key;

  fixture.cluster.evidence_scope =
      fixture.candidate.evidence_scope;

  fixture.cluster.context_key =
      fixture.candidate.context_key;

  fixture.cluster.diagnostic_code =
      fixture.candidate.diagnostic_code;

  fixture.cluster.category =
      category;

  fixture.cluster.max_severity =
      fixture.candidate.severity;

  fixture.cluster.state =
      fixture.candidate.state;

  fixture.cluster.occurrence_count =
      fixture.candidate.occurrence_count;

  for (std::size_t index = 0;
       index < observations;
       ++index) {
    fixture.cluster.observation_ids.push_back(
        "observation:" +
        std::to_string(index + 1));
  }


  fixture.job =
      make_cluster_problem_job(
          fixture.candidate);

  fixture.job.state =
      IntelligenceJobState::Running;

  return fixture;
}


bool has_action(
    const routing::core::intelligence::
        ClusterProblemAnalysisResult& result,
    const routing::core::intelligence::
        ClusterProblemNextAction action) {
  return std::find(
             result.next_actions.begin(),
             result.next_actions.end(),
             action) !=
      result.next_actions.end();
}

}  // namespace


int main() {
  using namespace routing::core::diagnostics;
  using namespace routing::core::intelligence;


  // -------------------------------------------------------------
  // Backend/enrichment domain.
  // -------------------------------------------------------------

  auto backend =
      make_fixture(
          DiagnosticCategory::Enrichment,
          "ROUTE_SEGMENT_ENRICHMENT_FAILED");

  const auto backend_result =
      analyze_cluster_problem(
          backend.job,
          backend.candidate,
          backend.cluster);

  assert(
      backend_result.domain ==
      ClusterProblemDomain::BackendReliability);

  assert(
      has_action(
          backend_result,
          ClusterProblemNextAction::
              ReviewBackendOrEnrichment));


  // -------------------------------------------------------------
  // Candidate pipeline domain.
  // -------------------------------------------------------------

  auto candidate_set =
      make_fixture(
          DiagnosticCategory::CandidateSet,
          "CANDIDATE_SET_DEGRADED_ROUTES");

  const auto candidate_result =
      analyze_cluster_problem(
          candidate_set.job,
          candidate_set.candidate,
          candidate_set.cluster);

  assert(
      candidate_result.domain ==
      ClusterProblemDomain::CandidatePipeline);

  assert(
      has_action(
          candidate_result,
          ClusterProblemNextAction::
              ReviewCandidatePipeline));


  // -------------------------------------------------------------
  // Evidence revision may not silently advance underneath old work.
  // -------------------------------------------------------------

  auto stale =
      make_fixture(
          DiagnosticCategory::DataCoverage,
          "DATA_COVERAGE_URBAN_LOW",
          2);

  // Job represents revision 2.
  assert(
      stale.job.evidence_revision == 2);

  // A third independent observation arrives while the job is running.
  stale.candidate.observation_count =
      3;

  stale.candidate.occurrence_count =
      9;

  stale.cluster.observation_ids.push_back(
      "observation:3");

  stale.cluster.occurrence_count =
      9;

  const auto stale_result =
      analyze_cluster_problem(
          stale.job,
          stale.candidate,
          stale.cluster);

  assert(
      stale_result.status ==
      ClusterProblemAnalysisStatus::
          StaleEvidence);

  assert(
      stale_result.evidence_revision == 2);

  assert(
      stale_result.observed_cluster_revision == 3);

  assert(
      has_action(
          stale_result,
          ClusterProblemNextAction::
              RefreshAnalysis));

  // Old analysis must not pretend to permit production behavior.
  assert(
      !stale_result.production_application_allowed);

  assert(
      !stale_result.preference_hypothesis_created);


  // -------------------------------------------------------------
  // Workflow advancement wins over background work.
  // -------------------------------------------------------------

  auto resolved =
      make_fixture(
          DiagnosticCategory::DataSignal,
          "DATA_URBAN_POSITIVE_SIGNAL_ABSENT",
          3);

  resolved.cluster.state =
      InvestigationState::Resolved;

  const auto resolved_result =
      analyze_cluster_problem(
          resolved.job,
          resolved.candidate,
          resolved.cluster);

  assert(
      resolved_result.status ==
      ClusterProblemAnalysisStatus::
          SupersededByWorkflowState);

  assert(
      resolved_result.next_actions.empty());


  // -------------------------------------------------------------
  // Analysis requires an actually claimed/running job.
  // -------------------------------------------------------------

  auto not_running =
      make_fixture(
          DiagnosticCategory::Backend,
          "BACKEND_TEST");

  not_running.job.state =
      IntelligenceJobState::Pending;

  bool pending_rejected =
      false;

  try {
    (void)analyze_cluster_problem(
        not_running.job,
        not_running.candidate,
        not_running.cluster);
  } catch (const std::invalid_argument&) {
    pending_rejected =
        true;
  }

  assert(
      pending_rejected);


  std::cout
      << "Cluster problem analysis revision tests passed\n";

  return 0;
}
