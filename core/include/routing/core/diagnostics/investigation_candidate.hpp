#pragma once

#include <algorithm>
#include <cstddef>
#include <cstdint>
#include <string>
#include <utility>
#include <vector>

#include "routing/core/diagnostics/anomaly_tracker.hpp"

namespace routing::core::diagnostics {

struct InvestigationCandidatePolicy {
  // Distinct observations, not individual route occurrences.
  std::size_t minimum_info_observations = 3;
  std::size_t minimum_warning_observations = 2;
  std::size_t minimum_error_observations = 1;

  bool include_resolved = false;
  bool include_dismissed = false;
};

struct InvestigationCandidate {
  std::string cluster_key;

  DiagnosticEvidenceScope evidence_scope =
      DiagnosticEvidenceScope::LocalOnly;

  std::string context_key;
  std::string diagnostic_code;

  DiagnosticSeverity severity =
      DiagnosticSeverity::Info;

  InvestigationState state =
      InvestigationState::Observed;

  std::uint64_t occurrence_count = 0;

  std::size_t observation_count = 0;
  std::size_t source_count = 0;
  std::size_t affected_route_count = 0;
  std::size_t affected_family_count = 0;

  std::string reason_key;
};

namespace detail {

inline std::size_t
minimum_observations(
    const DiagnosticSeverity severity,
    const InvestigationCandidatePolicy& policy) {
  switch (severity) {
    case DiagnosticSeverity::Info:
      return policy.minimum_info_observations;

    case DiagnosticSeverity::Warning:
      return policy.minimum_warning_observations;

    case DiagnosticSeverity::Error:
      return policy.minimum_error_observations;
  }

  return policy.minimum_warning_observations;
}

inline std::string
investigation_reason_key(
    const DiagnosticSeverity severity) {
  switch (severity) {
    case DiagnosticSeverity::Info:
      return
          "diagnostic.investigation.repeated_info_observation";

    case DiagnosticSeverity::Warning:
      return
          "diagnostic.investigation.repeated_warning_observation";

    case DiagnosticSeverity::Error:
      return
          "diagnostic.investigation.error_observed";
  }

  return
      "diagnostic.investigation.observed";
}

}  // namespace detail


// Produces investigation candidates only.
//
// This function does NOT:
//   create an AI hypothesis,
//   enqueue an intelligence job,
//   alter map data,
//   alter routing rules,
//   alter CostEngine parameters,
//   promote evidence scope.
[[nodiscard]]
inline std::vector<InvestigationCandidate>
build_investigation_candidates(
    const std::vector<AnomalyCluster>& clusters,
    const InvestigationCandidatePolicy& policy = {}) {
  std::vector<InvestigationCandidate>
      result;

  for (const auto& cluster :
       clusters) {
    if (cluster.state ==
            InvestigationState::Resolved &&
        !policy.include_resolved) {
      continue;
    }

    if (cluster.state ==
            InvestigationState::Dismissed &&
        !policy.include_dismissed) {
      continue;
    }

    const std::size_t observation_count =
        cluster.observation_ids.size();

    if (observation_count <
        detail::minimum_observations(
            cluster.max_severity,
            policy)) {
      continue;
    }

    InvestigationCandidate candidate;

    candidate.cluster_key =
        cluster.cluster_key;

    candidate.evidence_scope =
        cluster.evidence_scope;

    candidate.context_key =
        cluster.context_key;

    candidate.diagnostic_code =
        cluster.diagnostic_code;

    candidate.severity =
        cluster.max_severity;

    candidate.state =
        cluster.state;

    candidate.occurrence_count =
        cluster.occurrence_count;

    candidate.observation_count =
        observation_count;

    candidate.source_count =
        cluster.source_refs.size();

    candidate.affected_route_count =
        cluster.affected_route_ids.size();

    candidate.affected_family_count =
        cluster.affected_families.size();

    candidate.reason_key =
        detail::investigation_reason_key(
            cluster.max_severity);

    result.push_back(
        std::move(candidate));
  }

  std::sort(
      result.begin(),
      result.end(),
      [](const auto& left,
         const auto& right) {
        const auto left_severity =
            static_cast<std::uint8_t>(
                left.severity);

        const auto right_severity =
            static_cast<std::uint8_t>(
                right.severity);

        if (left_severity !=
            right_severity) {
          return left_severity >
              right_severity;
        }

        if (left.observation_count !=
            right.observation_count) {
          return left.observation_count >
              right.observation_count;
        }

        return left.cluster_key <
            right.cluster_key;
      });

  return result;
}

}  // namespace routing::core::diagnostics
