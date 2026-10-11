#include <cmath>
#include <cstdlib>
#include <fstream>
#include <iostream>
#include <sstream>
#include <string>
#include <unordered_set>

#include "routing/adapters/valhalla/valhalla_routing_engine.hpp"
#include "routing/core/evaluation/candidate_comparison.hpp"
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
      47.2410,
      9.5310,
  };

  request.costing_profile = "auto";

  // Vaduz -> Ruggell is our real multi-candidate fixture.
  // With the current Liechtenstein tiles and Valhalla 3.8.3,
  // requesting three alternates reproducibly yields multiple paths.
  // Valhalla may legally return fewer than requested, therefore the
  // assertion below requires only at least one genuine alternative.
  request.alternatives = 3;

  const auto result =
      engine.route(request);

  if (!result.success) {
    return fail(
        "Alternative routing failed: " +
        result.error_code +
        " - " +
        result.error_message);
  }

  if (result.routes.size() < 2) {
    return fail(
        "Expected main route plus at least one "
        "real Valhalla alternate, got " +
        std::to_string(
            result.routes.size()) +
        " route(s).");
  }

  std::unordered_set<std::string>
      route_ids;

  routing::core::VehicleProfile vehicle;
  routing::core::RuleSet rules;
  routing::core::RoutingContext context;

  const auto reference =
      routing::core::evaluation::
          evaluate_route(
              result.routes.front(),
              vehicle,
              rules,
              context);

  if (!reference.segment_data_available ||
      !reference.score_available) {
    return fail(
        "Reference route is not fully evaluable.");
  }

  std::cout
      << "PASS: real Valhalla alternatives\n"
      << "  routes: "
      << result.routes.size()
      << '\n';

  bool found_distinct_segment_path = false;

  for (std::size_t i = 0;
       i < result.routes.size();
       ++i) {
    const auto& route =
        result.routes[i];

    const std::string expected_id =
        "valhalla-" +
        std::to_string(i);

    if (route.route_id !=
        expected_id) {
      return fail(
          "Unexpected route id: " +
          route.route_id +
          ", expected " +
          expected_id);
    }

    if (!route_ids.insert(
            route.route_id).second) {
      return fail(
          "Duplicate route id: " +
          route.route_id);
    }

    if (route.family !=
        request.family) {
      return fail(
          "Candidate family was not preserved.");
    }

    if (route.engine_name !=
        "valhalla") {
      return fail(
          "Unexpected routing engine.");
    }

    if (route.geometry.size() < 2) {
      return fail(
          route.route_id +
          " has no usable geometry.");
    }

    if (route.maneuvers.empty()) {
      return fail(
          route.route_id +
          " has no maneuvers.");
    }

    if (route.segments.empty()) {
      return fail(
          route.route_id +
          " was not enriched with segments.");
    }

    if (route.segment_ids.size() !=
        route.segments.size()) {
      return fail(
          route.route_id +
          " segment id count mismatch.");
    }

    for (std::size_t segment_index = 0;
         segment_index <
             route.segments.size();
         ++segment_index) {
      if (route.segment_ids[
              segment_index] !=
          route.segments[
              segment_index].id) {
        return fail(
            route.route_id +
            " segment order mismatch.");
      }
    }

    const auto evaluation =
        routing::core::evaluation::
            evaluate_route(
                route,
                vehicle,
                rules,
                context);

    if (!evaluation.segment_data_available ||
        !evaluation.score_available) {
      return fail(
          route.route_id +
          " is not fully evaluable.");
    }

    if (!std::isfinite(
            evaluation.total_seconds_equivalent) ||
        evaluation.total_seconds_equivalent <= 0.0) {
      return fail(
          route.route_id +
          " has invalid total score.");
    }

    std::cout
        << "  "
        << route.route_id
        << ": "
        << route.distance_m
        << " m, "
        << route.duration_s
        << " s, score "
        << evaluation.total_seconds_equivalent
        << ", major "
        << evaluation.analysis
               .major_road_distance_m
        << " m, minor "
        << evaluation.analysis
               .minor_road_distance_m
        << " m, <=30 "
        << evaluation.analysis
               .speed_30_or_lower_distance_m
        << " m\n";

    if (i == 0) {
      continue;
    }

    if (route.segment_ids !=
        result.routes.front().segment_ids) {
      found_distinct_segment_path = true;
    }

    const auto comparison =
        routing::core::evaluation::
            compare_candidates(
                reference,
                evaluation);

    if (!comparison.segment_metrics_comparable) {
      return fail(
          route.route_id +
          " segment metrics are not comparable.");
    }

    if (!comparison.score_comparable) {
      return fail(
          route.route_id +
          " score is not comparable.");
    }

    if (!comparison
             .score_delta_seconds_equivalent
             .has_value()) {
      return fail(
          route.route_id +
          " has no score delta.");
    }

    std::cout
        << "    vs valhalla-0:"
        << " duration "
        << comparison.duration_delta_s
        << " s,"
        << " distance "
        << comparison.distance_delta_m
        << " m,"
        << " major "
        << comparison.major_road_delta_m
        << " m,"
        << " minor "
        << comparison.minor_road_delta_m
        << " m,"
        << " <=30 "
        << comparison
               .speed_30_or_lower_delta_m
        << " m,"
        << " score "
        << *comparison
               .score_delta_seconds_equivalent
        << " s-equivalent\n";
  }

  if (!found_distinct_segment_path) {
    return fail(
        "Valhalla returned alternatives, but enriched "
        "segment paths are identical.");
  }

  return 0;
}
