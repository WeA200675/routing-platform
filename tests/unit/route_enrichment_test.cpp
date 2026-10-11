#include <cassert>
#include <iostream>
#include <stdexcept>
#include <string>
#include <vector>

#include "routing/core/route_enrichment.hpp"

namespace {

routing::core::RoutePath make_route(
    const std::string& id) {
  routing::core::RoutePath route;

  route.route_id = id;

  route.distance_m = 1000.0;
  route.duration_s = 60.0;

  route.geometry = {
      {47.14, 9.52},
      {47.15, 9.53},
  };

  route.engine_name = "fake";
  route.engine_version = "1";

  return route;
}

routing::core::StreetSegment make_segment(
    const std::string& id) {
  using namespace routing::core;

  StreetSegment segment;

  segment.id = id;
  segment.length_m = 1000.0;

  segment.functional_road_class =
      FunctionalRoadClass::Primary;

  segment.road_network_class =
      RoadNetworkClass::FederalRoad;

  segment.speed_limit_kmh = 80.0;
  segment.practical_speed_kmh = 80.0;

  segment.curvature_score = 0.1;
  segment.serpentine_score = 0.1;
  segment.gradient_abs_pct = 1.0;
  segment.urban_score = 0.1;
  segment.data_confidence = 1.0;

  return segment;
}

}  // namespace

int main() {
  using namespace routing::core;

  std::vector<RoutePath> routes;

  routes.push_back(
      make_route("broken"));

  routes.push_back(
      make_route("good"));

  routes.push_back(
      make_route("empty"));

  const auto summary =
      enrich_route_segments_independently(
          routes,
          [](const RoutePath& route) {
            if (route.route_id == "broken") {
              throw std::runtime_error(
                  "Synthetic trace failure.");
            }

            if (route.route_id == "empty") {
              return std::vector<StreetSegment>{};
            }

            return std::vector<StreetSegment>{
                make_segment("good-segment"),
            };
          });

  assert(
      summary.complete_route_count == 1);

  assert(
      summary.unavailable_route_count == 2);

  // Broken route survives with routing geometry and timing.
  assert(
      routes[0].route_id == "broken");

  assert(
      routes[0].geometry.size() == 2);

  assert(
      routes[0].duration_s == 60.0);

  assert(
      routes[0].segments.empty());

  assert(
      routes[0].segment_ids.empty());

  assert(
      routes[0].segment_data_status ==
      RouteSegmentDataStatus::Unavailable);

  assert(
      routes[0].diagnostics.size() == 1);

  assert(
      routes[0].diagnostics.front().code ==
      "ROUTE_SEGMENT_ENRICHMENT_FAILED");

  // Good sibling survives and is fully enriched.
  assert(
      routes[1].segment_data_status ==
      RouteSegmentDataStatus::Complete);

  assert(
      routes[1].segments.size() == 1);

  assert(
      routes[1].segment_ids.size() == 1);

  assert(
      routes[1].segment_ids.front() ==
      "good-segment");

  assert(
      routes[1].diagnostics.empty());

  // Empty enrichment is also unavailable, never a fabricated
  // zero-segment "good" route.
  assert(
      routes[2].segment_data_status ==
      RouteSegmentDataStatus::Unavailable);

  assert(
      routes[2].segments.empty());

  assert(
      routes[2].diagnostics.size() == 1);

  std::cout
      << "Route enrichment isolation tests passed\n";

  return 0;
}
