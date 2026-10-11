#include <cassert>
#include <iostream>
#include <string>

#include "routing/core/intelligence/diagnostic_investigation_job.hpp"

int main() {
  using namespace routing::core::diagnostics;
  using namespace routing::core::intelligence;

  InvestigationCandidate candidate;

  candidate.cluster_key =
      "diagnostic-cluster-v1|"
      "evidence_scope=local-only|"
      "context=li%3Avaduz-ruggell|"
      "category=data-signal|"
      "diagnostic_scope=route|"
      "code=DATA_URBAN_POSITIVE_SIGNAL_ABSENT";

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
      12;

  candidate.observation_count =
      3;

  candidate.reason_key =
      "diagnostic.investigation."
      "repeated_info_observation";


  // -------------------------------------------------------------
  // Deterministic local-first job construction.
  // -------------------------------------------------------------

  const auto job =
      make_cluster_problem_job(
          candidate);

  assert(
      job.type ==
      IntelligenceJobType::ClusterProblem);

  assert(
      job.workload ==
      WorkloadClass::PostDrive);

  assert(
      !job.requires_network);

  assert(
      !job.requires_charging);

  assert(
      job.minimum_battery_percent ==
      25);

  assert(
      job.subject_key ==
      candidate.cluster_key);

  assert(
      job.context_key ==
      candidate.context_key);

  assert(
      job.data_scope_key ==
      "local-only");

  assert(
      job.evidence_revision == 3);

  // INFO base 40 + two additional observations * 2.
  assert(
      job.priority == 44);


  // occurrence_count must not inflate scheduling priority.
  auto many_route_occurrences =
      candidate;

  many_route_occurrences.occurrence_count =
      1000;

  assert(
      cluster_problem_job_priority(
          many_route_occurrences) ==
      cluster_problem_job_priority(
          candidate));


  // -------------------------------------------------------------
  // Stable-ID / coalescing.
  // -------------------------------------------------------------

  IntelligenceJobQueue queue;

  const auto first =
      ensure_cluster_problem_job(
          queue,
          candidate);

  assert(
      first.status ==
      DiagnosticInvestigationJobStatus::Added);

  assert(queue.size() == 1);

  const auto repeat =
      ensure_cluster_problem_job(
          queue,
          candidate);

  assert(
      repeat.status ==
      DiagnosticInvestigationJobStatus::Coalesced);

  assert(queue.size() == 1);


  // More independent evidence may increase priority.
  auto stronger =
      candidate;

  stronger.severity =
      DiagnosticSeverity::Warning;

  stronger.state =
      InvestigationState::Investigating;

  stronger.observation_count =
      5;

  stronger.occurrence_count =
      100;

  stronger.reason_key =
      "diagnostic.investigation."
      "repeated_warning_observation";

  const auto stronger_result =
      ensure_cluster_problem_job(
          queue,
          stronger);

  assert(
      stronger_result.status ==
      DiagnosticInvestigationJobStatus::Coalesced);

  const auto* stored =
      queue.find(
          first.job_id);

  assert(stored != nullptr);

  // Warning 65 + four extra observations*2 + investigating 5.
  assert(
      stored->priority == 78);

  assert(
      stored->evidence_revision == 5);

  assert(
      stored->reason_key ==
      stronger.reason_key);


  // -------------------------------------------------------------
  // Existing ResourceGovernor protects active navigation.
  // -------------------------------------------------------------

  ResourceSnapshot driving;

  driving.navigation_active =
      true;

  driving.device_charging =
      false;

  driving.network_available =
      false;

  driving.battery_percent =
      80;

  driving.thermal_state =
      ThermalState::Nominal;

  const auto while_driving =
      queue.claim_next(
          driving);

  assert(
      !while_driving.has_value());

  stored =
      queue.find(
          first.job_id);

  assert(stored != nullptr);

  assert(
      stored->state ==
      IntelligenceJobState::Deferred);


  // Offline after drive is allowed.
  driving.navigation_active =
      false;

  const auto after_drive =
      queue.claim_next(
          driving);

  assert(
      after_drive.has_value());

  assert(
      after_drive->id ==
      first.job_id);

  assert(
      after_drive->state ==
      IntelligenceJobState::Running);


  // Running work is never mutated underneath the worker.
  auto newest =
      stronger;

  newest.observation_count =
      10;

  newest.severity =
      DiagnosticSeverity::Error;

  const auto running_result =
      ensure_cluster_problem_job(
          queue,
          newest);

  assert(
      running_result.status ==
      DiagnosticInvestigationJobStatus::
          ExistingRunning);

  queue.mark_completed(
      after_drive->id);


  // Terminal work is never silently reopened.
  const auto terminal_result =
      ensure_cluster_problem_job(
          queue,
          newest);

  assert(
      terminal_result.status ==
      DiagnosticInvestigationJobStatus::
          ExistingTerminal);


  // -------------------------------------------------------------
  // Advanced investigation states do not go backwards.
  // -------------------------------------------------------------

  auto hypothesis =
      candidate;

  hypothesis.cluster_key =
      "diagnostic-cluster:already-hypothesis";

  hypothesis.state =
      InvestigationState::HypothesisProposed;

  const auto advanced =
      ensure_cluster_problem_job(
          queue,
          hypothesis);

  assert(
      advanced.status ==
      DiagnosticInvestigationJobStatus::
          SkippedAdvancedState);

  assert(queue.size() == 1);

  std::cout
      << "Diagnostic investigation job tests passed\n";

  return 0;
}
