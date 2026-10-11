#include <cassert>
#include <iostream>

#include "routing/core/candidates/route_identity.hpp"

int main() {
  using namespace routing::core;
  using namespace routing::core::candidates;

  RoutePath fastest;

  fastest.route_id =
      "valhalla-0";

  fastest.family =
      CandidateFamily::Fastest;

  fastest.engine_name =
      "valhalla";

  fastest.segment_ids = {
      "edge-1",
      "edge-2",
      "edge-3",
  };

  RoutePath comfort =
      fastest;

  comfort.route_id =
      "valhalla-0";

  comfort.family =
      CandidateFamily::Comfort;

  // Same physical path despite different family/request.
  assert(
      route_path_signature(
          fastest) ==
      route_path_signature(
          comfort));

  RoutePath other =
      comfort;

  other.segment_ids = {
      "edge-1",
      "edge-X",
      "edge-3",
  };

  assert(
      route_path_signature(
          fastest) !=
      route_path_signature(
          other));

  RoutePath geometry_a;

  geometry_a.route_id = "a";
  geometry_a.engine_name = "fake";

  geometry_a.geometry = {
      {47.0, 9.0},
      {47.1, 9.1},
  };

  RoutePath geometry_b =
      geometry_a;

  geometry_b.route_id = "b";

  assert(
      route_path_signature(
          geometry_a) ==
      route_path_signature(
          geometry_b));

  std::cout
      << "Route identity tests passed\n";

  return 0;
}
