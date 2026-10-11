#include "routing/adapters/valhalla/detail/valhalla_trace_request.hpp"

#include <iomanip>
#include <sstream>
#include <stdexcept>
#include <string>

namespace routing::adapters::valhalla::detail {

namespace {

void validate_point(const routing::core::GeoPoint& point) {
  if (point.latitude < -90.0 ||
      point.latitude > 90.0) {
    throw std::invalid_argument(
        "Latitude must be between -90 and 90 degrees.");
  }

  if (point.longitude < -180.0 ||
      point.longitude > 180.0) {
    throw std::invalid_argument(
        "Longitude must be between -180 and 180 degrees.");
  }
}

void validate_costing(const std::string_view costing) {
  if (costing.empty()) {
    throw std::invalid_argument(
        "Valhalla costing profile must not be empty.");
  }

  if (costing.find_first_not_of(
          "abcdefghijklmnopqrstuvwxyz"
          "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
          "0123456789_-") !=
      std::string_view::npos) {
    throw std::invalid_argument(
        "Invalid Valhalla costing profile.");
  }
}

void append_point(
    std::ostringstream& json,
    const routing::core::GeoPoint& point) {
  validate_point(point);

  json
      << "{\"lat\":"
      << std::setprecision(15)
      << point.latitude
      << ",\"lon\":"
      << std::setprecision(15)
      << point.longitude
      << "}";
}

}  // namespace

std::string build_trace_attributes_request(
    const std::vector<routing::core::GeoPoint>& geometry,
    const std::string_view costing_profile) {
  if (geometry.size() < 2) {
    throw std::invalid_argument(
        "Trace attributes requires at least two geometry points.");
  }

  validate_costing(costing_profile);

  std::ostringstream json;

  json << "{\"shape\":[";

  for (std::size_t i = 0; i < geometry.size(); ++i) {
    if (i != 0) {
      json << ",";
    }

    append_point(json, geometry[i]);
  }

  json
      << "],\"costing\":\""
      << costing_profile
      << "\",\"units\":\"kilometers\""
      << ",\"shape_match\":\"walk_or_snap\""
      << ",\"filters\":{"
      << "\"action\":\"include\","
      << "\"attributes\":["

      << "\"edge.id\","
      << "\"edge.way_id\","
      << "\"edge.names\","
      << "\"edge.length\","
      << "\"edge.road_class\","
      << "\"edge.use\","
      << "\"edge.speed\","
      << "\"edge.speed_limit\","
      << "\"edge.surface\","
      << "\"edge.curvature\","
      << "\"edge.is_urban\","
      << "\"edge.lane_count\","
      << "\"edge.weighted_grade\","
      << "\"edge.max_upward_grade\","
      << "\"edge.max_downward_grade\","
      << "\"edge.begin_shape_index\","
      << "\"edge.end_shape_index\""

      << "]}}";

  return json.str();
}

}  // namespace routing::adapters::valhalla::detail
