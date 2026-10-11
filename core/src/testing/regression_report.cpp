#include "routing/core/testing/regression_report.hpp"

#include <sstream>

#include "routing/core/testing/route_decision_report.hpp"

#include "routing/core/diagnostics/routing_diagnostic.hpp"

namespace routing::core::testing {

namespace {

const char* result_label(
    const RegressionCaseResult& result) {
  if (!result.executed) {
    return "SKIP";
  }

  if (result.passed) {
    return "PASS";
  }

  if (result.gates_suite) {
    return "FAIL";
  }

  return "OBSERVE-FAIL";
}

void append_diagnostic_summary(
    std::ostringstream& output,
    const std::vector<
        routing::core::diagnostics::RoutingDiagnostic>&
        diagnostics) {
  using routing::core::diagnostics::
      DiagnosticSeverity;
  using routing::core::diagnostics::
      diagnostic_severity_key;

  if (diagnostics.empty()) {
    return;
  }

  std::size_t warnings = 0;
  std::size_t errors = 0;

  for (const auto& diagnostic :
       diagnostics) {
    if (diagnostic.severity ==
        DiagnosticSeverity::Warning) {
      ++warnings;
    }

    if (diagnostic.severity ==
        DiagnosticSeverity::Error) {
      ++errors;
    }
  }

  output
      << "    diagnostics: total="
      << diagnostics.size()
      << " warning="
      << warnings
      << " error="
      << errors
      << "\n";

  // Keep normal regression output compact:
  // INFO observations remain available in verbose scenario reports.
  for (const auto& diagnostic :
       diagnostics) {
    if (diagnostic.severity ==
        DiagnosticSeverity::Info) {
      continue;
    }

    output
        << "    diagnostic: ["
        << diagnostic_severity_key(
               diagnostic.severity)
        << "] "
        << diagnostic.code;

    if (!diagnostic.route_id.empty()) {
      output
          << " route="
          << diagnostic.route_id;
    }

    output << "\n";
  }
}

}  // namespace

std::string format_regression_suite_report(
    const RegressionSuiteResult& result,
    const bool include_scenario_reports) {
  std::ostringstream output;

  output
      << "ROUTING REGRESSION SUITE\n"
      << "suite result: "
      << (result.passed ? "PASS" : "FAIL")
      << "\n"
      << "cases: "
      << result.total_case_count
      << "\n"
      << "executed: "
      << result.executed_case_count
      << "\n"
      << "skipped: "
      << result.skipped_case_count
      << "\n"
      << "gating: "
      << result.gating_case_count
      << "\n"
      << "gating failures: "
      << result.gating_failure_count
      << "\n"
      << "observe-only: "
      << result.observe_case_count
      << "\n"
      << "observe failures: "
      << result.observe_failure_count
      << "\n\n";

  for (const auto& case_result :
       result.cases) {
    const auto& regression_case =
        case_result.regression_case;

    output
        << "["
        << result_label(case_result)
        << "] "
        << regression_case.case_id
        << " v"
        << regression_case.case_version
        << " ["
        << regression_disposition_key(
               regression_case.disposition)
        << "]"
        << " source="
        << regression_case_source_key(
               regression_case
                   .provenance.source);

    if (!regression_case.issue_key.empty()) {
      output
          << " issue="
          << regression_case.issue_key;
    }

    if (!regression_case
             .provenance.source_ref
             .empty()) {
      output
          << " source_ref="
          << regression_case
                 .provenance.source_ref;
    }

    output
        << "\n"
        << "    "
        << regression_case.title
        << "\n";

    if (case_result.scenario_result
            .has_value()) {
      append_diagnostic_summary(
          output,
          case_result.scenario_result
              ->diagnostics);
    }

    if (case_result.scenario_result
            .has_value() &&
        !case_result.scenario_result
             ->passed) {
      for (const auto& assertion :
           case_result.scenario_result
               ->assertions) {
        if (assertion.passed) {
          continue;
        }

        output
            << "    assertion: "
            << assertion.key
            << " - "
            << assertion.detail
            << "\n";
      }
    }

    if (include_scenario_reports &&
        case_result.scenario_result
            .has_value()) {
      output
          << "\n"
          << format_routing_scenario_report(
                 *case_result.scenario_result)
          << "\n";
    }
  }

  return output.str();
}

}  // namespace routing::core::testing
