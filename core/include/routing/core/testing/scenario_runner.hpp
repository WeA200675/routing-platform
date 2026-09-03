#pragma once

#include <string>
#include <vector>

#include "routing/core/testing/routing_scenario.hpp"

namespace routing::core::testing {

struct ScenarioAssertionResult {
  bool passed = false;

  // Stable machine-readable key.
  std::string key;

  std::string detail;
};

struct RoutingScenarioResult {
  std::string scenario_id;

  bool passed = false;

  candidates::CandidateOrchestrationResult
      orchestration;

  std::vector<ScenarioAssertionResult>
      assertions;
};

[[nodiscard]]
RoutingScenarioResult run_routing_scenario(
    const IRoutingEngine& routing_engine,
    const RoutingScenario& scenario);

}  // namespace routing::core::testing
