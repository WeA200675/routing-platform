#include <cassert>
#include <cmath>
#include <iostream>
#include <limits>
#include <stdexcept>
#include <string>

#include "routing/core/navigation/navigation_runtime.hpp"

namespace {

routing::core::RoutePath
make_valid_route() {
  using namespace routing::core;

  RoutePath route;

  route.route_id =
      "route:boundary";

  route.distance_m =
      1000.0;

  route.duration_s =
      120.0;

  route.geometry = {
      {47.1000, 9.5000},
      {47.1050, 9.5000},
      {47.1100, 9.5000},
  };


  RouteManeuver first;

  first.type =
      ManeuverType::Continue;

  first.instruction =
      "Continue";

  first.distance_m =
      600.0;

  first.duration_s =
      70.0;

  first.begin_shape_index =
      0;

  first.end_shape_index =
      1;


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
      1;

  arrive.end_shape_index =
      2;


  route.maneuvers = {
      first,
      arrive,
  };

  route.engine_name =
      "fixture";

  route.engine_version =
      "1.0";

  return route;
}


template <typename Function>
bool invalid_argument_thrown(
    Function&& function) {
  try {
    function();
  } catch (const std::invalid_argument&) {
    return true;
  }

  return false;
}


template <typename Function>
bool logic_error_thrown(
    Function&& function) {
  try {
    function();
  } catch (const std::logic_error&) {
    return true;
  }

  return false;
}

}  // namespace


int main() {
  using namespace routing::core;
  using namespace routing::core::navigation;


  // -------------------------------------------------------------
  // INVALID ROUTES
  // -------------------------------------------------------------

  {
    auto route =
        make_valid_route();

    route.route_id.clear();

    assert(
        invalid_argument_thrown(
            [&]() {
              NavigationSession session(
                  "session",
                  route);
            }));
  }


  {
    auto route =
        make_valid_route();

    route.distance_m =
        std::numeric_limits<double>::
            quiet_NaN();

    assert(
        invalid_argument_thrown(
            [&]() {
              NavigationSession session(
                  "session",
                  route);
            }));
  }


  {
    auto route =
        make_valid_route();

    route.geometry = {
        {47.1000, 9.5000},
    };

    assert(
        invalid_argument_thrown(
            [&]() {
              NavigationSession session(
                  "session",
                  route);
            }));
  }


  {
    auto route =
        make_valid_route();

    route.geometry[1].latitude =
        91.0;

    assert(
        invalid_argument_thrown(
            [&]() {
              NavigationSession session(
                  "session",
                  route);
            }));
  }


  {
    auto route =
        make_valid_route();

    route.geometry = {
        {47.1000, 9.5000},
        {47.1000, 9.5000},
        {47.1000, 9.5000},
    };

    assert(
        invalid_argument_thrown(
            [&]() {
              NavigationSession session(
                  "session",
                  route);
            }));
  }


  {
    auto route =
        make_valid_route();

    route.maneuvers[1].end_shape_index =
        route.geometry.size();

    assert(
        invalid_argument_thrown(
            [&]() {
              NavigationSession session(
                  "session",
                  route);
            }));
  }


  {
    auto route =
        make_valid_route();

    route.maneuvers[1].begin_shape_index =
        0;

    route.maneuvers[1].end_shape_index =
        0;

    assert(
        invalid_argument_thrown(
            [&]() {
              NavigationSession session(
                  "session",
                  route);
            }));
  }


  {
    const auto route =
        make_valid_route();

    assert(
        invalid_argument_thrown(
            [&]() {
              NavigationSession session(
                  "",
                  route);
            }));
  }


  // Maneuver-less routes are valid route previews.
  {
    auto route =
        make_valid_route();

    route.maneuvers.clear();

    NavigationSession session(
        "session:no-guidance",
        route);

    const auto snapshot =
        session.snapshot();

    assert(
        !snapshot.current_maneuver.has_value());

    assert(
        !snapshot.next_maneuver.has_value());
  }


  // -------------------------------------------------------------
  // STATE / PROGRESS BOUNDARY
  // -------------------------------------------------------------

  NavigationSession session(
      "session:progress",
      make_valid_route());


  assert(
      logic_error_thrown(
          [&]() {
            NavigationProgressUpdate update;
            update.shape_segment_index = 0;
            update.segment_fraction = 0.2;

            (void)session.update_progress(
                update);
          }));


  (void)session.start();


  assert(
      invalid_argument_thrown(
          [&]() {
            NavigationProgressUpdate update;
            update.shape_segment_index = 0;
            update.segment_fraction = -0.1;

            (void)session.update_progress(
                update);
          }));


  assert(
      invalid_argument_thrown(
          [&]() {
            NavigationProgressUpdate update;
            update.shape_segment_index = 0;
            update.segment_fraction =
                std::numeric_limits<double>::
                    quiet_NaN();

            (void)session.update_progress(
                update);
          }));


  assert(
      invalid_argument_thrown(
          [&]() {
            NavigationProgressUpdate update;
            update.shape_segment_index = 2;
            update.segment_fraction = 0.0;

            (void)session.update_progress(
                update);
          }));


  NavigationProgressUpdate forward;

  forward.shape_segment_index =
      1;

  forward.segment_fraction =
      0.5;

  const auto forward_snapshot =
      session.update_progress(
          forward);

  assert(
      forward_snapshot.progress_fraction >
      0.0);


  assert(
      logic_error_thrown(
          [&]() {
            NavigationProgressUpdate update;
            update.shape_segment_index = 0;
            update.segment_fraction = 0.9;

            (void)session.update_progress(
                update);
          }));


  assert(
      logic_error_thrown(
          [&]() {
            NavigationProgressUpdate update;
            update.shape_segment_index = 1;
            update.segment_fraction = 0.25;

            (void)session.update_progress(
                update);
          }));


  NavigationProgressUpdate finish;

  finish.shape_segment_index =
      1;

  finish.segment_fraction =
      1.0;

  const auto arrived =
      session.update_progress(
          finish);

  assert(
      arrived.arrived);


  assert(
      logic_error_thrown(
          [&]() {
            NavigationProgressUpdate update;
            update.shape_segment_index = 1;
            update.segment_fraction = 1.0;

            (void)session.update_progress(
                update);
          }));


  assert(
      logic_error_thrown(
          [&]() {
            (void)session.start();
          }));


  // -------------------------------------------------------------
  // NO HIDDEN ROUTING SIDE EFFECT
  // -------------------------------------------------------------

  assert(
      !arrived.reroute_requested);

  assert(
      !arrived.route_recomputed);

  assert(
      !arrived.routing_engine_invoked);

  assert(
      !arrived.candidate_selection_invoked);

  assert(
      !arrived.cost_engine_invoked);

  assert(
      !arrived.production_route_mutation_allowed);


  std::cout
      << "Navigation runtime boundary tests passed\n";

  return 0;
}
