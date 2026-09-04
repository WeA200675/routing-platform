#include "routing/core/candidates/candidate_orchestrator.hpp"

#include <algorithm>
#include <cmath>
#include <string>
#include <unordered_map>
#include <utility>

namespace routing::core::candidates {

namespace {

void append_family_once(
    std::vector<CandidateFamily>& families,
    const CandidateFamily family) {
  if (std::find(
          families.begin(),
          families.end(),
          family) ==
      families.end()) {
    families.push_back(
        family);
  }
}

void append_reason_once(
    std::vector<std::string>& reasons,
    const std::string& reason) {
  if (std::find(
          reasons.begin(),
          reasons.end(),
          reason) ==
      reasons.end()) {
    reasons.push_back(
        reason);
  }
}

const FamilyRoutingRun* find_run(
    const std::vector<FamilyRoutingRun>& runs,
    const CandidateFamily family) {
  for (const auto& run :
       runs) {
    if (run.plan.family == family) {
      return &run;
    }
  }

  return nullptr;
}

std::optional<std::size_t>
find_unique_by_signature(
    const std::vector<
        UniqueCandidateRepresentative>& candidates,
    const std::string& signature) {
  for (std::size_t index = 0;
       index < candidates.size();
       ++index) {
    if (candidates[index]
            .path_signature ==
        signature) {
      return index;
    }
  }

  return std::nullopt;
}

}  // namespace

CandidateOrchestrator::CandidateOrchestrator(
    const IRoutingEngine& routing_engine)
    : routing_engine_(
          routing_engine) {}

CandidateOrchestrationResult
CandidateOrchestrator::route(
    const RouteRequest& base_request,
    const VehicleProfile& vehicle,
    const RuleSet& rules,
    const RoutingContext& context,
    const CandidateFamilySelectionPolicy& policy) const {
  CandidateOrchestrationResult result;

  if (!routing_engine_.ready()) {
    result.error_code =
        "ROUTING_ENGINE_NOT_READY";

    result.error_message =
        "Candidate orchestrator routing engine is not ready.";

    return result;
  }

  result.rule_profile =
      derive_rule_candidate_profile(
          vehicle,
          rules,
          context);

  result.activations =
      select_candidate_families(
          result.rule_profile,
          policy);

  std::vector<FamilyEvaluationPool>
      pools;

  for (const auto& activation :
       result.activations) {
    FamilyRoutingRun run;

    run.activation =
        activation;

    run.plan =
        candidate_family_plan(
            activation.family);

    RouteRequest request;

    try {
      request =
          make_candidate_request(
              base_request,
              run.plan);
    } catch (const std::exception& error) {
      run.status =
          FamilyRoutingStatus::
              RoutingFailed;

      run.error_code =
          "CANDIDATE_REQUEST_FAILED";

      run.error_message =
          error.what();

      result.family_runs.push_back(
          std::move(run));

      continue;
    }

    const auto routing_result =
        routing_engine_.route(
            request);

    if (!routing_result.success) {
      run.status =
          FamilyRoutingStatus::
              RoutingFailed;

      run.error_code =
          routing_result.error_code;

      run.error_message =
          routing_result.error_message;

      result.family_runs.push_back(
          std::move(run));

      continue;
    }

    if (routing_result.routes.empty()) {
      run.status =
          FamilyRoutingStatus::
              EmptyRoutingResult;

      run.error_code =
          "CANDIDATE_EMPTY_RESULT";

      run.error_message =
          "Routing backend returned no candidate routes.";

      result.family_runs.push_back(
          std::move(run));

      continue;
    }

    run.routes =
        routing_result.routes;

    result.generated_route_count +=
        run.routes.size();

    run.evaluations.reserve(
        run.routes.size());

    for (const auto& route :
         run.routes) {
      auto route_evaluation =
          evaluation::evaluate_route(
              route,
              vehicle,
              rules,
              context);

      if (route.segment_data_status ==
          RouteSegmentDataStatus::Unavailable) {
        ++run.degraded_route_count;
        ++result.degraded_route_count;
      }

      if (route_evaluation.allowed &&
          route_evaluation.score_available &&
          std::isfinite(
              route_evaluation
                  .total_seconds_equivalent)) {
        ++run.usable_route_count;
        ++result.usable_route_count;
      }

      run.evaluations.push_back(
          std::move(route_evaluation));
    }

    run.representative =
        select_family_representative(
            run.plan,
            run.evaluations);

    if (run.representative.status ==
        FamilyRepresentativeStatus::
            Selected) {
      run.status =
          FamilyRoutingStatus::
              RoutedRepresentativeSelected;
    } else {
      run.status =
          FamilyRoutingStatus::
              RoutedNoRepresentative;

      if (run.usable_route_count == 0 &&
          run.degraded_route_count > 0) {
        run.error_code =
            "CANDIDATE_NO_USABLE_ENRICHED_ROUTE";

        run.error_message =
            "Routing backend returned route geometry, but no "
            "candidate had usable semantic segment data.";
      }
    }

    FamilyEvaluationPool pool;

    pool.plan =
        run.plan;

    pool.evaluations =
        run.evaluations;

    pools.push_back(
        std::move(pool));

    result.family_runs.push_back(
        std::move(run));
  }

  result.portfolio =
      build_candidate_portfolio(
          pools);

  // Convert portfolio representatives back to concrete RoutePath objects
  // and deduplicate physical paths across candidate families.
  for (const auto& entry :
       result.portfolio.entries) {
    const auto* run =
        find_run(
            result.family_runs,
            entry.family);

    if (run == nullptr ||
        entry.source_route_index >=
            run->routes.size()) {
      continue;
    }

    const auto& route =
        run->routes[
            entry.source_route_index];

    const std::string signature =
        route_path_signature(
            route);

    const auto existing_index =
        find_unique_by_signature(
            result.unique_representatives,
            signature);

    if (existing_index.has_value()) {
      auto& existing =
          result.unique_representatives[
              *existing_index];

      append_family_once(
          existing.represented_families,
          entry.family);

      for (const auto& reason :
           run->activation.reason_keys) {
        append_reason_once(
            existing.family_reason_keys,
            reason);
      }

      continue;
    }

    UniqueCandidateRepresentative candidate;

    candidate.path_signature =
        signature;

    candidate.route =
        route;

    candidate.evaluation =
        entry.evaluation;

    candidate.represented_families.push_back(
        entry.family);

    candidate.family_reason_keys =
        run->activation.reason_keys;

    result.unique_representatives.push_back(
        std::move(candidate));
  }

  if (!result.portfolio
           .selected_entry_index
           .has_value()) {
    result.error_code =
        "NO_ALLOWED_CANDIDATE";

    result.error_message =
        "No generated candidate passed semantic route evaluation.";

    result.reason_key =
        "orchestrator.no_allowed_candidate";

    return result;
  }

  const auto& selected_entry =
      result.portfolio.entries[
          *result.portfolio
               .selected_entry_index];

  const auto* selected_run =
      find_run(
          result.family_runs,
          selected_entry.family);

  if (selected_run == nullptr ||
      selected_entry.source_route_index >=
          selected_run->routes.size()) {
    result.error_code =
        "CANDIDATE_MAPPING_FAILED";

    result.error_message =
        "Could not map selected portfolio entry back to its route.";

    return result;
  }

  const std::string selected_signature =
      route_path_signature(
          selected_run->routes[
              selected_entry.source_route_index]);

  result.selected_unique_index =
      find_unique_by_signature(
          result.unique_representatives,
          selected_signature);

  if (!result.selected_unique_index
           .has_value()) {
    result.error_code =
        "CANDIDATE_DEDUP_MAPPING_FAILED";

    result.error_message =
        "Selected route disappeared during path deduplication.";

    return result;
  }

  const auto& winner =
      result.unique_representatives[
          *result.selected_unique_index];

  if (!winner.evaluation.allowed ||
      !winner.evaluation.score_available ||
      !std::isfinite(
          winner.evaluation
              .total_seconds_equivalent)) {
    result.error_code =
        "INVALID_SELECTED_CANDIDATE";

    result.error_message =
        "Selected candidate has no valid semantic CostEngine score.";

    return result;
  }

  result.success = true;

  result.reason_key =
      "orchestrator.portfolio_lowest_core_cost";

  return result;
}

}  // namespace routing::core::candidates
