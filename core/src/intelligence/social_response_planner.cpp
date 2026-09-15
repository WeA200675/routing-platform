#include "routing/core/intelligence/social_response_planner.hpp"

#include <algorithm>

namespace routing::core::intelligence {
namespace {

double clamp_level(
    const double value) {
  return std::clamp(
      value,
      0.0,
      100.0);
}

}  // namespace

SocialResponsePlan SocialResponsePlanner::plan(
    const SocialResponseContext context,
    const SocialNeuralOutput& inference,
    const bool adult_flirt_opt_in) const {
  SocialResponsePlan result;

  result.humor_level =
      clamp_level(
          inference.humor_level);

  result.warmth_level =
      clamp_level(
          inference.warmth_level);

  result.directness_level =
      clamp_level(
          inference.directness_level);

  result.charm_level =
      clamp_level(
          inference.charm_level);

  result.playfulness_level =
      clamp_level(
          inference.playfulness_level);

  result.flirt_level =
      adult_flirt_opt_in
      ? clamp_level(
            inference.flirt_level)
      : 0.0;

  switch (context) {
    case SocialResponseContext::Conversation:
      result.allow_banter =
          result.humor_level >= 10.0 ||
          result.playfulness_level >= 10.0;

      result.allow_flirt =
          adult_flirt_opt_in &&
          result.flirt_level >= 10.0;

      return result;

    case SocialResponseContext::NavigationStatus:
      result.humor_level *= 0.35;
      result.charm_level *= 0.35;
      result.playfulness_level *= 0.35;

      // Flirt is conversation-only, even when adult opt-in exists.
      result.flirt_level = 0.0;
      result.allow_flirt = false;

      result.allow_banter =
          result.humor_level >= 20.0 ||
          result.playfulness_level >= 20.0;

      return result;

    case SocialResponseContext::CriticalGuidance:
      result.humor_level = 0.0;
      result.charm_level = 0.0;
      result.playfulness_level = 0.0;
      result.flirt_level = 0.0;

      result.warmth_level =
          std::max(
              result.warmth_level,
              70.0);

      result.directness_level =
          std::max(
              result.directness_level,
              90.0);

      result.allow_banter = false;
      result.allow_flirt = false;
      result.suppress_nonessential_social = true;

      return result;
  }

  return result;
}

}  // namespace routing::core::intelligence
