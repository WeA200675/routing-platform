#pragma once

#include <cstdint>
#include <string>
#include <string_view>
#include <vector>

#include "routing/core/diagnostics/anomaly_tracker.hpp"
#include "routing/core/diagnostics/investigation_candidate.hpp"
#include "routing/core/intelligence/intelligence_job_queue.hpp"

namespace routing::core::intelligence {

inline constexpr std::uint32_t
kClusterProblemAnalysisSchemaVersion = 1;


// A ClusterProblem analysis is an observational review result.
//
// It is deliberately not:
//   - a PreferenceHypothesis,
//   - a learning decision,
//   - a QuestionCandidate,
//   - a routing rule,
//   - a CostEngine contribution,
//   - permission for production application.
enum class ClusterProblemAnalysisStatus : std::uint8_t {
  Completed = 0,

  // New evidence arrived after the job's evidence revision.
  // Do not pretend the old analysis covers the new revision.
  StaleEvidence,

  // Workflow state advanced while work was running, e.g. a human
  // already resolved/dismissed the cluster.
  SupersededByWorkflowState,
};


enum class ClusterProblemDomain : std::uint8_t {
  DataQuality = 0,
  BackendReliability,
  CandidatePipeline,
  Unknown,
};


enum class ClusterProblemNextAction : std::uint8_t {
  ReviewSourceData = 0,
  ReviewBackendOrEnrichment,
  ReviewCandidatePipeline,

  // Advisory only. This does not create a QuestionCandidate.
  CollectAdditionalContext,

  // Analysis was based on an older evidence revision.
  RefreshAnalysis,
};


[[nodiscard]]
constexpr std::string_view
cluster_problem_analysis_status_key(
    const ClusterProblemAnalysisStatus status) {
  switch (status) {
    case ClusterProblemAnalysisStatus::Completed:
      return "completed";

    case ClusterProblemAnalysisStatus::StaleEvidence:
      return "stale-evidence";

    case ClusterProblemAnalysisStatus::
        SupersededByWorkflowState:
      return "superseded-by-workflow-state";
  }

  return "unknown";
}


[[nodiscard]]
constexpr std::string_view
cluster_problem_domain_key(
    const ClusterProblemDomain domain) {
  switch (domain) {
    case ClusterProblemDomain::DataQuality:
      return "data-quality";

    case ClusterProblemDomain::BackendReliability:
      return "backend-reliability";

    case ClusterProblemDomain::CandidatePipeline:
      return "candidate-pipeline";

    case ClusterProblemDomain::Unknown:
      return "unknown";
  }

  return "unknown";
}


[[nodiscard]]
constexpr std::string_view
cluster_problem_next_action_key(
    const ClusterProblemNextAction action) {
  switch (action) {
    case ClusterProblemNextAction::ReviewSourceData:
      return "review-source-data";

    case ClusterProblemNextAction::
        ReviewBackendOrEnrichment:
      return "review-backend-or-enrichment";

    case ClusterProblemNextAction::
        ReviewCandidatePipeline:
      return "review-candidate-pipeline";

    case ClusterProblemNextAction::
        CollectAdditionalContext:
      return "collect-additional-context";

    case ClusterProblemNextAction::RefreshAnalysis:
      return "refresh-analysis";
  }

  return "unknown";
}


struct ClusterProblemFinding {
  // Stable machine-readable analysis finding.
  std::string code;

  // Stable explanation/localisation key.
  std::string explanation_key;

  // Factual explanation only.
  //
  // Must not claim preference, causality or data corruption that the
  // evidence does not establish.
  std::string detail;

  std::vector<diagnostics::DiagnosticEvidence>
      evidence;
};


struct ClusterProblemAnalysisResult {
  std::uint32_t schema_version =
      kClusterProblemAnalysisSchemaVersion;

  // Stable for one cluster/evidence revision.
  std::string analysis_id;

  std::string job_id;
  std::string cluster_key;
  std::string context_key;
  std::string data_scope_key;
  std::string diagnostic_code;

  // Evidence revision the claimed job represents.
  std::uint64_t evidence_revision = 0;

  // Current cluster revision observed by the analysis worker.
  std::uint64_t observed_cluster_revision = 0;

  ClusterProblemAnalysisStatus status =
      ClusterProblemAnalysisStatus::Completed;

  ClusterProblemDomain domain =
      ClusterProblemDomain::Unknown;

  diagnostics::DiagnosticSeverity severity =
      diagnostics::DiagnosticSeverity::Info;

  std::vector<ClusterProblemFinding>
      findings;

  std::vector<ClusterProblemNextAction>
      next_actions;

  // Explicit safety boundary.
  //
  // These remain false for this result type.
  bool preference_hypothesis_created = false;
  bool learning_gate_invoked = false;
  bool question_candidate_created = false;
  bool production_application_allowed = false;
  bool evidence_scope_promotion_allowed = false;
};


// Deterministic analysis of one claimed ClusterProblem job.
//
// Inputs must refer to the same semantic cluster.
//
// If evidence advanced since the job was scheduled, the result becomes
// StaleEvidence instead of silently analysing a newer revision under an
// older job revision.
//
// This function never mutates job, candidate or cluster.
[[nodiscard]]
ClusterProblemAnalysisResult
analyze_cluster_problem(
    const IntelligenceJob& claimed_job,
    const diagnostics::InvestigationCandidate& candidate,
    const diagnostics::AnomalyCluster& cluster);

}  // namespace routing::core::intelligence
