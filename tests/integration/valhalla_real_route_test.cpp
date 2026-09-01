#include <cmath>
#include <cstdlib>
#include <fstream>
#include <iostream>
#include <sstream>
#include <string>
#include <algorithm>

#include "routing/adapters/valhalla/valhalla_routing_engine.hpp"

namespace {

int fail(const std::string& message) {
  std::cerr << "FAIL: " << message << '\n';
  return 1;
}

}  // namespace

int main() {
  const char* config_path =
      std::getenv("ROUTING_PLATFORM_VALHALLA_TEST_CONFIG");

  if (config_path == nullptr || std::string(config_path).empty()) {
    std::cout
        << "SKIP: ROUTING_PLATFORM_VALHALLA_TEST_CONFIG is not set.\n";
    return 77;
  }

  std::ifstream config_file(config_path);

  if (!config_file) {
    return fail(
        std::string("Could not open Valhalla config: ") +
        config_path);
  }

  std::ostringstream config_buffer;
  config_buffer << config_file.rdbuf();

  routing::adapters::valhalla::ValhallaRoutingEngine engine(
      {config_buffer.str()});

  if (!engine.ready()) {
    return fail("ValhallaRoutingEngine is not ready.");
  }

  routing::core::RouteRequest request;

  request.origin = {
      47.1410,
      9.5209,
  };

  request.destination = {
      47.1660,
      9.5100,
  };

  request.costing_profile = "auto";

  const auto result = engine.route(request);

  if (!result.success) {
    return fail(
        "Routing failed: " +
        result.error_code +
        " - " +
        result.error_message);
  }

  if (result.routes.size() != 1) {
    return fail(
        "Expected exactly one route, got " +
        std::to_string(result.routes.size()));
  }

  const auto& route = result.routes.front();

  if (route.engine_name != "valhalla") {
    return fail(
        "Unexpected routing engine: " +
        route.engine_name);
  }

  // Our current Liechtenstein fixture produces approximately:
  //
  //   distance = 3174 m
  //   duration = 242.149 s
  //
  // This is an integration test, not yet a frozen routing
  // regression test, therefore we deliberately allow tolerances.

  constexpr double expected_distance_m = 3174.0;
  constexpr double distance_tolerance_m = 150.0;

  constexpr double expected_duration_s = 242.149;
  constexpr double duration_tolerance_s = 30.0;

  if (std::abs(
          route.distance_m -
          expected_distance_m) >
      distance_tolerance_m) {
    return fail(
        "Unexpected route distance: " +
        std::to_string(route.distance_m) +
        " m");
  }

  if (std::abs(
          route.duration_s -
          expected_duration_s) >
      duration_tolerance_s) {
    return fail(
        "Unexpected route duration: " +
        std::to_string(route.duration_s) +
        " s");
  }

  if (route.geometry.size() < 2) {
  return fail(
      "Expected decoded route geometry, got " +
      std::to_string(route.geometry.size()) +
      " points");
}

if (route.maneuvers.empty()) {
  return fail("Expected at least one route maneuver.");
}

const bool has_instruction =
    std::any_of(
        route.maneuvers.begin(),
        route.maneuvers.end(),
        [](const routing::core::RouteManeuver& maneuver) {
          return !maneuver.instruction.empty();
        });

if (!has_instruction) {
  return fail("Expected at least one maneuver instruction.");
}

const bool has_street_name =
    std::any_of(
        route.maneuvers.begin(),
        route.maneuvers.end(),
        [](const routing::core::RouteManeuver& maneuver) {
          return !maneuver.street_names.empty();
        });

if (!has_street_name) {
  return fail("Expected at least one maneuver street name.");
}

const auto has_street =
    [&route](const std::string& expected_name) {
      for (const auto& maneuver : route.maneuvers) {
        for (const auto& street_name : maneuver.street_names) {
          if (street_name == expected_name) {
            return true;
          }
        }
      }

      return false;
    };

if (!has_street("Adlerkreisel")) {
  return fail(
      "Expected reference street name: Adlerkreisel.");
}

if (!has_street("Herrengasse")) {
  return fail(
      "Expected reference street name: Herrengasse.");
}

if (!has_street("Schulgass")) {
  return fail(
      "Expected reference street name: Schulgass.");
}

const bool has_engine_type =
    std::any_of(
        route.maneuvers.begin(),
        route.maneuvers.end(),
        [](const routing::core::RouteManeuver& maneuver) {
          return maneuver.engine_type.has_value();
        });

if (!has_engine_type) {
  return fail("Expected preserved Valhalla maneuver types.");
}

  // Multi-leg regression:
  // Maneuver shape indices from individual Valhalla legs must be
  // translated into indices of our combined RoutePath geometry.
  routing::core::RouteRequest via_request = request;

  via_request.via_points.push_back({
      47.1530,
      9.5150,
  });

  const auto via_result = engine.route(via_request);

  if (!via_result.success) {
    return fail(
        "Multi-leg routing failed: " +
        via_result.error_code +
        " - " +
        via_result.error_message);
  }

  if (via_result.routes.size() != 1) {
    return fail(
        "Expected exactly one multi-leg route, got " +
        std::to_string(via_result.routes.size()));
  }

  const auto& via_route = via_result.routes.front();

  if (via_route.geometry.size() < 3) {
    return fail(
        "Expected multi-leg route geometry.");
  }

  if (via_route.maneuvers.empty()) {
    return fail(
        "Expected multi-leg route maneuvers.");
  }

  std::size_t max_end_shape_index = 0;

  for (const auto& maneuver : via_route.maneuvers) {
    if (maneuver.begin_shape_index >
        maneuver.end_shape_index) {
      return fail(
          "Maneuver begin shape index exceeds end shape index.");
    }

    if (maneuver.end_shape_index >=
        via_route.geometry.size()) {
      return fail(
          "Maneuver shape index exceeds combined route geometry.");
    }

    max_end_shape_index =
        std::max(
            max_end_shape_index,
            maneuver.end_shape_index);
  }

  if (max_end_shape_index !=
      via_route.geometry.size() - 1) {
    return fail(
        "Multi-leg maneuver indices do not span the combined geometry.");
  }

  std::cout
      << "PASS: real Valhalla route\n"
      << "  engine:   "
      << route.engine_name
      << " "
      << route.engine_version
      << '\n'
      << "  distance: "
      << route.distance_m
      << " m\n"
      << "  duration: "
      << route.duration_s
      << " s\n"
      << "  geometry: "
      << route.geometry.size()
      << " points\n"
      << "  maneuvers: "
      << route.maneuvers.size()
      << '\n';

  return 0;
}