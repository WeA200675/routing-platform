#pragma once

#include <string>
#include <vector>

#include "routing/core/testing/regression_promotion.hpp"

namespace routing::core::testing {

[[nodiscard]]
std::string format_regression_promotion_report(
    const std::vector<RegressionPromotionProposal>& proposals);

}  // namespace routing::core::testing
