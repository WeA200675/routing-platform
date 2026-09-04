#include <cassert>
#include <cmath>
#include <iostream>
#include <string>

#include "routing/core/navigation/navigation_runtime.hpp"

namespace {

routing::core::RoutePath
make_route() {
  using namespace routing::core;

  RoutePath route;

  route.route_id =
      "route:navigation:1";

  route.family =
      CandidateFamily::ProfileOptimal;

  route.distance_m =
      3000.0;

  route.duration_s =
      600.0;

  route.geometry = {
      {47.1400, 9.5200},
      {47.1450, 9.5200},
      {47.1500, 9.5200},
      {47.1550, 9.5200},
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
      "Continue straight";

  continue_maneuver.street_names = {
      "Teststrasse",
  };

  continue_maneuver.distance_m =
      1900.0;

  continue_maneuver.duration_s =
      380.0;

  continue_maneuver.begin_shape_index =
      0;

  continue_maneuver.end_shape_index =
      2;


  RouteManeuver turn;

  turn.type =
      ManeuverType::TurnRight;

  turn.instruction =
      "Turn right";

  turn.distance_m =
      1100.0;

  turn.duration_s =
      220.0;

  turn.begin_shape_index =
      2;

  turn.end_shape_index =
      3;


  RouteManeuver arrive;

  arrive.type =
      ManeuverType::Arrive;

  arrive.instruction =
      "Arrive";

  arrive.begin_shape_index =
      3;

  arrive.end_shape_index =
      3;


  route.maneuvers = {
      start,
      continue_maneuver,
      turn,
      arrive,
  };

  route.engine_name =
      "fixture";

  route.engine_version =
      "1.0";

  route.segment_data_status =
      RouteSegmentDataStatus::Complete;


  RouteDiagnostic diagnostic;

  diagnostic.code =
      "TEST_DIAGNOSTIC";

  diagnostic.message =
      "Preserved preview diagnostic.";

  route.diagnostics.push_back(
      diagnostic);

  return route;
}

}  // namespace


int main() {
  using namespace routing::core;
  using namespace routing::core::navigation;


  const auto route =
      make_route();


  NavigationSession session(
      "navigation-session:1",
      route);


  // -------------------------------------------------------------
  // PREVIEW
  // -------------------------------------------------------------

  const auto preview =
      session.snapshot();

  assert(
      preview.schema_version ==
      kNavigationRuntimeSchemaVersion);

  assert(
      preview.state ==
      NavigationSessionState::Preview);

  assert(
      preview.route_preview != nullptr);

  assert(
      preview.route_preview->route_id ==
      route.route_id);

  assert(
      preview.route_preview->geometry.size() ==
      route.geometry.size());

  assert(
      preview.route_preview->maneuvers.size() ==
      route.maneuvers.size());

  assert(
      preview.route_preview->engine_name ==
      "fixture");

  assert(
      preview.route_preview->diagnostics.size() ==
      1);

  assert(
      preview.progress_fraction ==
      0.0);

  assert(
      preview.remaining_distance_m ==
      route.distance_m);

  assert(
      preview.remaining_duration_s ==
      route.duration_s);

  assert(
      preview.current_maneuver_index.has_value());

  assert(
      *preview.current_maneuver_index ==
      0);

  assert(
      preview.current_maneuver.has_value());

  assert(
      preview.current_maneuver->type ==
      ManeuverType::Start);


  // -------------------------------------------------------------
  // START
  // -------------------------------------------------------------

  const auto started =
      session.start();

  assert(
      started.state ==
      NavigationSessionState::Navigating);

  assert(
      !started.arrived);


  // start() is idempotent while navigating.
  const auto started_again =
      session.start();

  assert(
      started_again.state ==
      NavigationSessionState::Navigating);


  // -------------------------------------------------------------
  // MOVE INTO FIRST SEGMENT
  // -------------------------------------------------------------

  NavigationProgressUpdate first;

  first.shape_segment_index =
      0;

  first.segment_fraction =
      0.5;


  const auto first_snapshot =
      session.update_progress(
          first);

  assert(
      first_snapshot.progress_fraction >
      0.0);

  assert(
      first_snapshot.progress_fraction <
      1.0);

  assert(
      first_snapshot.traveled_distance_m >
      0.0);

  assert(
      first_snapshot.remaining_distance_m <
      route.distance_m);

  assert(
      first_snapshot.remaining_duration_s <
      route.duration_s);

  assert(
      first_snapshot.current_maneuver.has_value());

  assert(
      first_snapshot.current_maneuver->type ==
      ManeuverType::Continue);

  assert(
      first_snapshot.next_maneuver.has_value());

  assert(
      first_snapshot.next_maneuver->type ==
      ManeuverType::TurnRight);

  assert(
      first_snapshot.distance_to_current_maneuver_end_m >
      0.0);


  // -------------------------------------------------------------
  // MOVE BEYOND CONTINUE MANEUVER
  // -------------------------------------------------------------

  NavigationProgressUpdate turn_progress;

  turn_progress.shape_segment_index =
      2;

  turn_progress.segment_fraction =
      0.25;


  const auto turn_snapshot =
      session.update_progress(
          turn_progress);

  assert(
      turn_snapshot.current_maneuver.has_value());

  assert(
      turn_snapshot.current_maneuver->type ==
      ManeuverType::TurnRight);

  assert(
      turn_snapshot.next_maneuver.has_value());

  assert(
      turn_snapshot.next_maneuver->type ==
      ManeuverType::Arrive);

  assert(
      turn_snapshot.progress_fraction >
      first_snapshot.progress_fraction);


  // -------------------------------------------------------------
  // ARRIVAL
  // -------------------------------------------------------------

  NavigationProgressUpdate arrival;

  arrival.shape_segment_index =
      2;

  arrival.segment_fraction =
      1.0;


  const auto arrived =
      session.update_progress(
          arrival);

  assert(
      arrived.state ==
      NavigationSessionState::Arrived);

  assert(
      arrived.arrived);

  assert(
      std::abs(
          arrived.progress_fraction -
          1.0) <
      1e-12);

  assert(
      std::abs(
          arrived.remaining_distance_m) <
      1e-9);

  assert(
      std::abs(
          arrived.remaining_duration_s) <
      1e-9);

  assert(
      arrived.current_maneuver.has_value());

  assert(
      arrived.current_maneuver->type ==
      ManeuverType::Arrive);


  // -------------------------------------------------------------
  // HARD RUNTIME BOUNDARY
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
      << "Navigation runtime tests passed\n";

  return 0;
}
