#include <cassert>
#include <stdexcept>
#include <string>
#include <vector>

#include "routing/adapters/valhalla/detail/valhalla_trace_request.hpp"

int main() {
  using routing::adapters::valhalla::detail::
      build_trace_attributes_request;
  using routing::core::GeoPoint;

  const std::vector<GeoPoint> geometry = {
      {47.1410, 9.5209},
      {47.1500, 9.5170},
      {47.1660, 9.5100},
  };

  const std::string json =
      build_trace_attributes_request(
          geometry,
          "auto");

  assert(
      json.find("\"shape_match\":\"walk_or_snap\"") !=
      std::string::npos);

  assert(
      json.find("\"costing\":\"auto\"") !=
      std::string::npos);

  assert(
      json.find("\"units\":\"kilometers\"") !=
      std::string::npos);

  assert(
      json.find("\"edge.id\"") !=
      std::string::npos);

  assert(
      json.find("\"edge.length\"") !=
      std::string::npos);

  assert(
      json.find("\"edge.road_class\"") !=
      std::string::npos);

  assert(
      json.find("\"edge.speed_limit\"") !=
      std::string::npos);

  assert(
      json.find("\"edge.begin_shape_index\"") !=
      std::string::npos);

  assert(
      json.find("\"edge.end_shape_index\"") !=
      std::string::npos);

  bool short_geometry_threw = false;

  try {
    (void)build_trace_attributes_request(
        {{47.1410, 9.5209}},
        "auto");
  } catch (const std::invalid_argument&) {
    short_geometry_threw = true;
  }

  assert(short_geometry_threw);

  bool bad_costing_threw = false;

  try {
    (void)build_trace_attributes_request(
        geometry,
        "auto\"bad");
  } catch (const std::invalid_argument&) {
    bad_costing_threw = true;
  }

  assert(bad_costing_threw);

  bool bad_coordinate_threw = false;

  try {
    (void)build_trace_attributes_request(
        {
            {91.0, 9.5209},
            {47.1660, 9.5100},
        },
        "auto");
  } catch (const std::invalid_argument&) {
    bad_coordinate_threw = true;
  }

  assert(bad_coordinate_threw);

  return 0;
}
