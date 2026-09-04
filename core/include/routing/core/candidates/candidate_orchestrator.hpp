#pragma once

#include <cstddef>
#include <optional>
#include <string>
#include <vector>

#include "routing/core/candidates/candidate_portfolio.hpp"
#include "routing/core/candidates/route_identity.hpp"
#include "routing/core/candidates/rule_candidate_profile.hpp"

namespace routing::core::candidates {

enum class FamilyRoutingStatus {
  RoutedRepresentativeSelected = 0,
  RoutedNoRepresentative,
  RoutingFailed,
  EmptyRoutingResult,
};

struct FamilyRoutingRun {
  CandidateFamilyActivation activation;
  CandidateFamilyPlan plan;

  FamilyRoutingStatus status =
      FamilyRoutingStatus::RoutingFailed;

  std::string error_code;
  std::string error_message;

  std::vector<RoutePath> routes;

  std::vector<
      evaluation::RouteEvaluation>
      evaluations;

  // Routes returned by the backend but without usable semantic
  // Street Model data are preserved and counted rather than discarded.
  std::size_t degraded_route_count = 0;

  // A usable route is allowed, has a semantic score and a finite total.
  std::size_t usable_route_count = 0;

  FamilyRepresentativeDecision
      representative;
};

struct UniqueCandidateRepresentative {
  std::string path_signature;

  RoutePath route;

  evaluation::RouteEvaluation evaluation;

  // A physical path may be the representative of more than one
  // family. Preserve that information instead of showing duplicates.
  std::vector<CandidateFamily>
      represented_families;

  std::vector<std::string>
      family_reason_keys;
};

struct CandidateOrchestrationResult {
  bool success = false;

  std::string error_code;
  std::string error_message;

  RuleCandidateProfile rule_profile;

  std::vector<CandidateFamilyActivation>
      activations;

  std::vector<FamilyRoutingRun>
      family_runs;

  CandidatePortfolioSelection
      portfolio;

  std::vector<UniqueCandidateRepresentative>
      unique_representatives;

  std::optional<std::size_t>
      selected_unique_index;

  std::string reason_key;

  std::size_t generated_route_count = 0;

  std::size_t degraded_route_count = 0;
  std::size_t usable_route_count = 0;
};

class CandidateOrchestrator {
 public:
  explicit CandidateOrchestrator(
      const IRoutingEngine& routing_engine);

  [[nodiscard]]
  CandidateOrchestrationResult route(
      const RouteRequest& base_request,
      const VehicleProfile& vehicle,
      const RuleSet& rules,
      const RoutingContext& context,
      const CandidateFamilySelectionPolicy& policy = {}) const;

 private:
  const IRoutingEngine& routing_engine_;
};

}  // namespace routing::core::candidates
