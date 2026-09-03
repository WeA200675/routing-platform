#pragma once

#include <cstddef>
#include <optional>
#include <string>
#include <vector>

#include "routing/core/candidates/candidate_orchestrator.hpp"
#include "routing/core/testing/route_metric.hpp"

namespace routing::core::testing {

struct ScenarioMetricExpectation {
  RouteMetric metric =
      RouteMetric::MajorRoadShare;

  std::optional<double>
      minimum_value;

  std::optional<double>
      maximum_value;

  // Do not silently interpret missing data as a good metric.
  double minimum_known_coverage = 0.80;
};

struct RoutingScenarioExpectations {
  bool require_orchestration_success = true;
  bool require_selected_allowed = true;

  std::size_t minimum_generated_routes = 1;
  std::size_t minimum_family_representatives = 1;
  std::size_t minimum_unique_representatives = 1;

  std::optional<double>
      maximum_selected_distance_m;

  std::optional<double>
      maximum_selected_duration_s;

  std::vector<ScenarioMetricExpectation>
      selected_route_metrics;
};

struct RoutingScenario {
  std::string id;
  std::string title;

  RouteRequest request;

  VehicleProfile vehicle;
  RuleSet rules;
  RoutingContext context;

  candidates::CandidateFamilySelectionPolicy
      family_policy;

  RoutingScenarioExpectations
      expectations;
};

void validate_routing_scenario(
    const RoutingScenario& scenario);

}  // namespace routing::core::testing
