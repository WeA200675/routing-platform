#pragma once

#include <cstddef>
#include <cstdint>
#include <string>
#include <vector>

#include "routing/core/diagnostics/investigation_candidate.hpp"
#include "routing/core/intelligence/intelligence_job_queue.hpp"

namespace routing::core::intelligence {

enum class DiagnosticInvestigationJobStatus : std::uint8_t {
  Added = 0,
  Coalesced,
  ExistingRunning,
  ExistingTerminal,

  // HypothesisProposed/Resolved/Dismissed are never silently
  // pushed back into problem clustering.
  SkippedAdvancedState,
};

struct DiagnosticInvestigationJobResult {
  DiagnosticInvestigationJobStatus status =
      DiagnosticInvestigationJobStatus::Added;

  std::string job_id;
};

struct DiagnosticInvestigationBatchResult {
  std::size_t added_count = 0;
  std::size_t coalesced_count = 0;

  std::size_t existing_running_count = 0;
  std::size_t existing_terminal_count = 0;

  std::size_t skipped_advanced_state_count = 0;

  std::vector<std::string>
      job_ids;
};


// Stable deterministic ID.
//
// Same anomaly cluster -> same ClusterProblem job identity.
[[nodiscard]]
std::string cluster_problem_job_id(
    const diagnostics::InvestigationCandidate&
        candidate);


// Operational priority only.
//
// This does NOT influence route scoring, anomaly severity,
// regression pass/fail or hypothesis confidence.
[[nodiscard]]
std::uint8_t cluster_problem_job_priority(
    const diagnostics::InvestigationCandidate&
        candidate);


// Builds local-first analysis work.
//
// It does not:
//   - create a hypothesis,
//   - change investigation state,
//   - alter routing,
//   - alter CostEngine,
//   - alter rules or map data,
//   - promote evidence privacy/share scope.
[[nodiscard]]
IntelligenceJob make_cluster_problem_job(
    const diagnostics::InvestigationCandidate&
        candidate);


// Idempotent bridge.
//
// Pending/Deferred jobs may coalesce.
// Running/terminal jobs are never silently mutated/reopened.
[[nodiscard]]
DiagnosticInvestigationJobResult
ensure_cluster_problem_job(
    IntelligenceJobQueue& queue,
    const diagnostics::InvestigationCandidate&
        candidate);


[[nodiscard]]
DiagnosticInvestigationBatchResult
enqueue_investigation_candidates(
    IntelligenceJobQueue& queue,
    const std::vector<
        diagnostics::InvestigationCandidate>&
        candidates);

}  // namespace routing::core::intelligence
