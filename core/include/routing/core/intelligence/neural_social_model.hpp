#pragma once

namespace routing::core::intelligence {

struct SocialNeuralInput {
  // Explicit user-selected levels: 0.0 .. 100.0.
  double humor_level = 0.0;
  double warmth_level = 50.0;
  double directness_level = 50.0;
  double charm_level = 0.0;
  double playfulness_level = 0.0;
  double flirt_level = 0.0;

  // Runtime signals: 0.0 .. 1.0.
  double engagement = 0.5;
  double context_confidence = 1.0;

  // Flirt is never inferred as consent.
  bool adult_flirt_opt_in = false;

  // Defense in depth. Critical guidance suppresses embellishment.
  bool critical_guidance = false;
};

struct SocialNeuralOutput {
  double humor_level = 0.0;
  double warmth_level = 0.0;
  double directness_level = 0.0;
  double charm_level = 0.0;
  double playfulness_level = 0.0;
  double flirt_level = 0.0;
};

class NeuralSocialModel {
 public:
  [[nodiscard]]
  SocialNeuralOutput infer(
      const SocialNeuralInput& input) const;
};

}  // namespace routing::core::intelligence
