#include <cassert>
#include <iostream>
#include <stdexcept>

#include "routing/core/drive/drive_session.hpp"

namespace {

routing::core::drive::RouteRequestSnapshot
make_request() {
  routing::core::drive::RouteRequestSnapshot request;

  request.origin = {
      47.1410,
      9.5209,
  };

  request.destination = {
      47.2410,
      9.5310,
  };

  request.candidate_family =
      "profile_optimal";

  request.costing_profile =
      "auto";

  return request;
}

routing::core::drive::RouteSnapshot
make_route() {
  routing::core::drive::RouteSnapshot route;

  route.route_id = "route-a";
  route.candidate_family =
      "profile_optimal";

  route.distance_m = 12000.0;
  route.duration_s = 800.0;

  return route;
}

routing::core::drive::ReplaySemanticsSnapshot
make_semantics() {
  using namespace routing::core;

  VehicleProfile vehicle;
  vehicle.id = "tourer";
  vehicle.width_m = 2.0;
  vehicle.height_m = 1.8;
  vehicle.weight_kg = 2100.0;

  RuleSet rules;
  rules.id = "rules";
  rules.version = "7";

  RoutingContext context;
  context.comfort_budget_seconds = 900.0;
  context.shortcut_threshold_seconds = 600.0;
  context.max_segment_preference_bonus_fraction =
      0.20;

  return routing::core::drive::
      make_replay_semantics_snapshot(
          vehicle,
          rules,
          context);
}

}  // namespace

int main() {
  using namespace routing::core::drive;

  // Explicit legacy v1 session remains valid without replay payload.
  {
    DriveSessionHeader legacy;

    legacy.schema_version =
        kDriveSessionLegacySchemaVersion;

    legacy.session_id =
        "drive:legacy:v1";

    DriveSessionRecorder recorder(
        legacy,
        make_request(),
        make_route());

    assert(
        recorder.session().header.schema_version ==
        kDriveSessionLegacySchemaVersion);

    assert(
        !recorder.session()
             .replay_semantics
             .has_value());
  }

  // Current schema can carry exact semantics.
  {
    DriveSessionHeader current;

    current.session_id =
        "drive:replay:v2";

    assert(
        current.schema_version ==
        kDriveSessionReplaySemanticsVersion);

    const auto semantics =
        make_semantics();

    DriveSessionRecorder recorder(
        current,
        make_request(),
        make_route(),
        {},
        semantics);

    assert(
        recorder.session()
            .replay_semantics
            .has_value());

    assert(
        recorder.session()
            .replay_semantics
            ->vehicle.id ==
        "tourer");

    assert(
        recorder.session()
            .replay_semantics
            ->rules.version ==
        "7");

    assert(
        recorder.session()
            .replay_semantics
            ->context
            .comfort_budget_seconds ==
        900.0);
  }

  // Legacy schema may not lie about containing v2 replay semantics.
  {
    DriveSessionHeader legacy;

    legacy.schema_version =
        kDriveSessionLegacySchemaVersion;

    legacy.session_id =
        "drive:legacy:invalid";

    bool rejected = false;

    try {
      DriveSessionRecorder recorder(
          legacy,
          make_request(),
          make_route(),
          {},
          make_semantics());

      (void)recorder;
    } catch (const std::invalid_argument&) {
      rejected = true;
    }

    assert(rejected);
  }

  std::cout
      << "Drive replay snapshot tests passed\n";

  return 0;
}
