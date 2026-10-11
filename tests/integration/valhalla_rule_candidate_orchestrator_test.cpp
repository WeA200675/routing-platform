#include <cmath>
#include <cstdlib>
#include <fstream>
#include <iostream>
#include <sstream>
#include <string>

#include "routing/adapters/valhalla/valhalla_routing_engine.hpp"
#include "routing/core/candidates/candidate_orchestrator.hpp"
#include "routing/core/candidates/candidate_family_plan.hpp"

namespace {

int fail(
    const std::string& message) {
  std::cerr
      << "FAIL: "
      << message
      << '\n';

  return 1;
}

const char* status_name(
    const routing::core::candidates::
        FamilyRoutingStatus status) {
  using routing::core::candidates::
      FamilyRoutingStatus;

  switch (status) {
    case FamilyRoutingStatus::
        RoutedRepresentativeSelected:
      return "selected";

    case FamilyRoutingStatus::
        RoutedNoRepresentative:
      return "no-representative";

    case FamilyRoutingStatus::
        RoutingFailed:
      return "routing-failed";

    case FamilyRoutingStatus::
        EmptyRoutingResult:
      return "empty";
  }

  return "unknown";
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

  routing::core::RouteRequest request;

  // Reproducible real multi-path corridor.
  // Vaduz -> Ruggell.
  request.origin = {
      47.1410,
      9.5209,
  };

  request.destination = {
      47.2410,
      9.5310,
  };

  request.costing_profile =
      "auto";

  routing::core::VehicleProfile vehicle;
  routing::core::RuleSet rules;
  routing::core::RoutingContext context;

  routing::core::candidates::
      CandidateFamilySelectionPolicy policy;

  // Empty rules intentionally prove that semantic probing does
  // not invent user preferences. For this integration test we
  // then explicitly force every currently implemented specialist
  // family so the full orchestration path is exercised.
  policy.include_fastest_reference = true;
  policy.include_shortest_reference = true;

  policy.forced_families = {
      routing::core::CandidateFamily::MajorRoads,
      routing::core::CandidateFamily::Comfort,
      routing::core::CandidateFamily::LowUrban,
      routing::core::CandidateFamily::LowCurvature,
      routing::core::CandidateFamily::LowGradient,
  };

  routing::core::candidates::
      CandidateOrchestrator orchestrator(
          engine);

  const auto result =
      orchestrator.route(
          request,
          vehicle,
          rules,
          context,
          policy);

  std::cout
      << "REAL RULE-AWARE CANDIDATE ORCHESTRATOR\n";

  std::cout
      << "  rule signals:"
      << " major="
      << result.rule_profile
             .prefer_major_roads_seconds_per_km
      << " residential="
      << result.rule_profile
             .avoid_residential_seconds_per_km
      << " speed30="
      << result.rule_profile
             .avoid_speed_30_seconds_per_km
      << " urban="
      << result.rule_profile
             .avoid_urban_seconds_per_km
      << " curvature="
      << result.rule_profile
             .avoid_curvature_seconds_per_km
      << " serpentine="
      << result.rule_profile
             .avoid_serpentine_seconds_per_km
      << " gradient="
      << result.rule_profile
             .avoid_gradient_seconds_per_km
      << '\n';

  // No rules -> no automatic semantic signal.
  if (result.rule_profile
              .prefer_major_roads_seconds_per_km !=
          0.0 ||
      result.rule_profile
              .avoid_residential_seconds_per_km !=
          0.0 ||
      result.rule_profile
              .avoid_speed_30_seconds_per_km !=
          0.0 ||
      result.rule_profile
              .avoid_urban_seconds_per_km !=
          0.0 ||
      result.rule_profile
              .avoid_curvature_seconds_per_km !=
          0.0 ||
      result.rule_profile
              .avoid_serpentine_seconds_per_km !=
          0.0 ||
      result.rule_profile
              .avoid_gradient_seconds_per_km !=
          0.0) {
    return fail(
        "Empty rules produced a fake candidate preference.");
  }

  // All 8 currently implemented families:
  // Fastest, Shortest, ProfileOptimal, MajorRoads, Comfort,
  // LowUrban, LowCurvature, LowGradient.
  if (result.activations.size() != 8) {
    return fail(
        "Expected 8 active candidate families, got " +
        std::to_string(
            result.activations.size()));
  }

  if (result.family_runs.size() != 8) {
    return fail(
        "Expected 8 family runs, got " +
        std::to_string(
            result.family_runs.size()));
  }

  std::size_t selected_family_count = 0;
  std::size_t routed_family_count = 0;

  for (const auto& run :
       result.family_runs) {
    const std::string family_key(
        routing::core::candidates::
            candidate_family_key(
                run.plan.family));

    std::cout
        << "  "
        << family_key
        << ": "
        << status_name(
               run.status)
        << ", routes="
        << run.routes.size();

    if (!run.routes.empty()) {
      ++routed_family_count;
    }

    if (run.status ==
        routing::core::candidates::
            FamilyRoutingStatus::
                RoutedRepresentativeSelected) {
      ++selected_family_count;
    }

    for (const auto& route :
         run.routes) {
      if (route.segments.empty()) {
        return fail(
            family_key +
            " returned an unenriched route.");
      }

      if (route.segment_ids.size() !=
          route.segments.size()) {
        return fail(
            family_key +
            " segment ordering contract failed.");
      }

      if (route.geometry.size() < 2) {
        return fail(
            family_key +
            " has no geometry.");
      }

      if (route.maneuvers.empty()) {
        return fail(
            family_key +
            " has no maneuvers.");
      }
    }

    std::cout << '\n';
  }

  if (routed_family_count != 8) {
    return fail(
        "Not every implemented family routed successfully.");
  }

  // Attribute data can legitimately make some specialist-family
  // representatives unavailable, therefore do not require all 8.
  if (selected_family_count < 5) {
    return fail(
        "Too few family representatives: " +
        std::to_string(
            selected_family_count));
  }

  if (!result.success) {
    return fail(
        result.error_code +
        " - " +
        result.error_message);
  }

  if (result.generated_route_count < 8) {
    return fail(
        "Expected at least one generated route per family.");
  }

  if (result.unique_representatives.size() < 2) {
    return fail(
        "Real candidate orchestration produced no "
        "physical path diversity.");
  }

  if (!result.selected_unique_index
           .has_value()) {
    return fail(
        "No final unique candidate selected.");
  }

  const auto& winner =
      result.unique_representatives[
          *result.selected_unique_index];

  if (!winner.evaluation.allowed ||
      !winner.evaluation.score_available ||
      !std::isfinite(
          winner.evaluation
              .total_seconds_equivalent)) {
    return fail(
        "Final candidate has invalid CostEngine score.");
  }

  // Final winner must equal the already selected portfolio winner;
  // dedup may merge labels, but it must never alter scoring.
  if (!result.portfolio
           .selected_entry_index
           .has_value()) {
    return fail(
        "Portfolio has no selected entry.");
  }

  const auto& portfolio_winner =
      result.portfolio.entries[
          *result.portfolio
               .selected_entry_index];

  if (std::abs(
          portfolio_winner.evaluation
                  .total_seconds_equivalent -
          winner.evaluation
                  .total_seconds_equivalent) >
      1e-9) {
    return fail(
        "Dedup changed the semantic winning score.");
  }

  std::cout
      << "  generated routes: "
      << result.generated_route_count
      << '\n'
      << "  family representatives: "
      << result.portfolio.entries.size()
      << '\n'
      << "  unique representatives: "
      << result.unique_representatives.size()
      << '\n'
      << "  FINAL: "
      << winner.route.route_id
      << " family-count="
      << winner.represented_families.size()
      << " distance="
      << winner.route.distance_m
      << "m duration="
      << winner.route.duration_s
      << "s score="
      << winner.evaluation
             .total_seconds_equivalent
      << " s-equivalent\n";

  return 0;
}
