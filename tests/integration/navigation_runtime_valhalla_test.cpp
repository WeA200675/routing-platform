#include <cmath>
#include <cstdlib>
#include <fstream>
#include <iostream>
#include <sstream>
#include <string>

#include "routing/adapters/valhalla/valhalla_routing_engine.hpp"
#include "routing/core/navigation/navigation_runtime.hpp"

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
  using namespace routing::core;
  using namespace routing::core::navigation;


  const char* config_path =
      std::getenv(
          "ROUTING_PLATFORM_VALHALLA_TEST_CONFIG");

  if (config_path == nullptr ||
      std::string(config_path).empty()) {
    std::cout
        << "SKIP: ROUTING_PLATFORM_VALHALLA_TEST_CONFIG is not set.\n";

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


  std::ostringstream buffer;

  buffer
      << config_file.rdbuf();


  routing::adapters::valhalla::
      ValhallaRoutingEngine engine(
          {buffer.str()});

  if (!engine.ready()) {
    return fail(
        "ValhallaRoutingEngine is not ready.");
  }


  RouteRequest request;

  request.origin = {
      47.1410,
      9.5209,
  };

  request.destination = {
      47.1660,
      9.5100,
  };

  request.family =
      CandidateFamily::ProfileOptimal;

  request.costing_profile =
      "auto";


  const auto routed =
      engine.route(
          request);

  if (!routed.success) {
    return fail(
        "Routing failed: " +
        routed.error_code +
        " - " +
        routed.error_message);
  }


  if (routed.routes.size() != 1) {
    return fail(
        "Expected exactly one route.");
  }


  const auto& route =
      routed.routes.front();

  if (route.geometry.size() < 2) {
    return fail(
        "Expected Valhalla route geometry.");
  }

  if (route.maneuvers.empty()) {
    return fail(
        "Expected Valhalla route maneuvers.");
  }


  NavigationSession navigation(
      "navigation:valhalla:1",
      route);


  const auto preview =
      navigation.snapshot();

  if (preview.route_preview == nullptr) {
    return fail(
        "Navigation preview is missing.");
  }

  if (preview.route_preview->route_id !=
      route.route_id) {
    return fail(
        "Navigation preview route id mismatch.");
  }

  if (preview.route_preview->geometry.size() !=
      route.geometry.size()) {
    return fail(
        "Navigation preview geometry size mismatch.");
  }

  if (preview.route_preview->maneuvers.size() !=
      route.maneuvers.size()) {
    return fail(
        "Navigation preview maneuver size mismatch.");
  }

  if (preview.route_preview->engine_name !=
      "valhalla") {
    return fail(
        "Navigation preview lost Valhalla engine identity.");
  }


  const auto started =
      navigation.start();

  if (started.state !=
      NavigationSessionState::Navigating) {
    return fail(
        "Navigation session did not start.");
  }


  const std::size_t segment_count =
      route.geometry.size() -
      1;

  const std::size_t final_segment =
      segment_count -
      1;

  const std::size_t middle_segment =
      final_segment /
      2;


  NavigationProgressUpdate middle;

  middle.shape_segment_index =
      middle_segment;

  middle.segment_fraction =
      0.5;


  const auto moving =
      navigation.update_progress(
          middle);

  if (!(moving.progress_fraction > 0.0 &&
        moving.progress_fraction < 1.0)) {
    return fail(
        "Expected in-route navigation progress.");
  }

  if (!(moving.remaining_distance_m >
            0.0 &&
        moving.remaining_distance_m <
            route.distance_m)) {
    return fail(
        "Expected reduced remaining distance.");
  }

  if (!(moving.remaining_duration_s >
            0.0 &&
        moving.remaining_duration_s <
            route.duration_s)) {
    return fail(
        "Expected reduced remaining duration.");
  }

  if (!moving.current_maneuver.has_value()) {
    return fail(
        "Expected current Valhalla maneuver.");
  }

  if (moving.routing_engine_invoked ||
      moving.candidate_selection_invoked ||
      moving.cost_engine_invoked ||
      moving.route_recomputed) {
    return fail(
        "Navigation runtime crossed routing boundary.");
  }


  NavigationProgressUpdate finish;

  finish.shape_segment_index =
      final_segment;

  finish.segment_fraction =
      1.0;


  const auto arrived =
      navigation.update_progress(
          finish);

  if (!arrived.arrived ||
      arrived.state !=
          NavigationSessionState::Arrived) {
    return fail(
        "Expected Arrived navigation state.");
  }

  if (std::abs(
          arrived.progress_fraction -
          1.0) >
      1e-12) {
    return fail(
        "Arrival progress is not 1.0.");
  }

  if (std::abs(
          arrived.remaining_distance_m) >
      1e-6) {
    return fail(
        "Arrival still has remaining distance.");
  }

  if (std::abs(
          arrived.remaining_duration_s) >
      1e-6) {
    return fail(
        "Arrival still has remaining duration.");
  }

  if (!arrived.current_maneuver_index.has_value()) {
    return fail(
        "Arrival maneuver is missing.");
  }

  if (*arrived.current_maneuver_index !=
      route.maneuvers.size() - 1) {
    return fail(
        "Arrival did not expose final maneuver.");
  }

  if (arrived.reroute_requested ||
      arrived.route_recomputed ||
      arrived.routing_engine_invoked ||
      arrived.candidate_selection_invoked ||
      arrived.cost_engine_invoked ||
      arrived.production_route_mutation_allowed) {
    return fail(
        "Arrival snapshot crossed immutable routing boundary.");
  }


  std::cout
      << "PASS: Navigation runtime over real Valhalla route\n"
      << "  route:       "
      << route.route_id
      << '\n'
      << "  geometry:    "
      << route.geometry.size()
      << " points\n"
      << "  maneuvers:   "
      << route.maneuvers.size()
      << '\n'
      << "  distance:    "
      << route.distance_m
      << " m\n"
      << "  duration:    "
      << route.duration_s
      << " s\n";

  return 0;
}
