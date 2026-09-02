#include <cassert>
#include <cmath>
#include <stdexcept>

#include "routing/core/intelligence/ai_policy.hpp"

namespace {

bool nearly_equal(
    const double a,
    const double b,
    const double epsilon = 1e-9) {
  return std::abs(a - b) <= epsilon;
}

}  // namespace

int main() {
  using namespace routing::core;
  using namespace routing::core::intelligence;

  const auto level10 =
      LearningIntensity::from_ui_level(10);

  assert(level10.permille == 1000);
  assert(level10.ui_level() == 10);
  assert(nearly_equal(
      level10.normalized(),
      1.0));

  bool invalid_level_threw = false;

  try {
    (void)LearningIntensity::from_ui_level(11);
  } catch (const std::invalid_argument&) {
    invalid_level_threw = true;
  }

  assert(invalid_level_threw);

  AiPolicy policy;
  policy.global_learning_intensity =
      LearningIntensity::from_ui_level(7);

  policy.mode =
      AiAutonomyMode::BoundedAutomatic;

  AiAttributePolicy speed_policy;
  speed_policy.attribute =
      Attribute::SpeedLimitKmh;

  speed_policy.learning_intensity =
      LearningIntensity::from_ui_level(10);

  speed_policy.max_abs_adjustment = 10.0;
  speed_policy.minimum_confidence = 0.90;
  speed_policy.permissions.apply = true;

  assert(nearly_equal(
      effective_learning_factor(
          policy,
          speed_policy),
      0.7));

  AiParameterAdjustment adjustment;
  adjustment.attribute =
      Attribute::SpeedLimitKmh;

  adjustment.requested_delta = 25.0;
  adjustment.confidence = 0.95;

  adjustment.reason =
      "Repeated preference for main-road alternatives";

  const auto automatic =
      evaluate_ai_adjustment(
          policy,
          speed_policy,
          adjustment);

  // 10/10 Lernfähigkeit bedeutet ausdrücklich NICHT
  // unbegrenzten KI-Einfluss.
  assert(automatic.eligible);

  assert(nearly_equal(
      automatic.bounded_delta,
      10.0));

  assert(nearly_equal(
      automatic.effective_delta,
      10.0));

  speed_policy.user_locked = true;

  const auto locked =
      evaluate_ai_adjustment(
          policy,
          speed_policy,
          adjustment);

  assert(!locked.eligible);

  assert(nearly_equal(
      locked.effective_delta,
      0.0));

  assert(locked.reason == "user_locked");

  speed_policy.user_locked = false;

  policy.mode =
      AiAutonomyMode::Shadow;

  const auto shadow =
      evaluate_ai_adjustment(
          policy,
          speed_policy,
          adjustment);

  assert(shadow.eligible);
  assert(shadow.shadow_only);

  assert(nearly_equal(
      shadow.bounded_delta,
      10.0));

  assert(nearly_equal(
      shadow.effective_delta,
      0.0));

  policy.mode =
      AiAutonomyMode::BoundedAutomatic;

  adjustment.confidence = 0.50;

  const auto uncertain =
      evaluate_ai_adjustment(
          policy,
          speed_policy,
          adjustment);

  assert(!uncertain.eligible);

  assert(nearly_equal(
      uncertain.effective_delta,
      0.0));

  speed_policy.learning_intensity =
      LearningIntensity::from_ui_level(0);

  adjustment.confidence = 0.99;

  const auto learning_disabled =
      evaluate_ai_adjustment(
          policy,
          speed_policy,
          adjustment);

  assert(!learning_disabled.eligible);

  assert(
      learning_disabled.reason ==
      "learning_disabled");

  return 0;
}
