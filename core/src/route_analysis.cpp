#include "routing/core/route_analysis.hpp"

#include "routing/core/road_analysis.hpp"

namespace routing::core {

RouteAnalysis analyze_route_segments(
    const std::vector<StreetSegment>& segments) {
  RouteAnalysis analysis;
  analysis.segment_count = segments.size();

  for (const auto& segment : segments) {
    const double length_m = segment.length_m;
    analysis.analyzed_distance_m += length_m;

    if (segment.road_class == RoadClass::Unknown) {
      analysis.unknown_road_class_distance_m += length_m;
    } else if (is_major_road(segment.road_class)) {
      analysis.major_road_distance_m += length_m;
    } else if (is_minor_road(segment.road_class)) {
      analysis.minor_road_distance_m += length_m;
    }

    switch (classify_speed_30_or_lower(segment)) {
      case MatchState::Yes:
        analysis.speed_30_or_lower_distance_m += length_m;
        break;
      case MatchState::Unknown:
        analysis.unknown_speed_limit_distance_m += length_m;
        break;
      case MatchState::No:
        break;
    }

    switch (classify_strongly_curvy(segment)) {
      case MatchState::Yes:
        analysis.strongly_curvy_distance_m += length_m;
        break;
      case MatchState::Unknown:
        analysis.unknown_curvature_distance_m += length_m;
        break;
      case MatchState::No:
        break;
    }

    switch (classify_serpentine(segment)) {
      case MatchState::Yes:
        analysis.serpentine_distance_m += length_m;
        break;
      case MatchState::Unknown:
        analysis.unknown_serpentine_distance_m += length_m;
        break;
      case MatchState::No:
        break;
    }

    switch (classify_urban(segment)) {
      case MatchState::Yes:
        analysis.urban_distance_m += length_m;
        break;
      case MatchState::Unknown:
        analysis.unknown_urban_distance_m += length_m;
        break;
      case MatchState::No:
        break;
    }

    if (!segment.data_confidence.has_value()) {
      analysis.unknown_confidence_distance_m += length_m;
    }
  }

  return analysis;
}

}  // namespace routing::core