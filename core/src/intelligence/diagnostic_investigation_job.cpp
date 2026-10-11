#include "routing/core/intelligence/diagnostic_investigation_job.hpp"

#include <algorithm>
#include <stdexcept>
#include <utility>

namespace routing::core::intelligence {

namespace {

void validate_candidate(
    const diagnostics::InvestigationCandidate&
        candidate) {
  if (candidate.cluster_key.empty()) {
    throw std::invalid_argument(
        "Investigation candidate requires cluster_key.");
  }

  if (candidate.context_key.empty()) {
    throw std::invalid_argument(
        "Investigation candidate requires context_key.");
  }

  if (candidate.diagnostic_code.empty()) {
    throw std::invalid_argument(
        "Investigation candidate requires diagnostic_code.");
  }

  if (candidate.observation_count == 0) {
    throw std::invalid_argument(
        "Investigation candidate requires at least one observation.");
  }
}

bool advanced_state(
    const diagnostics::InvestigationState state) {
  return
      state ==
          diagnostics::InvestigationState::
              HypothesisProposed ||
      state ==
          diagnostics::InvestigationState::
              Resolved ||
      state ==
          diagnostics::InvestigationState::
              Dismissed;
}

std::uint8_t severity_base_priority(
    const diagnostics::DiagnosticSeverity severity) {
  switch (severity) {
    case diagnostics::DiagnosticSeverity::Info:
      return 40;

    case diagnostics::DiagnosticSeverity::Warning:
      return 65;

    case diagnostics::DiagnosticSeverity::Error:
      return 85;
  }

  return 40;
}

}  // namespace


std::string cluster_problem_job_id(
    const diagnostics::InvestigationCandidate&
        candidate) {
  validate_candidate(
      candidate);

  return
      std::string(
          "cluster-problem-v1|") +
      candidate.cluster_key;
}


std::uint8_t cluster_problem_job_priority(
    const diagnostics::InvestigationCandidate&
        candidate) {
  validate_candidate(
      candidate);

  unsigned int priority =
      severity_base_priority(
          candidate.severity);

  // Distinct observations may raise operational scheduling priority.
  //
  // occurrence_count is deliberately ignored so that several route
  // alternatives from one observation cannot inflate AI priority.
  const std::size_t extra_observations =
      candidate.observation_count > 0
          ? candidate.observation_count - 1
          : 0;

  priority +=
      static_cast<unsigned int>(
          std::min<std::size_t>(
              10,
              extra_observations * 2));

  if (candidate.state ==
      diagnostics::InvestigationState::
          Investigating) {
    priority += 5;
  }

  return static_cast<std::uint8_t>(
      std::min<unsigned int>(
          100,
          priority));
}


IntelligenceJob make_cluster_problem_job(
    const diagnostics::InvestigationCandidate&
        candidate) {
  validate_candidate(
      candidate);

  if (advanced_state(
          candidate.state)) {
    throw std::invalid_argument(
        "Advanced investigation state must not create ClusterProblem work.");
  }

  IntelligenceJob job;

  job.id =
      cluster_problem_job_id(
          candidate);

  job.type =
      IntelligenceJobType::ClusterProblem;

  // Local post-drive analysis.
  //
  // ResourceGovernor will defer this while navigation is active
  // or thermal state is Hot/Critical.
  job.workload =
      WorkloadClass::PostDrive;

  job.priority =
      cluster_problem_job_priority(
          candidate);

  // The clustering/problem-analysis bridge is local-first.
  job.requires_network =
      false;

  job.requires_charging =
      false;

  job.minimum_battery_percent =
      25;

  job.subject_key =
      candidate.cluster_key;

  job.context_key =
      candidate.context_key;

  job.data_scope_key =
      std::string(
          diagnostics::
              diagnostic_evidence_scope_key(
                  candidate.evidence_scope));

  job.reason_key =
      candidate.reason_key;

  job.evidence_revision =
      static_cast<std::uint64_t>(
          candidate.observation_count);

  return job;
}


DiagnosticInvestigationJobResult
ensure_cluster_problem_job(
    IntelligenceJobQueue& queue,
    const diagnostics::InvestigationCandidate&
        candidate) {
  validate_candidate(
      candidate);

  if (advanced_state(
          candidate.state)) {
    return {
        DiagnosticInvestigationJobStatus::
            SkippedAdvancedState,
        {},
    };
  }

  const auto queue_result =
      queue.enqueue_or_coalesce(
          make_cluster_problem_job(
              candidate));

  switch (queue_result.status) {
    case IntelligenceJobEnqueueStatus::Added:
      return {
          DiagnosticInvestigationJobStatus::Added,
          queue_result.id,
      };

    case IntelligenceJobEnqueueStatus::Coalesced:
      return {
          DiagnosticInvestigationJobStatus::Coalesced,
          queue_result.id,
      };

    case IntelligenceJobEnqueueStatus::ExistingRunning:
      return {
          DiagnosticInvestigationJobStatus::
              ExistingRunning,
          queue_result.id,
      };

    case IntelligenceJobEnqueueStatus::ExistingTerminal:
      return {
          DiagnosticInvestigationJobStatus::
              ExistingTerminal,
          queue_result.id,
      };
  }

  throw std::logic_error(
      "Unknown IntelligenceJobEnqueueStatus.");
}


DiagnosticInvestigationBatchResult
enqueue_investigation_candidates(
    IntelligenceJobQueue& queue,
    const std::vector<
        diagnostics::InvestigationCandidate>&
        candidates) {
  DiagnosticInvestigationBatchResult
      result;

  for (const auto& candidate :
       candidates) {
    const auto item =
        ensure_cluster_problem_job(
            queue,
            candidate);

    switch (item.status) {
      case DiagnosticInvestigationJobStatus::Added:
        ++result.added_count;
        break;

      case DiagnosticInvestigationJobStatus::Coalesced:
        ++result.coalesced_count;
        break;

      case DiagnosticInvestigationJobStatus::
          ExistingRunning:
        ++result.existing_running_count;
        break;

      case DiagnosticInvestigationJobStatus::
          ExistingTerminal:
        ++result.existing_terminal_count;
        break;

      case DiagnosticInvestigationJobStatus::
          SkippedAdvancedState:
        ++result.skipped_advanced_state_count;
        break;
    }

    if (!item.job_id.empty()) {
      result.job_ids.push_back(
          item.job_id);
    }
  }

  return result;
}

}  // namespace routing::core::intelligence
