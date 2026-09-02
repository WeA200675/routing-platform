#include <cassert>
#include <cmath>
#include <iostream>
#include <vector>

#include "routing/core/route_analysis.hpp"

namespace {

bool nearly_equal(const double a, const double b, const double epsilon = 1e-9) {
  return std::abs(a - b) <= epsilon;
}

}  // namespace

int main() {
  using routing::core::FunctionalRoadClass;
  using routing::core::RoadNetworkClass;
  using routing::core::StreetSegment;
  using routing::core::analyze_route_segments;

  std::vector<StreetSegment> segments;

  StreetSegment major;
  major.id = "major";
  major.length_m = 1000.0;
  major.functional_road_class = FunctionalRoadClass::Primary;
  major.road_network_class = RoadNetworkClass::FederalRoad;
  major.speed_limit_kmh = 100.0;
  major.curvature_score = 0.20;
  major.serpentine_score = 0.10;
  major.urban_score = 0.10;
  major.data_confidence = 0.95;
  segments.push_back(major);

  StreetSegment urban_30;
  urban_30.id = "urban-30";
  urban_30.length_m = 500.0;
  urban_30.functional_road_class = FunctionalRoadClass::Residential;
  urban_30.road_network_class = RoadNetworkClass::MunicipalRoad;
  urban_30.speed_limit_kmh = 30.0;
  urban_30.curvature_score = 0.80;
  urban_30.serpentine_score = 0.70;
  urban_30.urban_score = 0.90;
  urban_30.data_confidence = 0.85;
  segments.push_back(urban_30);

  StreetSegment unknown;
  unknown.id = "unknown";
  unknown.length_m = 250.0;
  unknown.functional_road_class = FunctionalRoadClass::Unknown;
  segments.push_back(unknown);

  const auto analysis = analyze_route_segments(segments);

  assert(analysis.segment_count == 3);

  assert(nearly_equal(analysis.analyzed_distance_m, 1750.0));

  assert(nearly_equal(analysis.major_road_distance_m, 1000.0));
  assert(nearly_equal(analysis.minor_road_distance_m, 500.0));
  assert(nearly_equal(analysis.unknown_road_class_distance_m, 250.0));

  assert(nearly_equal(analysis.speed_30_or_lower_distance_m, 500.0));
  assert(nearly_equal(analysis.unknown_speed_limit_distance_m, 250.0));

  assert(nearly_equal(analysis.strongly_curvy_distance_m, 500.0));
  assert(nearly_equal(analysis.unknown_curvature_distance_m, 250.0));

  assert(nearly_equal(analysis.serpentine_distance_m, 500.0));
  assert(nearly_equal(analysis.unknown_serpentine_distance_m, 250.0));

  assert(nearly_equal(analysis.urban_distance_m, 500.0));
  assert(nearly_equal(analysis.unknown_urban_distance_m, 250.0));

  assert(nearly_equal(analysis.unknown_confidence_distance_m, 250.0));

  std::cout << "Route analysis tests passed\n";
  return 0;
}