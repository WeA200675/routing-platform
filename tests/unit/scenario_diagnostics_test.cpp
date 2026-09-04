#include <algorithm>
#include <cassert>
#include <iostream>
#include <string>

#include "routing/core/testing/route_decision_report.hpp"
#include "routing/core/testing/scenario_runner.hpp"

namespace {

routing::core::RoutePath make_route(
    const routing::core::RouteRequest& request) {
  using namespace routing::core;

  const std::string suffix =
      std::to_string(
          static_cast<int>(
              request.family));

  RoutePath route;

  route.route_id =
      "diagnostic-route-" +
      suffix;

  route.family =
      request.family;

  route.distance_m =
      1000.0;

  route.duration_s =
      50.0;

  route.geometry = {
      request.origin,
      request.destination,
  };

  StreetSegment segment;

  segment.id =
      "diagnostic-segment-" +
      suffix;

  segment.length_m =
      1000.0;

  segment.functional_road_class =
      FunctionalRoadClass::Primary;

  segment.road_network_class =
      RoadNetworkClass::FederalRoad;

  segment.speed_limit_kmh =
      80.0;

  segment.practical_speed_kmh =
      80.0;

  segment.curvature_score =
      0.10;

  segment.serpentine_score =
      0.10;

  segment.gradient_abs_pct =
      1.0;

  // Fully known, factual zero-positive urban signal.
  segment.urban_score =
      0.10;

  segment.data_confidence =
      1.0;

  route.segments.push_back(
      segment);

  route.segment_ids.push_back(
      segment.id);

  route.engine_name =
      "diagnostic-fake";

  route.engine_version =
      "1";

  route.segment_data_status =
      RouteSegmentDataStatus::Complete;

  return route;
}

class FakeRoutingEngine final
    : public routing::core::IRoutingEngine {
 public:
  [[nodiscard]]
  std::string name() const override {
    return "diagnostic-fake";
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
    routing::core::RoutingResult result;

    result.success = true;

    result.routes.push_back(
        make_route(request));

    return result;
  }
};

bool has_code(
    const routing::core::testing::
        RoutingScenarioResult& result,
    const std::string& code) {
  return std::any_of(
      result.diagnostics.begin(),
      result.diagnostics.end(),
      [&](const auto& diagnostic) {
        return diagnostic.code ==
            code;
      });
}

}  // namespace

int main() {
  using namespace routing::core;
  using namespace routing::core::testing;

  FakeRoutingEngine engine;

  RoutingScenario scenario;

  scenario.id =
      "diagnostics:observer-only";

  scenario.title =
      "Diagnostics observer-only invariant";

  scenario.request.origin = {
      47.1410,
      9.5209,
  };

  scenario.request.destination = {
      47.1510,
      9.5209,
  };

  scenario.request.costing_profile =
      "auto";

  // Diagnostics must never become implicit regression assertions.
  const auto result =
      run_routing_scenario(
          engine,
          scenario);

  assert(
      result.orchestration.success);

  assert(
      result.passed);

  assert(
      has_code(
          result,
          "DATA_URBAN_POSITIVE_SIGNAL_ABSENT"));

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
      winner.evaluation
          .score_available);

  // Route Lab uses this same report path, therefore diagnostic
  // visibility here also verifies Route Lab visibility.
  const std::string report =
      format_routing_scenario_report(
          result);

  assert(
      report.find("diagnostics") !=
      std::string::npos);

  assert(
      report.find(
          "DATA_URBAN_POSITIVE_SIGNAL_ABSENT") !=
      std::string::npos);

  assert(
      report.find(
          "degraded routes:") !=
      std::string::npos);

  assert(
      report.find(
          "usable routes:") !=
      std::string::npos);

  std::cout
      << "Scenario diagnostics tests passed\n";

  return 0;
}
