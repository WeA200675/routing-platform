#include <cassert>
#include <stdexcept>

#include "routing/core/drive/routing_snapshot.hpp"

int main() {
  using namespace routing::core;
  using namespace routing::core::drive;

  RouteRequest request;
  request.origin = {47.1410, 9.5209};
  request.destination = {47.1660, 9.5100};
  request.via_points.push_back(
      {47.1530, 9.5150});

  request.family =
      CandidateFamily::MajorRoads;

  request.alternatives = 3;
  request.costing_profile = "auto";

  const auto request_snapshot =
      make_route_request_snapshot(request);

  assert(
      request_snapshot.candidate_family ==
      "major_roads");

  assert(
      request_snapshot.via_points.size() == 1);

  assert(
      request_snapshot.alternatives_requested == 3);

  assert(
      request_snapshot.costing_profile.has_value());

  assert(
      *request_snapshot.costing_profile ==
      "auto");

  RoutePath route;
  route.route_id = "route-001";

  route.family =
      CandidateFamily::MajorRoads;

  route.distance_m = 3174.0;
  route.duration_s = 242.149;

  StreetSegment first;
  first.id = "valhalla:10";
  first.length_m = 100.0;

  StreetSegment second;
  second.id = "valhalla:20";
  second.length_m = 200.0;

  route.segments = {
      first,
      second,
  };

  route.segment_ids = {
      "valhalla:10",
      "valhalla:20",
  };

  const auto snapshot =
      make_route_snapshot(route);

  assert(snapshot.route_id == "route-001");

  assert(
      snapshot.candidate_family ==
      "major_roads");

  assert(snapshot.distance_m == 3174.0);
  assert(snapshot.duration_s == 242.149);

  assert(snapshot.segment_ids.size() == 2);

  assert(
      snapshot.segment_ids[0] ==
      "valhalla:10");

  assert(
      snapshot.segment_ids[1] ==
      "valhalla:20");

  RoutePath invalid = route;

  invalid.segment_ids[1] =
      "valhalla:not-the-segment";

  bool mismatch_threw = false;

  try {
    (void)make_route_snapshot(invalid);
  } catch (const std::invalid_argument&) {
    mismatch_threw = true;
  }

  assert(mismatch_threw);

  invalid = route;
  invalid.route_id.clear();

  bool missing_id_threw = false;

  try {
    (void)make_route_snapshot(invalid);
  } catch (const std::invalid_argument&) {
    missing_id_threw = true;
  }

  assert(missing_id_threw);

  return 0;
}
