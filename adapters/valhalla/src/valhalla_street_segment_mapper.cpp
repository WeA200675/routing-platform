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

  // Functional Valhalla road classes must never be mistaken for
  // legal/network classes such as Bundesstraße or Landesstraße.
  segment.road_network_class =
      RoadNetworkClass::Unknown;

  if (edge.speed_limit_kmh.has_value()) {
    segment.speed_limit_kmh =
        *edge.speed_limit_kmh;
  }

  segment.speed_limit_unlimited =
      edge.speed_limit_unlimited;

  // edge.speed is Valhalla's routing speed, not the legal limit.
  if (edge.speed_kmh.has_value()) {
    segment.practical_speed_kmh =
        *edge.speed_kmh;
  }

  if (edge.is_urban.has_value()) {
    segment.urban_score =
        *edge.is_urban ? 1.0 : 0.0;
  }

  // Deliberately not mapped yet:
  // - curvature: raw Valhalla scale is not our normalized score
  // - grades: neutral normalization is not yet defined
  // - surface: neutral Street Model field is not yet present
  // - lane_count: neutral lane model is not yet present

  return segment;
}

}  // namespace routing::adapters::valhalla
