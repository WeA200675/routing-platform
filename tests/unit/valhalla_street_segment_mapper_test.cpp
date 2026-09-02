#include <cassert>
#include <cmath>
#include <iostream>
#include <stdexcept>

#include "routing/adapters/valhalla/valhalla_street_segment_mapper.hpp"
#include "routing/core/route_analysis.hpp"

namespace {

bool nearly_equal(
    const double a,
    const double b,
    const double epsilon = 1e-9) {
  return std::abs(a - b) <= epsilon;
}

}  // namespace

int main() {
  using routing::adapters::valhalla::
      map_valhalla_edge_to_street_segment;
  using routing::adapters::valhalla::
      map_valhalla_functional_road_class;
  using routing::adapters::valhalla::detail::
      ValhallaEdgeAttributes;
  using routing::core::FunctionalRoadClass;
  using routing::core::RoadNetworkClass;

  assert(
      map_valhalla_functional_road_class("motorway") ==
      FunctionalRoadClass::Motorway);

  assert(
      map_valhalla_functional_road_class("trunk") ==
      FunctionalRoadClass::Trunk);

  assert(
      map_valhalla_functional_road_class("primary") ==
      FunctionalRoadClass::Primary);

  assert(
      map_valhalla_functional_road_class("secondary") ==
      FunctionalRoadClass::Secondary);

  assert(
      map_valhalla_functional_road_class("tertiary") ==
      FunctionalRoadClass::Tertiary);

  assert(
      map_valhalla_functional_road_class("unclassified") ==
      FunctionalRoadClass::Unclassified);

  assert(
      map_valhalla_functional_road_class("residential") ==
      FunctionalRoadClass::Residential);

  assert(
      map_valhalla_functional_road_class("service_other") ==
      FunctionalRoadClass::Service);

  assert(
      map_valhalla_functional_road_class("null") ==
      FunctionalRoadClass::Unknown);

  assert(
      map_valhalla_functional_road_class("") ==
      FunctionalRoadClass::Unknown);

  assert(
      map_valhalla_functional_road_class("future_new_class") ==
      FunctionalRoadClass::Unknown);

  ValhallaEdgeAttributes edge;
  edge.id = 123456;
  edge.way_id = 987654;
  edge.length_m = 1250.0;
  edge.road_class = "primary";
  edge.speed_kmh = 78.0;
  edge.speed_limit_kmh = 100.0;
  edge.surface = "paved";
  edge.curvature = 3;
  edge.is_urban = true;
  edge.lane_count = 2;
  edge.weighted_grade = 1.5;

  const auto segment =
      map_valhalla_edge_to_street_segment(edge);

  assert(segment.id == "valhalla:123456");
  assert(nearly_equal(segment.length_m, 1250.0));

  assert(
      segment.functional_road_class ==
      FunctionalRoadClass::Primary);

  assert(
      segment.road_network_class ==
      RoadNetworkClass::Unknown);

  assert(segment.speed_limit_kmh.has_value());
  assert(
      nearly_equal(*segment.speed_limit_kmh, 100.0));

  assert(segment.practical_speed_kmh.has_value());
  assert(
      nearly_equal(*segment.practical_speed_kmh, 78.0));

  assert(segment.urban_score.has_value());
  assert(nearly_equal(*segment.urban_score, 1.0));

  // Raw Valhalla curvature must not silently become our
  // normalized 0..1 curvature score.
  assert(!segment.curvature_score.has_value());

  ValhallaEdgeAttributes rural;
  rural.id = 2;
  rural.length_m = 500.0;
  rural.road_class = "residential";
  rural.is_urban = false;

  const auto rural_segment =
      map_valhalla_edge_to_street_segment(rural);

  assert(
      rural_segment.functional_road_class ==
      FunctionalRoadClass::Residential);

  assert(rural_segment.urban_score.has_value());
  assert(nearly_equal(*rural_segment.urban_score, 0.0));

  ValhallaEdgeAttributes unlimited;
  unlimited.id = 3;
  unlimited.length_m = 1000.0;
  unlimited.road_class = "motorway";
  unlimited.speed_limit_unlimited = true;

  const auto unlimited_segment =
      map_valhalla_edge_to_street_segment(unlimited);

  // Unlimited and unknown must not be converted to a fake
  // numeric speed limit, but they must remain distinguishable.
  assert(!unlimited_segment.speed_limit_kmh.has_value());
  assert(unlimited_segment.speed_limit_unlimited);

  const auto unlimited_analysis =
      routing::core::analyze_route_segments(
          {unlimited_segment});

  assert(nearly_equal(
      unlimited_analysis.speed_30_or_lower_distance_m,
      0.0));

  assert(nearly_equal(
      unlimited_analysis.unknown_speed_limit_distance_m,
      0.0));

  bool missing_id_threw = false;

  try {
    ValhallaEdgeAttributes missing_id;
    missing_id.length_m = 100.0;

    (void)map_valhalla_edge_to_street_segment(
        missing_id);
  } catch (const std::invalid_argument&) {
    missing_id_threw = true;
  }

  assert(missing_id_threw);

  bool missing_length_threw = false;

  try {
    ValhallaEdgeAttributes missing_length;
    missing_length.id = 99;

    (void)map_valhalla_edge_to_street_segment(
        missing_length);
  } catch (const std::invalid_argument&) {
    missing_length_threw = true;
  }

  assert(missing_length_threw);

  std::cout
      << "Valhalla street segment mapper tests passed\n";

  return 0;
}
