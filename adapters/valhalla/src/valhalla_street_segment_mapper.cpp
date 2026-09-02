#include "routing/adapters/valhalla/valhalla_street_segment_mapper.hpp"

#include <stdexcept>
#include <string>

namespace routing::adapters::valhalla {

routing::core::FunctionalRoadClass
map_valhalla_functional_road_class(
    const std::string_view road_class) {
  using routing::core::FunctionalRoadClass;

  if (road_class == "motorway") {
    return FunctionalRoadClass::Motorway;
  }
  if (road_class == "trunk") {
    return FunctionalRoadClass::Trunk;
  }
  if (road_class == "primary") {
    return FunctionalRoadClass::Primary;
  }
  if (road_class == "secondary") {
    return FunctionalRoadClass::Secondary;
  }
  if (road_class == "tertiary") {
    return FunctionalRoadClass::Tertiary;
  }
  if (road_class == "unclassified") {
    return FunctionalRoadClass::Unclassified;
  }
  if (road_class == "residential") {
    return FunctionalRoadClass::Residential;
  }
  if (road_class == "service_other") {
    return FunctionalRoadClass::Service;
  }

  return FunctionalRoadClass::Unknown;
}

routing::core::StreetSegment
map_valhalla_edge_to_street_segment(
    const detail::ValhallaEdgeAttributes& edge) {
  using routing::core::RoadNetworkClass;
  using routing::core::StreetSegment;

  if (!edge.id.has_value()) {
    throw std::invalid_argument(
        "Cannot map Valhalla edge without edge.id.");
  }

  if (!edge.length_m.has_value()) {
    throw std::invalid_argument(
        "Cannot map Valhalla edge without edge.length.");
  }

  StreetSegment segment;

  segment.id =
      "valhalla:" + std::to_string(*edge.id);

  segment.length_m = *edge.length_m;

  segment.functional_road_class =
      map_valhalla_functional_road_class(
          edge.road_class.value_or(""));

  // Valhalla road_class is a functional hierarchy and does
  // not tell us whether a road is a Bundes-, Landes- or
  // Kreisstraße.
  segment.road_network_class =
      RoadNetworkClass::Unknown;

  if (edge.speed_limit_kmh.has_value()) {
    segment.speed_limit_kmh =
        *edge.speed_limit_kmh;
  }

  // edge.speed is kept separate from the legal speed limit.
  if (edge.speed_kmh.has_value()) {
    segment.practical_speed_kmh =
        *edge.speed_kmh;
  }

  if (edge.is_urban.has_value()) {
    segment.urban_score =
        *edge.is_urban ? 1.0 : 0.0;
  }

  // Deliberately not mapped yet:
  // - curvature: Valhalla's raw scale is not our 0..1 score
  // - grades: normalization semantics are not defined yet
  // - surface: StreetSegment has no neutral surface field yet
  // - lane_count: StreetSegment has no lane model yet
  // - unlimited speed: the current core speed-limit model
  //   cannot represent it without conflating it with unknown

  return segment;
}

}  // namespace routing::adapters::valhalla
