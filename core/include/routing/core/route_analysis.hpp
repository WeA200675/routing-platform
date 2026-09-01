#pragma once

#include <cstddef>
#include <vector>

#include "routing/core/model.hpp"

namespace routing::core {

struct RouteAnalysis {
  // Distance represented by all supplied StreetSegments.
  double analyzed_distance_m = 0.0;

  // Road hierarchy.
  double major_road_distance_m = 0.0;
  double minor_road_distance_m = 0.0;
  double unknown_road_class_distance_m = 0.0;

  // Speed limits.
  double speed_30_or_lower_distance_m = 0.0;
  double unknown_speed_limit_distance_m = 0.0;

  // Geometry / driving character.
  double strongly_curvy_distance_m = 0.0;
  double unknown_curvature_distance_m = 0.0;

  double serpentine_distance_m = 0.0;
  double unknown_serpentine_distance_m = 0.0;

  // Urban context.
  double urban_distance_m = 0.0;
  double unknown_urban_distance_m = 0.0;

  // General data quality.
  double unknown_confidence_distance_m = 0.0;

  std::size_t segment_count = 0;
};

[[nodiscard]] RouteAnalysis analyze_route_segments(
    const std::vector<StreetSegment>& segments);

}  // namespace routing::core