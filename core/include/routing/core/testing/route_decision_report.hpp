#pragma once

#include <string>

#include "routing/core/testing/scenario_runner.hpp"

namespace routing::core::testing {

[[nodiscard]]
std::string format_candidate_orchestration_report(
    const candidates::CandidateOrchestrationResult& result);

[[nodiscard]]
std::string format_routing_scenario_report(
    const RoutingScenarioResult& result);

}  // namespace routing::core::testing
