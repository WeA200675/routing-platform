#pragma once

#include <string>

#include "routing/core/testing/regression_suite.hpp"

namespace routing::core::testing {

[[nodiscard]]
std::string format_regression_suite_report(
    const RegressionSuiteResult& result,
    bool include_scenario_reports = false);

}  // namespace routing::core::testing
