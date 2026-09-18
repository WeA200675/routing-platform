#include "routing/core/intelligence/neural_social_model.hpp"

#include <algorithm>
#include <cmath>

namespace routing::core::intelligence {
namespace {

double clamp_level(
    const double value) {
  return std::clamp(
      value,
      0.0,
      100.0);
}

double clamp_unit(
    const double value) {
  return std::clamp(
      value,
      0.0,
      1.0);
}

double normalized_level(
    const double value) {
  return clamp_level(value) /
      100.0;
}

double bounded_activation(
    const double value) {
  return 0.5 +
      0.5 *
          std::tanh(value);
}

}  // namespace

SocialNeuralOutput NeuralSocialModel::infer(
    const SocialNeuralInput& input) const {
  const double humor =
      normalized_level(
          input.humor_level);

  const double warmth =
      normalized_level(
          input.warmth_level);

  const double directness =
      normalized_level(
          input.directness_level);

  const double charm =
      normalized_level(
          input.charm_level);

  const double playfulness =
      normalized_level(
          input.playfulness_level);

  const double flirt =
      normalized_level(
          input.flirt_level);

  const double engagement =
      clamp_unit(
          input.engagement);

  const double context_confidence =
      clamp_unit(
          input.context_confidence);

  // Small deterministic feed-forward scoring layer.
  // The contract is intentionally bounded so a future trained backend can
  // replace these fixed weights without gaining authority over routing.
  const double affinity_hidden =
      bounded_activation(
          1.10 * warmth +
          0.90 * charm +
          0.70 * engagement -
          1.20);

  const double playful_hidden =
      bounded_activation(
          1.00 * humor +
          1.10 * playfulness +
          0.40 * engagement -
          1.00);

  const double confidence_gain =
      0.55 +
      0.45 *
          context_confidence;

  SocialNeuralOutput output;

  output.humor_level =
      clamp_level(
          100.0 *
          humor *
          confidence_gain *
          (0.75 +
           0.25 *
               playful_hidden));

  output.warmth_level =
      clamp_level(
          100.0 *
          warmth *
          (0.80 +
           0.20 *
               affinity_hidden));

  output.directness_level =
      clamp_level(
          100.0 *
          directness *
          (0.90 +
           0.10 *
               context_confidence));

  output.charm_level =
      clamp_level(
          100.0 *
          charm *
          confidence_gain *
          (0.70 +
           0.30 *
               affinity_hidden));

  output.playfulness_level =
      clamp_level(
          100.0 *
          playfulness *
          confidence_gain *
          (0.70 +
           0.30 *
               playful_hidden));

  output.flirt_level =
      input.adult_flirt_opt_in
      ? clamp_level(
            100.0 *
            flirt *
            confidence_gain *
            (0.60 +
             0.40 *
                 affinity_hidden))
      : 0.0;

  if (input.critical_guidance) {
    output.humor_level = 0.0;
    output.charm_level = 0.0;
    output.playfulness_level = 0.0;
    output.flirt_level = 0.0;

    output.warmth_level =
        std::max(
            output.warmth_level,
            70.0);

    output.directness_level =
        std::max(
            output.directness_level,
            90.0);
  }

  return output;
}

}  // namespace routing::core::intelligence
