#include <cmath>
#include <cstdlib>
#include <fstream>
#include <iostream>
#include <set>
#include <sstream>
#include <string>
#include <vector>

#include "routing/adapters/valhalla/valhalla_routing_engine.hpp"
#include "routing/core/candidates/candidate_portfolio.hpp"
#include "routing/core/candidates/candidate_family_plan.hpp"
#include "routing/core/candidates/family_representative.hpp"
#include "routing/core/evaluation/route_evaluation.hpp"

namespace {

int fail(
    const std::string& message) {
  std::cerr
      << "FAIL: "
      << message
      << '\n';

  return 1;
}

std::string path_signature(
    const routing::core::RoutePath& route) {
  std::ostringstream result;

  for (const auto& id :
       route.segment_ids) {
    result
        << id
        << '|';
  }

  return result.str();
}

const char* status_name(
    const routing::core::candidates::
        FamilyRepresentativeStatus status) {
  using routing::core::candidates::
      FamilyRepresentativeStatus;

  switch (status) {
    case FamilyRepresentativeStatus::Selected:
      return "selected";

    case FamilyRepresentativeStatus::DeferredFamily:
      return "deferred";

    case FamilyRepresentativeStatus::NoCandidates:
      return "no-candidates";

    case FamilyRepresentativeStatus::NoAllowedCandidate:
      return "no-allowed";

    case FamilyRepresentativeStatus::InsufficientData:
      return "insufficient-data";
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

  routing::core::RouteRequest base;

  // Real reproducible multi-route corridor.
  // Vaduz -> Ruggell.
  base.origin = {
      47.1410,
      9.5209,
  };

  base.destination = {
      47.2410,
      9.5310,
  };

  base.costing_profile =
      "auto";

  routing::core::VehicleProfile vehicle;
  routing::core::RuleSet rules;
  routing::core::RoutingContext context;

  std::vector<
      routing::core::candidates::
          FamilyEvaluationPool>
      pools;

  std::set<std::string>
      unique_generated_paths;

  std::size_t implemented_families = 0;
  std::size_t routed_families = 0;
  std::size_t selected_families = 0;

  std::cout
      << "REAL CANDIDATE FAMILY GENERATION\n";

  for (const auto& plan :
       routing::core::candidates::
           all_candidate_family_plans()) {
    const std::string family_key(
        routing::core::candidates::
            candidate_family_key(
                plan.family));

    if (!plan.implemented) {
      std::cout
          << "  "
          << family_key
          << ": deferred\n";

      continue;
    }

    ++implemented_families;

    const auto request =
        routing::core::candidates::
            make_candidate_request(
                base,
                plan);

    const auto result =
        engine.route(
            request);

    if (!result.success) {
      return fail(
          family_key +
          " routing failed: " +
          result.error_code +
          " - " +
          result.error_message);
    }

    if (result.routes.empty()) {
      return fail(
          family_key +
          " returned no routes.");
    }

    ++routed_families;

    routing::core::candidates::
        FamilyEvaluationPool pool;

    pool.plan =
        plan;

    std::cout
        << "  "
        << family_key
        << ": routes="
        << result.routes.size();

    for (const auto& route :
         result.routes) {
      if (route.family !=
          plan.family) {
        return fail(
            family_key +
            " did not preserve CandidateFamily.");
      }

      if (route.geometry.size() < 2) {
        return fail(
            family_key +
            " route has no usable geometry.");
      }

      if (route.maneuvers.empty()) {
        return fail(
            family_key +
            " route has no maneuvers.");
      }

      if (route.segments.empty()) {
        return fail(
            family_key +
            " route has no StreetSegments.");
      }

      if (route.segment_ids.size() !=
          route.segments.size()) {
        return fail(
            family_key +
            " segment ID count mismatch.");
      }

      unique_generated_paths.insert(
          path_signature(
              route));

      const auto evaluation =
          routing::core::evaluation::
              evaluate_route(
                  route,
                  vehicle,
                  rules,
                  context);

      if (!evaluation
               .segment_data_available ||
          !evaluation
               .score_available) {
        return fail(
            family_key +
            " route is not fully evaluable.");
      }

      if (!evaluation.allowed) {
        return fail(
            family_key +
            " route unexpectedly disallowed.");
      }

      if (!std::isfinite(
              evaluation
                  .total_seconds_equivalent) ||
          evaluation
                  .total_seconds_equivalent <=
              0.0) {
        return fail(
            family_key +
            " route has invalid core score.");
      }

      pool.evaluations.push_back(
          evaluation);
    }

    const auto decision =
        routing::core::candidates::
            select_family_representative(
                plan,
                pool.evaluations);

    std::cout
        << ", representative="
        << status_name(
               decision.status);

    if (decision.status ==
        routing::core::candidates::
            FamilyRepresentativeStatus::
                Selected) {
      ++selected_families;

      if (!decision
               .selected_index
               .has_value()) {
        return fail(
            family_key +
            " selected without index.");
      }

      const auto& selected =
          pool.evaluations[
              *decision.selected_index];

      std::cout
          << " "
          << selected.route_id
          << " "
          << selected.reported_distance_m
          << "m/"
          << selected.reported_duration_s
          << "s"
          << " score="
          << selected
                 .total_seconds_equivalent
          << " major="
          << selected.analysis
                 .major_road_distance_m
          << "m"
          << " urban="
          << selected.analysis
                 .urban_distance_m
          << "m";
    }

    std::cout << '\n';

    pools.push_back(
        std::move(pool));
  }

  if (implemented_families != 8) {
    return fail(
        "Unexpected implemented family count: " +
        std::to_string(
            implemented_families));
  }

  if (routed_families !=
      implemented_families) {
    return fail(
        "Not every implemented family routed.");
  }

  // Some attribute-specific families may legitimately be
  // unavailable if the data coverage is insufficient.
  // Core families must nevertheless produce enough candidates
  // for a useful portfolio.
  if (selected_families < 5) {
    return fail(
        "Too few families produced a representative: " +
        std::to_string(
            selected_families));
  }

  // The known Vaduz -> Ruggell fixture must generate genuine
  // route diversity. We test all generated pools, not only
  // family representatives.
  if (unique_generated_paths.size() < 2) {
    return fail(
        "Candidate generation produced no real path diversity.");
  }

  const auto portfolio =
      routing::core::candidates::
          build_candidate_portfolio(
              pools);

  if (portfolio.entries.size() < 5) {
    return fail(
        "Candidate portfolio is too small.");
  }

  if (!portfolio
           .selected_entry_index
           .has_value()) {
    return fail(
        "Candidate portfolio has no final winner.");
  }

  const auto& winner =
      portfolio.entries[
          *portfolio
               .selected_entry_index];

  if (!winner.evaluation.allowed ||
      !winner.evaluation.score_available ||
      !std::isfinite(
          winner.evaluation
              .total_seconds_equivalent)) {
    return fail(
        "Portfolio winner has invalid core score.");
  }

  // Prove the portfolio winner really is minimum CostEngine
  // total across all family representatives.
  for (const auto& entry :
       portfolio.entries) {
    if (entry.evaluation
            .total_seconds_equivalent <
        winner.evaluation
                .total_seconds_equivalent -
            1e-9) {
      return fail(
          "Portfolio winner is not the lowest "
          "RouteEvaluation core cost.");
    }
  }

  std::cout
      << "  unique generated paths: "
      << unique_generated_paths.size()
      << '\n'
      << "  family representatives: "
      << portfolio.entries.size()
      << '\n'
      << "  FINAL: "
      << winner.candidate_key
      << " score="
      << winner.evaluation
             .total_seconds_equivalent
      << " s-equivalent\n";

  return 0;
}
