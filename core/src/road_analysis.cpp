#include "routing/core/road_analysis.hpp"

namespace routing::core {

bool is_major_road(
    const FunctionalRoadClass road_class) {
  switch (road_class) {
    case FunctionalRoadClass::Motorway:
    case FunctionalRoadClass::Trunk:
    case FunctionalRoadClass::Primary:
      return true;

    default:
      return false;
  }
}

bool is_minor_road(
    const FunctionalRoadClass road_class) {
  switch (road_class) {
    case FunctionalRoadClass::Residential:
    case FunctionalRoadClass::Service:
    case FunctionalRoadClass::Track:
      return true;

    default:
      return false;
  }
}

MatchState classify_speed_30_or_lower(
    const StreetSegment& segment) {
  if (!segment.speed_limit_kmh.has_value()) {
    return MatchState::Unknown;
  }

  return *segment.speed_limit_kmh <= 30.0
      ? MatchState::Yes
      : MatchState::No;
}

MatchState classify_strongly_curvy(
    const StreetSegment& segment,
    const double threshold) {
  if (!segment.curvature_score.has_value()) {
    return MatchState::Unknown;
  }

  return *segment.curvature_score >= threshold
      ? MatchState::Yes
      : MatchState::No;
}

MatchState classify_serpentine(
    const StreetSegment& segment,
    const double threshold) {
  if (!segment.serpentine_score.has_value()) {
    return MatchState::Unknown;
  }

  return *segment.serpentine_score >= threshold
      ? MatchState::Yes
      : MatchState::No;
}

MatchState classify_urban(
    const StreetSegment& segment,
    const double threshold) {
  if (!segment.urban_score.has_value()) {
    return MatchState::Unknown;
  }

  return *segment.urban_score >= threshold
      ? MatchState::Yes
      : MatchState::No;
}

bool is_speed_30_or_lower(
    const StreetSegment& segment) {
  return classify_speed_30_or_lower(segment) == MatchState::Yes;
}

bool is_strongly_curvy(
    const StreetSegment& segment,
    const double threshold) {
  return classify_strongly_curvy(segment, threshold) == MatchState::Yes;
}

bool is_serpentine(
    const StreetSegment& segment,
    const double threshold) {
  return classify_serpentine(segment, threshold) == MatchState::Yes;
}

bool is_urban(
    const StreetSegment& segment,
    const double threshold) {
  return classify_urban(segment, threshold) == MatchState::Yes;
}

}  // namespace routing::core