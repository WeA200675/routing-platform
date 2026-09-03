#include "routing/core/testing/regression_suite.hpp"

#include <stdexcept>
#include <string>
#include <unordered_set>

namespace routing::core::testing {

void validate_regression_suite(
    const std::vector<RoutingRegressionCase>& cases) {
  std::unordered_set<std::string>
      case_ids;

  for (const auto& regression_case :
       cases) {
    validate_regression_case(
        regression_case);

    const auto inserted =
        case_ids.insert(
            regression_case.case_id);

    if (!inserted.second) {
      throw std::invalid_argument(
          "Duplicate regression case_id: " +
          regression_case.case_id);
    }
  }
}

RegressionSuiteResult run_regression_suite(
    const IRoutingEngine& routing_engine,
    const std::vector<RoutingRegressionCase>& cases) {
  validate_regression_suite(
      cases);

  RegressionSuiteResult result;

  result.total_case_count =
      cases.size();

  result.passed = true;

  for (const auto& regression_case :
       cases) {
    RegressionCaseResult case_result;

    case_result.regression_case =
        regression_case;

    if (regression_case.disposition ==
        RegressionDisposition::Disabled) {
      ++result.skipped_case_count;

      case_result.executed = false;
      case_result.passed = true;
      case_result.gates_suite = false;

      result.cases.push_back(
          std::move(case_result));

      continue;
    }

    case_result.executed = true;

    ++result.executed_case_count;

    case_result.scenario_result =
        run_routing_scenario(
            routing_engine,
            regression_case.scenario);

    case_result.passed =
        case_result.scenario_result
            ->passed;

    if (regression_case.disposition ==
        RegressionDisposition::Gating) {
      case_result.gates_suite = true;

      ++result.gating_case_count;

      if (!case_result.passed) {
        ++result.gating_failure_count;
        result.passed = false;
      }
    } else {
      case_result.gates_suite = false;

      ++result.observe_case_count;

      if (!case_result.passed) {
        ++result.observe_failure_count;
      }
    }

    result.cases.push_back(
        std::move(case_result));
  }

  return result;
}

}  // namespace routing::core::testing
