#pragma once

#include <cstdint>
#include <string_view>

#include "routing/core/evaluation/route_evaluation.hpp"

namespace routing::core::testing {

enum class RouteMetric : std::uint8_t {
  MajorRoadShare = 0,
  MinorRoadShare,
  Speed30OrLowerShare,
  StronglyCurvyShare,
  SerpentineShare,
  UrbanShare,
  SteepGradientShare,

  KnownRoadClassCoverage,
  KnownSpeedLimitCoverage,
  KnownCurvatureCoverage,
  KnownSerpentineCoverage,
  KnownUrbanCoverage,
  KnownGradientCoverage,

  UnknownConfidenceShare,
};

struct RouteMetricValue {
  bool available = false;

  // Normalized 0..1.
  double value = 0.0;

  // Fraction of analyzed route distance for which the source
  // attribute needed by this metric is known.
  //
  // For a coverage metric itself this is 1.0.
  double known_coverage = 0.0;
};

[[nodiscard]]
std::string_view route_metric_key(
    RouteMetric metric);

[[nodiscard]]
RouteMetricValue measure_route_metric(
    const evaluation::RouteEvaluation& evaluation,
    RouteMetric metric);

}  // namespace routing::core::testing
