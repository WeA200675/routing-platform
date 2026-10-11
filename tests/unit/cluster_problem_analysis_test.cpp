#include <algorithm>
#include <cassert>
#include <iostream>
#include <string>

#include "routing/core/intelligence/cluster_problem_analysis.hpp"
#include "routing/core/intelligence/cluster_problem_analysis_report.hpp"
#include "routing/core/intelligence/diagnostic_investigation_job.hpp"

namespace {

bool has_finding(
    const routing::core::intelligence::
        ClusterProblemAnalysisResult& result,
    const std::string& code) {
  return std::any_of(
      result.findings.begin(),
      result.findings.end(),
      [&](const auto& finding) {
        return finding.code ==
            code;
      });
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

  InvestigationCandidate candidate;

  candidate.cluster_key =
      "diagnostic-cluster:test:data-quality";

  candidate.evidence_scope =
      DiagnosticEvidenceScope::LocalOnly;

  candidate.context_key =
      "li:vaduz-ruggell";

  candidate.diagnostic_code =
      "DATA_URBAN_POSITIVE_SIGNAL_ABSENT";

  candidate.severity =
      DiagnosticSeverity::Info;

  candidate.state =
      InvestigationState::Observed;

  candidate.occurrence_count =
      8;

  candidate.observation_count =
      3;

  candidate.source_count =
      3;

  candidate.affected_route_count =
      4;

  candidate.affected_family_count =
      2;

  candidate.reason_key =
      "diagnostic.investigation."
      "repeated_info_observation";


  AnomalyCluster cluster;

  cluster.cluster_key =
      candidate.cluster_key;

  cluster.evidence_scope =
      candidate.evidence_scope;

  cluster.context_key =
      candidate.context_key;

  cluster.diagnostic_code =
      candidate.diagnostic_code;

  cluster.category =
      DiagnosticCategory::DataSignal;

  cluster.diagnostic_scope =
      DiagnosticScope::Route;

  cluster.max_severity =
      candidate.severity;

  cluster.state =
      candidate.state;

  cluster.occurrence_count =
      candidate.occurrence_count;

  cluster.observation_ids = {
      "observation:1",
      "observation:2",
      "observation:3",
  };

  cluster.source_refs = {
      "scenario:1",
      "regression:1",
      "route-lab:1",
  };

  cluster.affected_route_ids = {
      "route-a",
      "route-b",
      "route-c",
      "route-d",
  };

  cluster.affected_families = {
      routing::core::CandidateFamily::Fastest,
      routing::core::CandidateFamily::MajorRoads,
  };


  IntelligenceJob job =
      make_cluster_problem_job(
          candidate);

  // Analysis is worker execution, therefore the job must have
  // been claimed first.
  job.state =
      IntelligenceJobState::Running;

  const auto result =
      analyze_cluster_problem(
          job,
          candidate,
          cluster);


  assert(
      result.schema_version ==
      kClusterProblemAnalysisSchemaVersion);

  assert(
      result.status ==
      ClusterProblemAnalysisStatus::Completed);

  assert(
      result.domain ==
      ClusterProblemDomain::DataQuality);

  assert(
      result.evidence_revision == 3);

  assert(
      result.observed_cluster_revision == 3);

  assert(
      result.analysis_id.find(
          "cluster-analysis-v1|") == 0);

  assert(
      has_finding(
          result,
          "ANALYSIS_EVIDENCE_SUMMARY"));

  assert(
      has_finding(
          result,
          "ANALYSIS_DATA_QUALITY_REVIEW"));

  assert(
      has_finding(
          result,
          "ANALYSIS_PREFERENCE_HYPOTHESIS_BOUNDARY"));

  assert(
      has_action(
          result,
          ClusterProblemNextAction::
              ReviewSourceData));


  // Hard boundary: diagnostics do not become preference learning.
  assert(
      !result.preference_hypothesis_created);

  assert(
      !result.learning_gate_invoked);

  assert(
      !result.question_candidate_created);

  assert(
      !result.production_application_allowed);

  assert(
      !result.evidence_scope_promotion_allowed);


  const auto report =
      format_cluster_problem_analysis_report(
          result);

  assert(
      report.find(
          "CLUSTER PROBLEM ANALYSIS") !=
      std::string::npos);

  assert(
      report.find(
          "domain: data-quality") !=
      std::string::npos);

  assert(
      report.find(
          "preference hypothesis created: no") !=
      std::string::npos);

  assert(
      report.find(
          "production application allowed: no") !=
      std::string::npos);

  std::cout
      << "Cluster problem analysis tests passed\n";

  return 0;
}
