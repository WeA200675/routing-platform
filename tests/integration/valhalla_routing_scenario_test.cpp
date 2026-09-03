#include <cstdlib>
#include <fstream>
#include <iostream>
#include <sstream>
#include <string>

#include "routing/adapters/valhalla/valhalla_routing_engine.hpp"
#include "routing/core/candidates/candidate_family_plan.hpp"
#include "routing/core/testing/route_decision_report.hpp"

namespace {

int fail(
    const std::string& message) {
  std::cerr
      << "FAIL: "
      << message
      << '\n';

  return 1;
}

}  // namespace

int main() {
  const char* config_path =
      std::getenv(
          "ROUTING_PLATFORM_VALHALLA_TEST_CONFIG");

  if (config_path == nullptr ||
      std::string(config_path).empty()) {
    std::cout
        << "SKIP: ROUTING_PLATFORM_VALHALLA_TEST_CONFIG "
        << "is not set.\n";

    return 77;
  }

  std::ifstream config_file(
      config_path);

  if (!config_file) {
    return fail(
        std::string(
            "Could not open Valhalla config: ") +
        config_path);
  }

  std::ostringstream config_buffer;
  config_buffer << config_file.rdbuf();

  routing::adapters::valhalla::
      ValhallaRoutingEngine engine(
          {config_buffer.str()});

  if (!engine.ready()) {
    return fail(
        "ValhallaRoutingEngine is not ready.");
  }

  using namespace routing::core;
  using namespace routing::core::candidates;
  using namespace routing::core::testing;

  RoutingScenario scenario;

  scenario.id =
      "li:vadz-ruggell:candidate-regression-v1";

  scenario.title =
      "Vaduz to Ruggell candidate regression";

  scenario.request.origin = {
      47.1410,
      9.5209,
  };

  scenario.request.destination = {
      47.2410,
      9.5310,
  };

  scenario.request.costing_profile =
      "auto";

  scenario.family_policy
      .include_fastest_reference =
          true;

  scenario.family_policy
      .include_shortest_reference =
          true;

  // Exercise every currently implemented specialist family.
  for (const auto& plan :
       all_candidate_family_plans()) {
    if (!plan.implemented) {
      continue;
    }

    if (plan.family ==
            CandidateFamily::Fastest ||
        plan.family ==
            CandidateFamily::Shortest ||
        plan.family ==
            CandidateFamily::ProfileOptimal) {
      continue;
    }

    scenario.family_policy
        .forced_families
        .push_back(
            plan.family);
  }

  scenario.expectations
      .minimum_generated_routes =
          8;

  scenario.expectations
      .minimum_family_representatives =
          5;

  scenario.expectations
      .minimum_unique_representatives =
          2;

  // Broad safety rails, not a frozen route geometry.
  scenario.expectations
      .maximum_selected_distance_m =
          25000.0;

  scenario.expectations
      .maximum_selected_duration_s =
          1800.0;

  ScenarioMetricExpectation road_data;

  road_data.metric =
      RouteMetric::KnownRoadClassCoverage;

  road_data.minimum_value = 0.80;
  road_data.minimum_known_coverage = 1.0;

  scenario.expectations
      .selected_route_metrics
      .push_back(
          road_data);

  const auto result =
      run_routing_scenario(
          engine,
          scenario);

  std::cout
      << format_routing_scenario_report(
             result);

  if (!result.passed) {
    return fail(
        "Real routing scenario regression failed.");
  }

  if (!result.orchestration
           .selected_unique_index
           .has_value()) {
    return fail(
        "Scenario passed without a selected route.");
  }

  return 0;
}
