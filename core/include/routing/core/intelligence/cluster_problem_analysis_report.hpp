#pragma once

#include <sstream>
#include <string>

#include "routing/core/intelligence/cluster_problem_analysis.hpp"

namespace routing::core::intelligence {

[[nodiscard]]
inline std::string
format_cluster_problem_analysis_report(
    const ClusterProblemAnalysisResult& result) {
  std::ostringstream output;

  output
      << "CLUSTER PROBLEM ANALYSIS\n"
      << "schema: "
      << result.schema_version
      << "\n"
      << "analysis id: "
      << result.analysis_id
      << "\n"
      << "job id: "
      << result.job_id
      << "\n"
      << "cluster: "
      << result.cluster_key
      << "\n"
      << "context: "
      << result.context_key
      << "\n"
      << "data scope: "
      << result.data_scope_key
      << "\n"
      << "diagnostic: "
      << result.diagnostic_code
      << "\n"
      << "status: "
      << cluster_problem_analysis_status_key(
             result.status)
      << "\n"
      << "domain: "
      << cluster_problem_domain_key(
             result.domain)
      << "\n"
      << "severity: "
      << diagnostics::diagnostic_severity_key(
             result.severity)
      << "\n"
      << "evidence revision: "
      << result.evidence_revision
      << "\n"
      << "observed cluster revision: "
      << result.observed_cluster_revision
      << "\n\n";

  output
      << "safety boundary\n"
      << "  preference hypothesis created: "
      << (result.preference_hypothesis_created
              ? "yes"
              : "no")
      << "\n"
      << "  learning gate invoked: "
      << (result.learning_gate_invoked
              ? "yes"
              : "no")
      << "\n"
      << "  question candidate created: "
      << (result.question_candidate_created
              ? "yes"
              : "no")
      << "\n"
      << "  production application allowed: "
      << (result.production_application_allowed
              ? "yes"
              : "no")
      << "\n"
      << "  evidence scope promotion allowed: "
      << (result.evidence_scope_promotion_allowed
              ? "yes"
              : "no")
      << "\n\n";

  output
      << "findings\n";

  for (const auto& finding :
       result.findings) {
    output
        << "  - "
        << finding.code
        << ": "
        << finding.detail
        << "\n";

    for (const auto& evidence :
         finding.evidence) {
      output
          << "      "
          << evidence.key
          << "="
          << evidence.value;

      if (!evidence.unit.empty()) {
        output
            << " "
            << evidence.unit;
      }

      output << "\n";
    }
  }

  output
      << "\nnext actions\n";

  if (result.next_actions.empty()) {
    output
        << "  - none\n";
  } else {
    for (const auto action :
         result.next_actions) {
      output
          << "  - "
          << cluster_problem_next_action_key(
                 action)
          << "\n";
    }
  }

  return output.str();
}

}  // namespace routing::core::intelligence
