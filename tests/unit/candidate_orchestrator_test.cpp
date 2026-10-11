#include <cassert>
#include <iostream>
#include <string>

#include "routing/core/candidates/candidate_orchestrator.hpp"

namespace {

routing::core::RoutePath make_route(
    const routing::core::RouteRequest& request,
    const std::string& route_id,
    const std::string& segment_id,
    const double length_m,
    const double speed_kmh,
    const routing::core::FunctionalRoadClass road_class =
        routing::core::FunctionalRoadClass::Secondary) {
  using namespace routing::core;

  RoutePath route;

  route.route_id =
      route_id;

  route.family =
      request.family;

  route.distance_m =
      length_m;

  route.duration_s =
      length_m /
      (speed_kmh / 3.6);

  route.geometry = {
      request.origin,
      request.destination,
  };

  RouteManeuver maneuver;

  maneuver.type =
      ManeuverType::Start;

  maneuver.instruction =
      "Test route";

  maneuver.distance_m =
      length_m;

  maneuver.duration_s =
      route.duration_s;

  route.maneuvers.push_back(
      maneuver);

  StreetSegment segment;

  segment.id =
      segment_id;

  segment.length_m =
      length_m;

  segment.functional_road_class =
      road_class;

  segment.road_network_class =
      road_class ==
              FunctionalRoadClass::Primary
          ? RoadNetworkClass::FederalRoad
          : RoadNetworkClass::MunicipalRoad;

  segment.speed_limit_kmh =
      speed_kmh;

  segment.practical_speed_kmh =
      speed_kmh;

  segment.curvature_score = 0.10;
  segment.serpentine_score = 0.10;
  segment.gradient_abs_pct = 1.0;
  segment.urban_score = 0.10;
  segment.data_confidence = 1.0;

  route.segments.push_back(
      segment);

  route.segment_ids.push_back(
      segment.id);

  route.engine_name =
      "fake";

  route.engine_version =
      "1";

  return route;
}

class FakeRoutingEngine final
    : public routing::core::IRoutingEngine {
 public:
  [[nodiscard]]
  std::string name() const override {
    return "fake";
  }

  [[nodiscard]]
  std::string version() const override {
    return "1";
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
    result.success = true;

    switch (request.family) {
      case CandidateFamily::Fastest:
        result.routes.push_back(
            make_route(
                request,
                "fake-0",
                "path-a",
                1000.0,
                50.0));
        break;

      case CandidateFamily::Shortest:
        // Same physical path as Fastest.
        result.routes.push_back(
            make_route(
                request,
                "fake-0",
                "path-a",
                1000.0,
                50.0));
        break;

      case CandidateFamily::ProfileOptimal:
        result.routes.push_back(
            make_route(
                request,
                "fake-0",
                "path-a",
                1000.0,
                50.0));

        result.routes.push_back(
            make_route(
                request,
                "fake-1",
                "path-b",
                900.0,
                90.0));
        break;

      case CandidateFamily::MajorRoads:
        result.routes.push_back(
            make_route(
                request,
                "fake-0",
                "path-c",
                1100.0,
                80.0,
                FunctionalRoadClass::Primary));
        break;

      default:
        result.routes.push_back(
            make_route(
                request,
                "fake-0",
                "path-other",
                1200.0,
                60.0));
        break;
    }

    return result;
  }
};

}  // namespace

int main() {
  using namespace routing::core;
  using namespace routing::core::candidates;

  FakeRoutingEngine engine;

  CandidateOrchestrator orchestrator(
      engine);

  RouteRequest request;

  request.origin = {
      47.1410,
      9.5209,
  };

  request.destination = {
      47.2410,
      9.5310,
  };

  VehicleProfile vehicle;
  RuleSet rules;
  RoutingContext context;

  CandidateFamilySelectionPolicy policy;

  policy.include_fastest_reference = true;
  policy.include_shortest_reference = true;

  policy.forced_families = {
      CandidateFamily::MajorRoads,
  };

  const auto result =
      orchestrator.route(
          request,
          vehicle,
          rules,
          context,
          policy);

  assert(result.success);

  // Fastest + Shortest + ProfileOptimal + forced MajorRoads.
  assert(result.activations.size() == 4);
  assert(result.family_runs.size() == 4);

  // ProfileOptimal generated 2, the other three generated 1 each.
  assert(result.generated_route_count == 5);

  // Fastest and Shortest both represent path-a and must merge.
  // ProfileOptimal selects path-b, MajorRoads path-c.
  assert(
      result.unique_representatives.size() ==
      3);

  assert(
      result.selected_unique_index
          .has_value());

  const auto& winner =
      result.unique_representatives[
          *result.selected_unique_index];

  // path-b = 900 m @ 90 km/h = 36 seconds.
  // It must win through the existing CostEngine total.
  assert(
      winner.route.segment_ids.size() ==
      1);

  assert(
      winner.route.segment_ids.front() ==
      "path-b");

  assert(
      winner.evaluation
          .total_seconds_equivalent <
      40.0);

  // Verify physical-path dedup preserved both families.
  bool found_merged_path = false;

  for (const auto& candidate :
       result.unique_representatives) {
    if (candidate.route.segment_ids.front() !=
        "path-a") {
      continue;
    }

    found_merged_path = true;

    bool has_fastest = false;
    bool has_shortest = false;

    for (const auto family :
         candidate.represented_families) {
      has_fastest =
          has_fastest ||
          family ==
              CandidateFamily::Fastest;

      has_shortest =
          has_shortest ||
          family ==
              CandidateFamily::Shortest;
    }

    assert(has_fastest);
    assert(has_shortest);
  }

  assert(found_merged_path);

  std::cout
      << "Candidate orchestrator tests passed\n";

  return 0;
}
