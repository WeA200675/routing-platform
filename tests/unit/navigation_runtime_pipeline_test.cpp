#include <cassert>
#include <iostream>
#include <string>

#include "routing/core/navigation/navigation_runtime.hpp"
#include "routing/core/routing_engine.hpp"

namespace {

class FixtureRoutingEngine final :
    public routing::core::IRoutingEngine {
 public:
  [[nodiscard]]
  std::string name() const override {
    return "fixture-routing-engine";
  }

  [[nodiscard]]
  std::string version() const override {
    return "1.0";
  }

  [[nodiscard]]
  bool ready() const override {
    return true;
  }

  [[nodiscard]]
  routing::core::RoutingResult route(
      const routing::core::RouteRequest& request) const override {
    using namespace routing::core;

    RoutingResult result;

    result.success =
        true;


    RoutePath route;

    route.route_id =
        "fixture-route";

    route.family =
        request.family;

    route.distance_m =
        2400.0;

    route.duration_s =
        300.0;

    route.geometry = {
        request.origin,
        {47.1500, 9.5150},
        request.destination,
    };


    RouteManeuver start;

    start.type =
        ManeuverType::Start;

    start.instruction =
        "Start";

    start.begin_shape_index =
        0;

    start.end_shape_index =
        0;


    RouteManeuver continue_maneuver;

    continue_maneuver.type =
        ManeuverType::Continue;

    continue_maneuver.instruction =
        "Continue";

    continue_maneuver.street_names = {
        "Fixture Road",
    };

    continue_maneuver.distance_m =
        2000.0;

    continue_maneuver.duration_s =
        250.0;

    continue_maneuver.begin_shape_index =
        0;

    continue_maneuver.end_shape_index =
        2;


    RouteManeuver arrive;

    arrive.type =
        ManeuverType::Arrive;

    arrive.instruction =
        "Arrive";

    arrive.distance_m =
        400.0;

    arrive.duration_s =
        50.0;

    arrive.begin_shape_index =
        2;

    arrive.end_shape_index =
        2;


    route.maneuvers = {
        start,
        continue_maneuver,
        arrive,
    };

    route.engine_name =
        name();

    route.engine_version =
        version();


    result.routes.push_back(
        route);

    return result;
  }
};

}  // namespace


int main() {
  using namespace routing::core;
  using namespace routing::core::navigation;


  FixtureRoutingEngine engine;

  assert(
      engine.ready());


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
      CandidateFamily::Comfort;


  const auto routed =
      engine.route(
          request);

  assert(
      routed.success);

  assert(
      routed.routes.size() ==
      1);


  // Navigation consumes the already returned route.
  NavigationSession navigation(
      "navigation:pipeline",
      routed.routes.front());


  const auto preview =
      navigation.snapshot();

  assert(
      preview.route_preview != nullptr);

  assert(
      preview.route_preview->family ==
      CandidateFamily::Comfort);

  assert(
      preview.route_preview->engine_name ==
      "fixture-routing-engine");

  assert(
      preview.route_preview->geometry.size() ==
      3);


  const auto started =
      navigation.start();

  assert(
      started.state ==
      NavigationSessionState::Navigating);


  NavigationProgressUpdate midway;

  midway.shape_segment_index =
      0;

  midway.segment_fraction =
      0.75;


  const auto moving =
      navigation.update_progress(
          midway);

  assert(
      moving.progress_fraction >
      0.0);

  assert(
      moving.remaining_distance_m <
      routed.routes.front().distance_m);

  assert(
      moving.current_maneuver.has_value());

  assert(
      !moving.routing_engine_invoked);

  assert(
      !moving.candidate_selection_invoked);

  assert(
      !moving.cost_engine_invoked);


  NavigationProgressUpdate finish;

  finish.shape_segment_index =
      1;

  finish.segment_fraction =
      1.0;


  const auto arrived =
      navigation.update_progress(
          finish);

  assert(
      arrived.arrived);

  assert(
      arrived.state ==
      NavigationSessionState::Arrived);

  assert(
      arrived.current_maneuver.has_value());

  assert(
      arrived.current_maneuver->type ==
      ManeuverType::Arrive);

  assert(
      !arrived.route_recomputed);


  std::cout
      << "Navigation runtime pipeline tests passed\n";

  return 0;
}
