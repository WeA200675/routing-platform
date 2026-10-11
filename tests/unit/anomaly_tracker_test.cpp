#include <cassert>
#include <cstdint>
#include <iostream>
#include <string>

#include "routing/core/diagnostics/anomaly_tracker.hpp"

namespace {

routing::core::diagnostics::DiagnosticEvidenceRecord
make_record(
    const std::string& record_id,
    const std::string& observation_id,
    const std::string& source_ref,
    const routing::core::diagnostics::
        DiagnosticEvidenceSource source,
    const routing::core::diagnostics::
        DiagnosticEvidenceScope evidence_scope,
    const routing::core::diagnostics::
        DiagnosticSeverity severity,
    const std::string& route_id,
    const routing::core::CandidateFamily family,
    const std::uint64_t observed_at_ms,
    const std::string& version_ref) {
  using namespace routing::core;
  using namespace routing::core::diagnostics;

  DiagnosticEvidenceRecord record;

  record.record_id =
      record_id;

  record.observation_id =
      observation_id;

  record.source_ref =
      source_ref;

  record.source =
      source;

  record.evidence_scope =
      evidence_scope;

  record.context_key =
      "li:vaduz-ruggell";

  record.version_ref =
      version_ref;

  record.observed_at_ms =
      observed_at_ms;

  record.diagnostic.code =
      "DATA_URBAN_POSITIVE_SIGNAL_ABSENT";

  record.diagnostic.severity =
      severity;

  record.diagnostic.category =
      DiagnosticCategory::DataSignal;

  record.diagnostic.scope =
      DiagnosticScope::Route;

  record.diagnostic.family =
      family;

  record.diagnostic.route_id =
      route_id;

  record.diagnostic.explanation_key =
      "diagnostic.data.urban.positive_signal_absent";

  return record;
}

}  // namespace

int main() {
  using namespace routing::core;
  using namespace routing::core::diagnostics;

  AnomalyTracker tracker(2);

  const auto first =
      make_record(
          "record:1",
          "observation:1",
          "scenario:1",
          DiagnosticEvidenceSource::Scenario,
          DiagnosticEvidenceScope::LocalOnly,
          DiagnosticSeverity::Info,
          "route-a",
          CandidateFamily::Fastest,
          1000,
          "version:1");

  const auto second =
      make_record(
          "record:2",
          "observation:2",
          "regression:1",
          DiagnosticEvidenceSource::RegressionCase,
          DiagnosticEvidenceScope::LocalOnly,
          DiagnosticSeverity::Warning,
          "route-b",
          CandidateFamily::MajorRoads,
          2000,
          "version:2");

  const auto first_result =
      tracker.ingest(
          first);

  assert(
      first_result.status ==
      AnomalyIngestStatus::AddedNewCluster);

  const auto second_result =
      tracker.ingest(
          second);

  assert(
      second_result.status ==
      AnomalyIngestStatus::AddedExistingCluster);

  assert(tracker.size() == 1);

  const auto cluster_key =
      diagnostic_cluster_key(
          first);

  const auto* cluster =
      tracker.find(
          cluster_key);

  assert(cluster != nullptr);

  assert(
      cluster->occurrence_count == 2);

  assert(
      cluster->observation_ids.size() == 2);

  assert(
      cluster->source_refs.size() == 2);

  assert(
      cluster->affected_route_ids.size() == 2);

  assert(
      cluster->affected_families.size() == 2);

  assert(
      cluster->version_refs.size() == 2);

  assert(
      cluster->max_severity ==
      DiagnosticSeverity::Warning);

  assert(
      cluster->first_seen_ms.has_value());

  assert(
      *cluster->first_seen_ms ==
      1000);

  assert(
      cluster->last_seen_ms.has_value());

  assert(
      *cluster->last_seen_ms ==
      2000);

  const auto duplicate =
      tracker.ingest(
          second);

  assert(
      duplicate.status ==
      AnomalyIngestStatus::DuplicateIgnored);

  assert(
      tracker.find(cluster_key)
          ->occurrence_count == 2);

  assert(
      tracker.set_investigation_state(
          cluster_key,
          InvestigationState::Investigating));

  const auto third =
      make_record(
          "record:3",
          "observation:3",
          "route-lab:1",
          DiagnosticEvidenceSource::RouteLab,
          DiagnosticEvidenceScope::LocalOnly,
          DiagnosticSeverity::Error,
          "route-c",
          CandidateFamily::ProfileOptimal,
          3000,
          "version:3");

  tracker.ingest(
      third);

  cluster =
      tracker.find(
          cluster_key);

  assert(cluster != nullptr);

  assert(
      cluster->occurrence_count == 3);

  assert(
      cluster->observation_ids.size() == 3);

  assert(
      cluster->max_severity ==
      DiagnosticSeverity::Error);

  // New evidence must not silently change workflow state.
  assert(
      cluster->state ==
      InvestigationState::Investigating);

  // Sample retention is bounded independently from total count.
  assert(
      cluster->evidence_samples.size() == 2);

  auto personal =
      make_record(
          "record:personal",
          "observation:personal",
          "drive:personal",
          DiagnosticEvidenceSource::DriveSession,
          DiagnosticEvidenceScope::Personal,
          DiagnosticSeverity::Info,
          "route-personal",
          CandidateFamily::Fastest,
          4000,
          "version:3");

  tracker.ingest(
      personal);

  assert(tracker.size() == 2);

  assert(
      diagnostic_cluster_key(
          personal) !=
      cluster_key);

  std::cout
      << "Anomaly tracker tests passed\n";

  return 0;
}
