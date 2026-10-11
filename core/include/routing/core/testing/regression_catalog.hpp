#pragma once

#include <vector>

#include "routing/core/testing/regression_case.hpp"

namespace routing::core::testing {

[[nodiscard]]
std::vector<RoutingRegressionCase>
builtin_regression_cases();

}  // namespace routing::core::testing
