#pragma once

#include <cstdint>

#include "routing/core/intelligence/neural_social_model.hpp"

namespace routing::core::intelligence {

enum class SocialResponseContext : std::uint8_t {
  Conversation = 0,
  NavigationStatus,
  CriticalGuidance,
};

struct SocialResponsePlan {
  double humor_level = 0.0;
  double warmth_level = 0.0;
  double directness_level = 0.0;
  double charm_level = 0.0;
  double playfulness_level = 0.0;
  double flirt_level = 0.0;

  bool allow_banter = false;
  bool allow_flirt = false;

  bool suppress_nonessential_social = false;
};

class SocialResponsePlanner {
 public:
  [[nodiscard]]
  SocialResponsePlan plan(
      SocialResponseContext context,
      const SocialNeuralOutput& inference,
      bool adult_flirt_opt_in) const;
};

}  // namespace routing::core::intelligence
