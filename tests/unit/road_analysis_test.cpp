#include <cassert>
#include <iostream>

#include "routing/core/road_analysis.hpp"

int main() {
  using routing::core::RoadClass;
  using routing::core::StreetSegment;
  using routing::core::is_major_road;
  using routing::core::is_minor_road;
  using routing::core::is_serpentine;
  using routing::core::is_speed_30_or_lower;
  using routing::core::is_strongly_curvy;
  using routing::core::is_urban;
  using routing::core::MatchState;
  using routing::core::classify_serpentine;
  using routing::core::classify_speed_30_or_lower;
  using routing::core::classify_strongly_curvy;
  using routing::core::classify_urban;

  // Road hierarchy.
  assert(is_major_road(RoadClass::Motorway));
  assert(is_major_road(RoadClass::FederalRoad));
  assert(is_major_road(RoadClass::StateRoad));
  assert(is_major_road(RoadClass::Primary));

  assert(!is_major_road(RoadClass::Residential));
  assert(!is_major_road(RoadClass::Service));
  assert(!is_major_road(RoadClass::Track));

  assert(is_minor_road(RoadClass::Residential));
  assert(is_minor_road(RoadClass::Service));
  assert(is_minor_road(RoadClass::Track));

  assert(!is_minor_road(RoadClass::Motorway));
  assert(!is_minor_road(RoadClass::FederalRoad));

  StreetSegment segment;

  // Unknown values remain explicitly unknown.
  assert(classify_speed_30_or_lower(segment) == MatchState::Unknown);
  assert(classify_strongly_curvy(segment) == MatchState::Unknown);
  assert(classify_serpentine(segment) == MatchState::Unknown);
  assert(classify_urban(segment) == MatchState::Unknown);

  // Convenience bool helpers only return true for MatchState::Yes.
  assert(!is_speed_30_or_lower(segment));
  assert(!is_strongly_curvy(segment));
  assert(!is_serpentine(segment));
  assert(!is_urban(segment));

  // Speed limit.
  segment.speed_limit_kmh = 30.0;
  assert(is_speed_30_or_lower(segment));

  segment.speed_limit_kmh = 50.0;
  assert(!is_speed_30_or_lower(segment));

  // Curvature.
  segment.curvature_score = 0.70;
  assert(is_strongly_curvy(segment));

  segment.curvature_score = 0.69;
  assert(!is_strongly_curvy(segment));

  // Serpentines.
  segment.serpentine_score = 0.60;
  assert(is_serpentine(segment));

  segment.serpentine_score = 0.59;
  assert(!is_serpentine(segment));

  // Urban context.
  segment.urban_score = 0.60;
  assert(is_urban(segment));

  segment.urban_score = 0.59;
  assert(!is_urban(segment));

  std::cout << "Road analysis tests passed\n";
  return 0;
}