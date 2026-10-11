#pragma once

#include <sstream>
#include <string>
#include <vector>

#include "routing/core/diagnostics/anomaly_tracker.hpp"
#include "routing/core/diagnostics/investigation_candidate.hpp"

namespace routing::core::diagnostics {

[[nodiscard]]
inline std::string
format_anomaly_tracking_report(
    const AnomalyTracker& tracker,
    const std::vector<InvestigationCandidate>&
        investigation_candidates = {}) {
  std::ostringstream output;

  output
      << "DIAGNOSTIC ANOMALY TRACKING\n"
      << "observer-only: yes\n"
      << "routing changes: no\n"
      << "map-data changes: no\n"
      << "rule changes: no\n"
      << "automatic hypothesis creation: no\n"
      << "automatic intelligence jobs: no\n"
      << "automatic global promotion: no\n"
      << "\n"
      << "clusters: "
      << tracker.clusters().size()
      << "\n"
      << "investigation candidates: "
      << investigation_candidates.size()
      << "\n\n";

  for (const auto& cluster :
       tracker.clusters()) {
    output
        << "["
        << diagnostic_severity_key(
               cluster.max_severity)
        << "] "
        << cluster.diagnostic_code
        << "\n"
        << "  key: "
        << cluster.cluster_key
        << "\n"
        << "  evidence scope: "
        << diagnostic_evidence_scope_key(
               cluster.evidence_scope)
        << "\n"
        << "  context: "
        << cluster.context_key
        << "\n"
        << "  state: "
        << investigation_state_key(
               cluster.state)
        << "\n"
        << "  occurrences: "
        << cluster.occurrence_count
        << "\n"
        << "  independent observations: "
        << cluster.observation_ids.size()
        << "\n"
        << "  sources: "
        << cluster.source_refs.size()
        << "\n"
        << "  routes: "
        << cluster.affected_route_ids.size()
        << "\n"
        << "  families: "
        << cluster.affected_families.size()
        << "\n"
        << "  versions: "
        << cluster.version_refs.size()
        << "\n"
        << "  retained evidence samples: "
        << cluster.evidence_samples.size()
        << "\n";

    if (cluster.first_seen_ms.has_value()) {
      output
          << "  first seen ms: "
          << *cluster.first_seen_ms
          << "\n";
    }

    if (cluster.last_seen_ms.has_value()) {
      output
          << "  last seen ms: "
          << *cluster.last_seen_ms
          << "\n";
    }

    output << "\n";
  }

  if (!investigation_candidates.empty()) {
    output
        << "investigation candidates\n";

    for (const auto& candidate :
         investigation_candidates) {
      output
          << "  ["
          << diagnostic_severity_key(
                 candidate.severity)
          << "] "
          << candidate.diagnostic_code
          << " observations="
          << candidate.observation_count
          << " occurrences="
          << candidate.occurrence_count
          << " reason="
          << candidate.reason_key
          << "\n";
    }
  }

  return output.str();
}

}  // namespace routing::core::diagnostics
