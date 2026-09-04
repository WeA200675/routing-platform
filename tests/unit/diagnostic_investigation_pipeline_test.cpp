#include <algorithm>
#include <cassert>
#include <iostream>
#include <string>

#include "routing/core/diagnostics/anomaly_tracker.hpp"
#include "routing/core/diagnostics/investigation_candidate.hpp"
#include "routing/core/intelligence/diagnostic_investigation_job.hpp"

namespace {

routing::core::diagnostics::DiagnosticEvidenceRecord
make_record(
    const std::string& record_id,
    const std::string& observation_id,
    const routing::core::diagnostics::
        DiagnosticEvidenceScope scope,
    const std::string& route_id) {
  using namespace routing::core;
  using namespace routing::core::diagnostics;

  DiagnosticEvidenceRecord record;

  record.record_id =
      record_id;

  record.source =
      DiagnosticEvidenceSource::RegressionCase;

  record.evidence_scope =
      scope;

  record.observation_id =
      observation_id;

  record.source_ref =
      "regression:urban-coverage";

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

const routing::core::intelligence::IntelligenceJob*
find_scope_job(
    const routing::core::intelligence::
        IntelligenceJobQueue& queue,
    const std::vector<
        routing::core::diagnostics::
            InvestigationCandidate>& candidates,
    const routing::core::diagnostics::
        DiagnosticEvidenceScope scope) {
  using namespace routing::core::intelligence;

  const auto candidate =
      std::find_if(
          candidates.begin(),
          candidates.end(),
          [&](const auto& item) {
            return item.evidence_scope ==
                scope;
          });

  if (candidate ==
      candidates.end()) {
    return nullptr;
  }

  return queue.find(
      cluster_problem_job_id(
          *candidate));
}

}  // namespace


int main() {
  using namespace routing::core::diagnostics;
  using namespace routing::core::intelligence;

  AnomalyTracker tracker;


  // -------------------------------------------------------------
  // Two LOCAL observations -> one Warning investigation candidate.
  // -------------------------------------------------------------

  tracker.ingest(
      make_record(
          "local:1",
          "local-observation:1",
          DiagnosticEvidenceScope::LocalOnly,
          "route-a"));

  tracker.ingest(
      make_record(
          "local:2",
          "local-observation:2",
          DiagnosticEvidenceScope::LocalOnly,
          "route-b"));

  auto candidates =
      build_investigation_candidates(
          tracker.clusters());

  assert(
      candidates.size() == 1);

  assert(
      candidates.front().observation_count ==
      2);

  IntelligenceJobQueue queue;

  const auto first_batch =
      enqueue_investigation_candidates(
          queue,
          candidates);

  assert(
      first_batch.added_count == 1);

  assert(
      first_batch.coalesced_count == 0);

  assert(queue.size() == 1);


  // Reprocessing the same candidate set must not duplicate work.
  const auto second_batch =
      enqueue_investigation_candidates(
          queue,
          candidates);

  assert(
      second_batch.added_count == 0);

  assert(
      second_batch.coalesced_count == 1);

  assert(queue.size() == 1);


  // -------------------------------------------------------------
  // Same diagnostic/context in PERSONAL scope remains separate.
  // -------------------------------------------------------------

  tracker.ingest(
      make_record(
          "personal:1",
          "personal-observation:1",
          DiagnosticEvidenceScope::Personal,
          "route-personal-a"));

  tracker.ingest(
      make_record(
          "personal:2",
          "personal-observation:2",
          DiagnosticEvidenceScope::Personal,
          "route-personal-b"));

  assert(
      tracker.size() == 2);

  candidates =
      build_investigation_candidates(
          tracker.clusters());

  assert(
      candidates.size() == 2);

  const auto third_batch =
      enqueue_investigation_candidates(
          queue,
          candidates);

  // Existing LOCAL work coalesces.
  // PERSONAL work becomes a second independent job.
  assert(
      third_batch.added_count == 1);

  assert(
      third_batch.coalesced_count == 1);

  assert(queue.size() == 2);


  const auto* local_job =
      find_scope_job(
          queue,
          candidates,
          DiagnosticEvidenceScope::LocalOnly);

  const auto* personal_job =
      find_scope_job(
          queue,
          candidates,
          DiagnosticEvidenceScope::Personal);

  assert(local_job != nullptr);
  assert(personal_job != nullptr);

  assert(
      local_job->id !=
      personal_job->id);

  assert(
      local_job->subject_key !=
      personal_job->subject_key);

  assert(
      local_job->data_scope_key ==
      "local-only");

  assert(
      personal_job->data_scope_key ==
      "personal");


  // Bridge is analysis scheduling only.
  assert(
      local_job->type ==
      IntelligenceJobType::ClusterProblem);

  assert(
      personal_job->type ==
      IntelligenceJobType::ClusterProblem);

  assert(
      !local_job->requires_network);

  assert(
      !personal_job->requires_network);

  std::cout
      << "Diagnostic investigation pipeline tests passed\n";

  return 0;
}
