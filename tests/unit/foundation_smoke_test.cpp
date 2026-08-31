#include <cassert>
#include <iostream>

#include "routing/core/cost_math.h"
#include "routing/core/version.hpp"

int main() {
  using routing::core::ExposurePenaltyInput;
  using routing::core::ExposurePenaltySeconds;
  using routing::core::MeetsShortcutThreshold;
  using routing::core::WithinComfortBudget;

  assert(routing::core::kFoundationVersion == "0.2.0");

  // 90% severity, 3 km exposure, 60 s-equivalent/km, quadratic shaping.
  const double penalty = ExposurePenaltySeconds({0.9, 3.0, 60.0, 2.0});
  assert(penalty > 145.7 && penalty < 145.9);

  assert(!MeetsShortcutThreshold(9.0 * 60.0, 10.0 * 60.0));
  assert(MeetsShortcutThreshold(10.0 * 60.0, 10.0 * 60.0));

  assert(WithinComfortBudget(57.0 * 60.0, 42.0 * 60.0, 15.0 * 60.0));
  assert(!WithinComfortBudget(58.0 * 60.0, 42.0 * 60.0, 15.0 * 60.0));

  std::cout << "Foundation 0.1 smoke tests passed\n";
  return 0;
}
