#include <cmath>
#include <cstdlib>
#include <fstream>
#include <iostream>
#include <sstream>
#include <string>

#include "routing/adapters/valhalla/valhalla_routing_engine.hpp"
#include "routing/core/evaluation/route_evaluation.hpp"

namespace {

int fail(
    const std::string& message) {
  std::cerr
      << "FAIL: "
      << message
      << '\n';

  return 1;
}

bool nearly_equal(
    double left,
    double right,
    double tolerance = 0.01) {
  return std::abs(left - right) <= tolerance;
}

}  // namespace

int main() {
  const char* config_path =
      std::getenv(
          "ROUTING_PLATFORM_VALHALLA_TEST_CONFIG");

  if (config_path == nullptr ||
      std::string(config_path).empty()) {
    std::cout
        << "SKIP: ROUTING_PLATFORM_VALHALLA_TEST_CONFIG "
        << "is not set.\n";

    return 77;
  }

  std::ifstream config_file(
      config_path);

  if (!config_file) {
    return fail(
        std::string(
            "Could not open Valhalla config: ") +
        config_path);
  }

  std::ostringstream config_buffer;
  config_buffer << config_file.rdbuf();

  routing::adapters::valhalla::
      ValhallaRoutingEngine engine(
          {config_buffer.str()});

  if (!engine.ready()) {
    return fail(
        "ValhallaRoutingEngine is not ready.");
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

  const auto result =
      engine.route(request);

  if (!result.success) {
    return fail(
        "Routing failed: " +
        result.error_code +
        " - " +
        result.error_message);
  }

  if (result.routes.size() != 1) {
    return fail(
        "Expected exactly one route.");
  }

  const auto& route =
      result.routes.front();

  routing::core::VehicleProfile vehicle;
  routing::core::RuleSet rules;
  routing::core::RoutingContext context;

  const auto evaluation =
      routing::core::evaluation::
          evaluate_route(
              route,
              vehicle,
              rules,
              context);

  if (!evaluation.segment_data_available) {
    return fail(
        "Real route has no evaluation segment data.");
  }

  if (!evaluation.score_available) {
    return fail(
        "Real route has no CostEngine score.");
  }

  if (!evaluation.allowed) {
    return fail(
        "Reference route unexpectedly disallowed.");
  }

  if (!std::isfinite(
          evaluation.total_seconds_equivalent) ||
      evaluation.total_seconds_equivalent <= 0.0) {
    return fail(
        "Real route has invalid total score.");
  }

  if (evaluation.analysis.segment_count !=
      route.segments.size()) {
    return fail(
        "Evaluation segment count mismatch.");
  }

  const double functional_sum =
      evaluation.functional_roads.motorway_m +
      evaluation.functional_roads.trunk_m +
      evaluation.functional_roads.primary_m +
      evaluation.functional_roads.secondary_m +
      evaluation.functional_roads.tertiary_m +
      evaluation.functional_roads.unclassified_m +
      evaluation.functional_roads.residential_m +
      evaluation.functional_roads.service_m +
      evaluation.functional_roads.track_m +
      evaluation.functional_roads.unknown_m;

  if (!nearly_equal(
          functional_sum,
          evaluation.analysis.analyzed_distance_m)) {
    return fail(
        "Functional road distances do not cover "
        "the analyzed route.");
  }

  const double network_sum =
      evaluation.road_networks.federal_m +
      evaluation.road_networks.state_m +
      evaluation.road_networks.county_m +
      evaluation.road_networks.municipal_m +
      evaluation.road_networks.other_m +
      evaluation.road_networks.unknown_m;

  if (!nearly_equal(
          network_sum,
          evaluation.analysis.analyzed_distance_m)) {
    return fail(
        "Road-network distances do not cover "
        "the analyzed route.");
  }

  std::cout
      << "PASS: real Valhalla route evaluation\n"
      << "  route:       "
      << evaluation.route_id
      << '\n'
      << "  distance:    "
      << evaluation.reported_distance_m
      << " m\n"
      << "  duration:    "
      << evaluation.reported_duration_s
      << " s\n"
      << "  score:       "
      << evaluation.total_seconds_equivalent
      << " s-equivalent\n"
      << "  expected:    "
      << evaluation.expected_travel_seconds
      << " s\n"
      << "  uncertainty: "
      << evaluation.uncertainty_seconds
      << " s-equivalent\n"
      << "  major:       "
      << evaluation.analysis.major_road_distance_m
      << " m\n"
      << "  minor:       "
      << evaluation.analysis.minor_road_distance_m
      << " m\n"
      << "  residential: "
      << evaluation.functional_roads.residential_m
      << " m\n"
      << "  <=30 km/h:   "
      << evaluation.analysis.speed_30_or_lower_distance_m
      << " m\n"
      << "  federal:     "
      << evaluation.road_networks.federal_m
      << " m\n"
      << "  gradient>=8: "
      << evaluation.steep_gradient_distance_m
      << " m\n"
      << "  urban:       "
      << evaluation.analysis.urban_distance_m
      << " m\n";

  return 0;
}
