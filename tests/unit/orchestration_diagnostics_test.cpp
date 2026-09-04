#include <algorithm>
#include <cassert>
#include <iostream>
#include <string>
#include <vector>

#include "routing/core/diagnostics/route_diagnostics.hpp"

namespace {

bool has_code(
    const std::vector<
        routing::core::diagnostics::RoutingDiagnostic>& diagnostics,
    const std::string& code) {
  return std::any_of(
      diagnostics.begin(),
      diagnostics.end(),
      [&](const auto& diagnostic) {
        return diagnostic.code ==
            code;
      });
}

const routing::core::diagnostics::RoutingDiagnostic*
find_code(
    const std::vector<
        routing::core::diagnostics::RoutingDiagnostic>& diagnostics,
    const std::string& code) {
  const auto found =
      std::find_if(
          diagnostics.begin(),
          diagnostics.end(),
          [&](const auto& diagnostic) {
            return diagnostic.code ==
                code;
          });

  return found == diagnostics.end()
      ? nullptr
      : &*found;
}

}  // namespace

int main() {
  using namespace routing::core;
  using namespace routing::core::candidates;
  using namespace routing::core::diagnostics;

  CandidateOrchestrationResult result;

  // Overall orchestration may succeed despite one family failing.
  result.success = true;

  result.generated_route_count = 2;
  result.degraded_route_count = 1;
  result.usable_route_count = 1;

  FamilyRoutingRun failed_family;

  failed_family.plan.family =
      CandidateFamily::LowUrban;

  failed_family.status =
      FamilyRoutingStatus::RoutingFailed;

  failed_family.error_code =
      "SYNTHETIC_FAILURE";

  failed_family.error_message =
      "Synthetic family failure.";

  result.family_runs.push_back(
      failed_family);

  const auto diagnostics =
      collect_candidate_orchestration_diagnostics(
          result);

  assert(
      has_code(
          diagnostics,
          "CANDIDATE_SET_DEGRADED_ROUTES"));

  assert(
      has_code(
          diagnostics,
          "CANDIDATE_FAMILY_ROUTING_FAILED"));

  assert(
      !has_code(
          diagnostics,
          "CANDIDATE_SET_NO_USABLE_ROUTE"));

  // One family failure is observational and does not imply that the
  // complete orchestration failed.
  assert(result.success);

  auto unusable =
      result;

  unusable.usable_route_count = 0;

  const auto unusable_diagnostics =
      collect_candidate_orchestration_diagnostics(
          unusable);

  const auto* no_usable =
      find_code(
          unusable_diagnostics,
          "CANDIDATE_SET_NO_USABLE_ROUTE");

  assert(no_usable != nullptr);

  assert(
      no_usable->severity ==
      DiagnosticSeverity::Error);

  std::cout
      << "Orchestration diagnostics tests passed\n";

  return 0;
}
