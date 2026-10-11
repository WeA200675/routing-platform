#include "routing/core/intelligence/ai_policy.hpp"

#include <algorithm>
#include <stdexcept>

namespace routing::core::intelligence {

LearningIntensity LearningIntensity::from_ui_level(
    const std::uint8_t level) {
  if (level > 10) {
    throw std::invalid_argument(
        "AI learning level must be between 0 and 10.");
  }

  LearningIntensity result;
  result.permille =
      static_cast<std::uint16_t>(level) * 100;

  return result;
}

std::uint8_t LearningIntensity::ui_level() const {
  if (permille > 1000) {
    throw std::logic_error(
        "AI learning intensity exceeds internal range.");
  }

  return static_cast<std::uint8_t>(
      (permille + 50) / 100);
}

double LearningIntensity::normalized() const {
  if (permille > 1000) {
    throw std::logic_error(
        "AI learning intensity exceeds internal range.");
  }

  return static_cast<double>(permille) / 1000.0;
}

const AiAttributePolicy*
find_attribute_policy(
    const AiPolicy& policy,
    const Attribute attribute) {
  const auto found =
      std::find_if(
          policy.attributes.begin(),
          policy.attributes.end(),
          [attribute](
              const AiAttributePolicy& candidate) {
            return candidate.attribute == attribute;
          });

  return found == policy.attributes.end()
      ? nullptr
      : &*found;
}

double effective_learning_factor(
    const AiPolicy& policy,
    const AiAttributePolicy& attribute_policy) {
  return
      policy.global_learning_intensity.normalized() *
      attribute_policy.learning_intensity.normalized();
}

AiAdjustmentDecision evaluate_ai_adjustment(
    const AiPolicy& policy,
    const AiAttributePolicy& attribute_policy,
    const AiParameterAdjustment& adjustment) {
  if (adjustment.attribute !=
      attribute_policy.attribute) {
    throw std::invalid_argument(
        "AI adjustment attribute does not match policy.");
  }

  if (adjustment.confidence < 0.0 ||
      adjustment.confidence > 1.0) {
    throw std::invalid_argument(
        "AI adjustment confidence must be between 0 and 1.");
  }

  if (attribute_policy.minimum_confidence < 0.0 ||
      attribute_policy.minimum_confidence > 1.0) {
    throw std::invalid_argument(
        "AI minimum confidence must be between 0 and 1.");
  }

  if (attribute_policy.max_abs_adjustment < 0.0) {
    throw std::invalid_argument(
        "AI maximum adjustment must not be negative.");
  }

  AiAdjustmentDecision decision;

  decision.bounded_delta =
      std::clamp(
          adjustment.requested_delta,
          -attribute_policy.max_abs_adjustment,
          attribute_policy.max_abs_adjustment);

  if (policy.mode ==
      AiAutonomyMode::Disabled) {
    decision.reason = "ai_disabled";
    return decision;
  }

  if (!attribute_policy.permissions.observe) {
    decision.reason =
        "observation_not_allowed";
    return decision;
  }

  // Harte User-Sperre schlägt jede KI-Stufe.
  if (attribute_policy.user_locked) {
    decision.reason = "user_locked";
    return decision;
  }

  if (!attribute_policy.permissions.learn ||
      effective_learning_factor(
          policy,
          attribute_policy) <= 0.0) {
    decision.reason = "learning_disabled";
    return decision;
  }

  if (adjustment.confidence <
      attribute_policy.minimum_confidence) {
    decision.reason =
        "below_confidence_threshold";
    return decision;
  }

  switch (policy.mode) {
    case AiAutonomyMode::Disabled:
      decision.reason = "ai_disabled";
      return decision;

    case AiAutonomyMode::Observe:
      decision.reason = "observation_only";
      return decision;

    case AiAutonomyMode::Ask:
      decision.reason = "question_only";
      return decision;

    case AiAutonomyMode::Propose:
      if (!attribute_policy.permissions.propose) {
        decision.reason =
            "proposal_not_allowed";
        return decision;
      }

      decision.eligible = true;
      decision.reason = "proposal_ready";
      return decision;

    case AiAutonomyMode::Shadow:
      decision.eligible = true;
      decision.shadow_only = true;
      decision.reason = "shadow_only";
      return decision;

    case AiAutonomyMode::BoundedAutomatic:
      if (!attribute_policy.permissions.apply) {
        decision.reason =
            "automatic_apply_not_allowed";
        return decision;
      }

      decision.eligible = true;
      decision.effective_delta =
          decision.bounded_delta;
      decision.reason =
          "bounded_automatic_apply";
      return decision;
  }

  throw std::logic_error(
      "Unknown AI autonomy mode.");
}

}  // namespace routing::core::intelligence
