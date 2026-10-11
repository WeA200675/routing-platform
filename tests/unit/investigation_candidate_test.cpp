#include <algorithm>
#include <cassert>
#include <iostream>
#include <string>
#include <vector>

#include "routing/core/diagnostics/investigation_candidate.hpp"

namespace {

routing::core::diagnostics::AnomalyCluster
make_cluster(
    const std::string& key,
    const routing::core::diagnostics::
        DiagnosticSeverity severity,
    const std::size_t observations,
    const routing::core::diagnostics::
        InvestigationState state =
            routing::core::diagnostics::
                InvestigationState::Observed) {
  using namespace routing::core::diagnostics;

  AnomalyCluster cluster;

  cluster.cluster_key =
      key;

  cluster.context_key =
      "context";

  cluster.diagnostic_code =
      key;

  cluster.max_severity =
      severity;

  cluster.state =
      state;

  // Deliberately larger than independent observation count.
  cluster.occurrence_count =
      observations + 2;

  for (std::size_t index = 0;
       index < observations;
       ++index) {
    cluster.observation_ids.push_back(
        "observation:" +
        std::to_string(index));
  }

  return cluster;
}

bool has_code(
    const std::vector<
        routing::core::diagnostics::
            InvestigationCandidate>& candidates,
    const std::string& code) {
  return std::any_of(
      candidates.begin(),
      candidates.end(),
      [&](const auto& candidate) {
        return candidate.diagnostic_code ==
            code;
      });
}

}  // namespace

int main() {
  using namespace routing::core::diagnostics;

  std::vector<AnomalyCluster>
      clusters;

  clusters.push_back(
      make_cluster(
          "info-two",
          DiagnosticSeverity::Info,
          2));

  clusters.push_back(
      make_cluster(
          "info-three",
          DiagnosticSeverity::Info,
          3));

  clusters.push_back(
      make_cluster(
          "warning-one",
          DiagnosticSeverity::Warning,
          1));

  clusters.push_back(
      make_cluster(
          "warning-two",
          DiagnosticSeverity::Warning,
          2));

  clusters.push_back(
      make_cluster(
          "error-one",
          DiagnosticSeverity::Error,
          1));

  clusters.push_back(
      make_cluster(
          "resolved-error",
          DiagnosticSeverity::Error,
          10,
          InvestigationState::Resolved));

  clusters.push_back(
      make_cluster(
          "dismissed-error",
          DiagnosticSeverity::Error,
          10,
          InvestigationState::Dismissed));

  const auto candidates =
      build_investigation_candidates(
          clusters);

  assert(
      !has_code(
          candidates,
          "info-two"));

  assert(
      has_code(
          candidates,
          "info-three"));

  assert(
      !has_code(
          candidates,
          "warning-one"));

  assert(
      has_code(
          candidates,
          "warning-two"));

  assert(
      has_code(
          candidates,
          "error-one"));

  // Closed cases do not silently reopen.
  assert(
      !has_code(
          candidates,
          "resolved-error"));

  assert(
      !has_code(
          candidates,
          "dismissed-error"));

  assert(!candidates.empty());

  // Highest severity first.
  assert(
      candidates.front().severity ==
      DiagnosticSeverity::Error);

  const auto info =
      std::find_if(
          candidates.begin(),
          candidates.end(),
          [](const auto& candidate) {
            return candidate.diagnostic_code ==
                "info-three";
          });

  assert(info != candidates.end());

  // Threshold is based on independent observations.
  assert(
      info->observation_count == 3);

  assert(
      info->occurrence_count == 5);

  std::cout
      << "Investigation candidate tests passed\n";

  return 0;
}
