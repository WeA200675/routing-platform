#include <cassert>
#include <iostream>
#include <stdexcept>

#include "routing/core/testing/routing_scenario.hpp"

int main() {
  using namespace routing::core;
  using namespace routing::core::testing;

  RoutingScenario scenario;

  scenario.id =
      "scenario:test";

  scenario.title =
      "Validation Test";

  scenario.request.origin = {
      47.1410,
      9.5209,
  };

  scenario.request.destination = {
      47.2410,
      9.5310,
  };

  scenario.expectations
      .maximum_selected_distance_m =
          25000.0;

  ScenarioMetricExpectation metric;

  metric.metric =
      RouteMetric::MajorRoadShare;

  metric.minimum_value = 0.50;
  metric.minimum_known_coverage = 0.80;

  scenario.expectations
      .selected_route_metrics
      .push_back(metric);

  validate_routing_scenario(
      scenario);

  {
    auto invalid =
        scenario;

    invalid.id.clear();

    bool rejected = false;

    try {
      validate_routing_scenario(
          invalid);
    } catch (
        const std::invalid_argument&) {
      rejected = true;
    }

    assert(rejected);
  }

  {
    auto invalid =
        scenario;

    invalid.request.origin.latitude =
        100.0;

    bool rejected = false;

    try {
      validate_routing_scenario(
          invalid);
    } catch (
        const std::invalid_argument&) {
      rejected = true;
    }

    assert(rejected);
  }

  {
    auto invalid =
        scenario;

    invalid.expectations
        .selected_route_metrics
        .front()
        .minimum_known_coverage =
            1.5;

    bool rejected = false;

    try {
      validate_routing_scenario(
          invalid);
    } catch (
        const std::invalid_argument&) {
      rejected = true;
    }

    assert(rejected);
  }

  {
    auto invalid =
        scenario;

    auto& expectation =
        invalid.expectations
            .selected_route_metrics
            .front();

    expectation.minimum_value = 0.80;
    expectation.maximum_value = 0.40;

    bool rejected = false;

    try {
      validate_routing_scenario(
          invalid);
    } catch (
        const std::invalid_argument&) {
      rejected = true;
    }

    assert(rejected);
  }

  std::cout
      << "Routing scenario validation tests passed\n";

  return 0;
}
