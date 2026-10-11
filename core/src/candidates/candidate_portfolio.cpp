#include "routing/core/candidates/candidate_portfolio.hpp"

#include <cmath>
#include <string>

namespace routing::core::candidates {

namespace {

constexpr double kCostEpsilon = 1e-9;

bool better_final_candidate(
    const CandidatePortfolioEntry& candidate,
    const CandidatePortfolioEntry& current) {
  const double candidate_cost =
      candidate.evaluation
          .total_seconds_equivalent;

  const double current_cost =
      current.evaluation
          .total_seconds_equivalent;

  if (candidate_cost <
      current_cost -
          kCostEpsilon) {
    return true;
  }

  if (candidate_cost >
      current_cost +
          kCostEpsilon) {
    return false;
  }

  if (candidate.evaluation.reported_duration_s <
      current.evaluation.reported_duration_s -
          kCostEpsilon) {
    return true;
  }

  if (candidate.evaluation.reported_duration_s >
      current.evaluation.reported_duration_s +
          kCostEpsilon) {
    return false;
  }

  if (candidate.evaluation.reported_distance_m <
      current.evaluation.reported_distance_m -
          kCostEpsilon) {
    return true;
  }

  if (candidate.evaluation.reported_distance_m >
      current.evaluation.reported_distance_m +
          kCostEpsilon) {
    return false;
  }

  return candidate.candidate_key <
      current.candidate_key;
}

}  // namespace

CandidatePortfolioSelection
build_candidate_portfolio(
    const std::vector<
        FamilyEvaluationPool>&
        pools) {
  CandidatePortfolioSelection result;

  for (const auto& pool :
       pools) {
    const auto decision =
        select_family_representative(
            pool.plan,
            pool.evaluations);

    if (decision.status !=
            FamilyRepresentativeStatus::
                Selected ||
        !decision.selected_index.has_value()) {
      continue;
    }

    const std::size_t route_index =
        *decision.selected_index;

    if (route_index >=
        pool.evaluations.size()) {
      continue;
    }

    CandidatePortfolioEntry entry;

    entry.family =
        pool.plan.family;

    entry.family_key =
        std::string(
            candidate_family_key(
                pool.plan.family));

    entry.source_route_index =
        route_index;

    entry.evaluation =
        pool.evaluations[
            route_index];

    entry.family_decision =
        decision;

    entry.candidate_key =
        entry.family_key +
        ":" +
        entry.evaluation.route_id;

    result.entries.push_back(
        std::move(entry));
  }

  for (std::size_t index = 0;
       index < result.entries.size();
       ++index) {
    const auto& entry =
        result.entries[index];

    if (!entry.evaluation.allowed ||
        !entry.evaluation.score_available ||
        !std::isfinite(
            entry.evaluation
                .total_seconds_equivalent)) {
      continue;
    }

    if (!result.selected_entry_index
             .has_value()) {
      result.selected_entry_index =
          index;

      continue;
    }

    if (better_final_candidate(
            entry,
            result.entries[
                *result
                     .selected_entry_index])) {
      result.selected_entry_index =
          index;
    }
  }

  if (result.selected_entry_index
          .has_value()) {
    result.reason_key =
        "portfolio.lowest_core_cost";
  } else {
    result.reason_key =
        "portfolio.no_allowed_candidate";
  }

  return result;
}

}  // namespace routing::core::candidates
