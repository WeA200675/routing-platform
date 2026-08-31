#pragma once

#include <algorithm>

namespace routing::core {

struct ExposurePenaltyInput {
  double severity{0.0};      // normalized [0,1]
  double exposure{0.0};      // e.g. km, seconds, normalized exposure
  double weight{0.0};        // seconds-equivalent per exposure unit
  double exponent{1.0};      // nonlinear severity shaping
};

inline double ExposurePenaltySeconds(const ExposurePenaltyInput& input) {
  const double severity = std::clamp(input.severity, 0.0, 1.0);
  const double exposure = std::max(0.0, input.exposure);
  const double exponent = std::max(1.0, input.exponent);
  const double weight = std::max(0.0, input.weight);

  double shaped = severity;
  for (int i = 1; i < static_cast<int>(exponent); ++i) {
    shaped *= severity;
  }
  // Fractional exponents are intentionally not supported in Foundation 0.1;
  // the public contract stays deterministic and dependency-free.
  return shaped * exposure * weight;
}

inline bool MeetsShortcutThreshold(double seconds_saved,
                                   double minimum_gain_seconds) {
  return seconds_saved >= std::max(0.0, minimum_gain_seconds);
}

inline bool WithinComfortBudget(double candidate_time_seconds,
                                double baseline_time_seconds,
                                double max_extra_seconds) {
  return candidate_time_seconds <=
         baseline_time_seconds + std::max(0.0, max_extra_seconds);
}

}  // namespace routing::core
