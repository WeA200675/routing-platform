#include <cassert>
#include <iostream>
#include <string>

#include "routing/core/testing/route_decision_report.hpp"

namespace {

routing::core::RoutePath make_route(
    const routing::core::RouteRequest& request,
    const std::string& route_id,
    const std::string& segment_id,
    const double length_m,
    const double speed_kmh,
    const routing::core::FunctionalRoadClass road_class) {
  using namespace routing::core;

  RoutePath route;

  route.route_id = route_id;
  route.family = request.family;
  route.distance_m = length_m;

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
      "Scenario test route";

  maneuver.distance_m =
      length_m;

  maneuver.duration_s =
      route.duration_s;

  route.maneuvers.push_back(
      maneuver);

  StreetSegment segment;

  segment.id = segment_id;
  segment.length_m = length_m;

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

  route.engine_name = "fake";
  route.engine_version = "1";

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

    if (request.family ==
        CandidateFamily::Fastest) {
      result.routes.push_back(
          make_route(
              request,
              "fake-fastest",
              "path-a",
              1000.0,
              50.0,
              FunctionalRoadClass::Secondary));

      return result;
    }

    if (request.family ==
        CandidateFamily::ProfileOptimal) {
      // Duplicate of Fastest.
      result.routes.push_back(
          make_route(
              request,
              "fake-profile-0",
              "path-a",
              1000.0,
              50.0,
              FunctionalRoadClass::Secondary));

      // Better semantic route.
      result.routes.push_back(
          make_route(
              request,
              "fake-profile-1",
              "path-b",
              900.0,
              90.0,
              FunctionalRoadClass::Primary));

      return result;
    }

    result.routes.push_back(
        make_route(
            request,
            "fake-other",
            "path-c",
            1200.0,
            60.0,
            FunctionalRoadClass::Secondary));

    return result;
  }
};

}  // namespace

int main() {
  using namespace routing::core;
  using namespace routing::core::testing;

  FakeRoutingEngine engine;

  RoutingScenario scenario;

  scenario.id =
      "scenario:runner";

  scenario.request.origin = {
      47.1410,
      9.5209,
  };

  scenario.request.destination = {
      47.2410,
      9.5310,
  };

  scenario.family_policy
      .include_fastest_reference =
          true;

  scenario.family_policy
      .include_shortest_reference =
          false;

  scenario.expectations
      .minimum_generated_routes =
          3;

  scenario.expectations
      .minimum_family_representatives =
          2;

  scenario.expectations
      .minimum_unique_representatives =
          2;

  scenario.expectations
      .maximum_selected_distance_m =
          1000.0;

  scenario.expectations
      .maximum_selected_duration_s =
          60.0;

  ScenarioMetricExpectation road_class;

  road_class.metric =
      RouteMetric::KnownRoadClassCoverage;

  road_class.minimum_value = 1.0;
  road_class.minimum_known_coverage = 1.0;

  scenario.expectations
      .selected_route_metrics
      .push_back(
          road_class);

  ScenarioMetricExpectation major;

  major.metric =
      RouteMetric::MajorRoadShare;

  major.minimum_value = 0.90;
  major.minimum_known_coverage = 1.0;

  scenario.expectations
      .selected_route_metrics
      .push_back(
          major);

  const auto result =
      run_routing_scenario(
          engine,
          scenario);

  assert(result.passed);
  assert(result.orchestration.success);

  assert(
      result.orchestration
          .generated_route_count ==
      3);

  assert(
      result.orchestration
          .unique_representatives
          .size() ==
      2);

  assert(
      result.orchestration
          .selected_unique_index
          .has_value());

  const auto& winner =
      result.orchestration
          .unique_representatives[
              *result.orchestration
                   .selected_unique_index];

  assert(
      winner.route.segment_ids.front() ==
      "path-b");

  const auto report =
      format_routing_scenario_report(
          result);

  assert(
      report.find(
          "scenario result: PASS") !=
      std::string::npos);

  assert(
      report.find(
          "* SELECTED") !=
      std::string::npos);

  assert(
      report.find(
          "fake-profile-1") !=
      std::string::npos);

  // Now deliberately create a failing regression condition.
  auto failing =
      scenario;

  failing.id =
      "scenario:runner:expected-failure";

  // Keep the expectation itself valid, but require the selected
  // route to have at most 50% major-road share. The actual winner
  // is 100% Primary/Federal and must therefore fail this metric.
  failing.expectations
      .selected_route_metrics
      .back()
      .minimum_value =
          0.0;

  failing.expectations
      .selected_route_metrics
      .back()
      .maximum_value =
          0.50;

  const auto failed =
      run_routing_scenario(
          engine,
          failing);

  assert(!failed.passed);

  bool found_failed_metric = false;

  for (const auto& assertion :
       failed.assertions) {
    if (!assertion.passed &&
        assertion.key ==
            "selected.metric.major_road_share") {
      found_failed_metric = true;
    }
  }

  assert(found_failed_metric);

  std::cout
      << "Scenario runner tests passed\n";

  return 0;
}
