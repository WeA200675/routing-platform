#pragma once

#include <cstddef>
#include <optional>
#include <vector>

#include "routing/core/testing/regression_case.hpp"
#include "routing/core/testing/scenario_runner.hpp"

namespace routing::core::testing {

struct RegressionCaseResult {
  RoutingRegressionCase regression_case;

  bool executed = false;
  bool passed = false;
  bool gates_suite = false;

  std::optional<RoutingScenarioResult>
      scenario_result;
};

struct RegressionSuiteResult {
  bool passed = false;

  std::size_t total_case_count = 0;
  std::size_t executed_case_count = 0;
  std::size_t skipped_case_count = 0;

  std::size_t gating_case_count = 0;
  std::size_t gating_failure_count = 0;

  std::size_t observe_case_count = 0;
  std::size_t observe_failure_count = 0;

  std::vector<RegressionCaseResult>
      cases;
};

void validate_regression_suite(
    const std::vector<RoutingRegressionCase>& cases);

[[nodiscard]]
RegressionSuiteResult run_regression_suite(
    const IRoutingEngine& routing_engine,
    const std::vector<RoutingRegressionCase>& cases);

}  // namespace routing::core::testing
