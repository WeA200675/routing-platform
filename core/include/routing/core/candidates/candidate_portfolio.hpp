#pragma once

#include <cstddef>
#include <optional>
#include <string>
#include <vector>

#include "routing/core/candidates/family_representative.hpp"

namespace routing::core::candidates {

struct FamilyEvaluationPool {
  CandidateFamilyPlan plan;

  std::vector<
      evaluation::RouteEvaluation>
      evaluations;
};

struct CandidatePortfolioEntry {
  CandidateFamily family =
      CandidateFamily::ProfileOptimal;

  std::string family_key;

  // Unique within the portfolio even when backend route IDs
  // restart at e.g. "valhalla-0" for each request.
  std::string candidate_key;

  std::size_t source_route_index = 0;

  evaluation::RouteEvaluation evaluation;

  FamilyRepresentativeDecision
      family_decision;
};

struct CandidatePortfolioSelection {
  std::vector<CandidatePortfolioEntry>
      entries;

  std::optional<std::size_t>
      selected_entry_index;

  // The final portfolio winner is intentionally selected
  // ONLY from RouteEvaluation total cost, i.e. the existing
  // CostEngine aggregation.
  std::string reason_key;
};

[[nodiscard]]
CandidatePortfolioSelection
build_candidate_portfolio(
    const std::vector<
        FamilyEvaluationPool>&
        pools);

}  // namespace routing::core::candidates
