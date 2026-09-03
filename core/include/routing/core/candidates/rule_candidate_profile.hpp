#pragma once

#include <cstddef>
#include <string>
#include <vector>

#include "routing/core/candidates/candidate_family_plan.hpp"
#include "routing/core/cost_engine.hpp"

namespace routing::core::candidates {

// Raw rule impact measured through the existing CostEngine.
//
// raw_rule_seconds contains the sum of CostContribution values,
// deliberately before interpreting the result as a candidate-family
// generation hint.
struct RulePreferenceProbe {
  bool baseline_allowed = true;
  bool variant_allowed = true;

  double baseline_raw_rule_seconds = 0.0;
  double variant_raw_rule_seconds = 0.0;
};

struct RulePreferenceProbeSet {
  // Variant: Primary + FederalRoad
  // Baseline: Secondary + MunicipalRoad
  RulePreferenceProbe major_road;

  // Variant: Residential
  RulePreferenceProbe residential;

  // Variant: speed limit 30 km/h
  RulePreferenceProbe speed_30;

  // Variant: high urban score
  RulePreferenceProbe urban;

  // Variant: high curvature
  RulePreferenceProbe curvature;

  // Variant: high serpentine score
  RulePreferenceProbe serpentine;

  // Variant: steep gradient
  RulePreferenceProbe gradient;
};

// All values are non-negative seconds-equivalent per kilometre.
//
// Infinity means the tested variant/baseline relationship is driven by
// a hard exclusion.
//
// This is NOT a second route score. It only determines which diverse
// candidate families are worth asking the backend to generate.
struct RuleCandidateProfile {
  double prefer_major_roads_seconds_per_km = 0.0;

  double avoid_residential_seconds_per_km = 0.0;
  double avoid_speed_30_seconds_per_km = 0.0;
  double avoid_urban_seconds_per_km = 0.0;
  double avoid_curvature_seconds_per_km = 0.0;
  double avoid_serpentine_seconds_per_km = 0.0;
  double avoid_gradient_seconds_per_km = 0.0;

  RulePreferenceProbeSet probes;
};

struct CandidateFamilySelectionPolicy {
  // ProfileOptimal is always retained because it is the route pool that
  // directly asks our semantic CostEngine for its best representative.
  bool include_fastest_reference = true;
  bool include_shortest_reference = false;

  // Signals below this value do not automatically add a family.
  double activation_threshold_seconds_per_km = 0.5;

  // Applies to automatic additions.
  // Mandatory and explicitly forced families are never silently removed.
  std::size_t max_families = 7;

  // Expert/test mode escape hatch.
  // These are generation requests, not score overrides.
  std::vector<CandidateFamily> forced_families;
};

struct CandidateFamilyActivation {
  CandidateFamily family =
      CandidateFamily::ProfileOptimal;

  // Maximum rule signal responsible for this activation.
  double signal_seconds_per_km = 0.0;

  bool mandatory = false;
  bool forced = false;

  // Stable explanation keys.
  std::vector<std::string> reason_keys;
};

[[nodiscard]]
RuleCandidateProfile compile_rule_candidate_profile(
    const RulePreferenceProbeSet& probes);

[[nodiscard]]
RuleCandidateProfile derive_rule_candidate_profile(
    const VehicleProfile& vehicle,
    const RuleSet& rules,
    const RoutingContext& context);

[[nodiscard]]
std::vector<CandidateFamilyActivation>
select_candidate_families(
    const RuleCandidateProfile& profile,
    const CandidateFamilySelectionPolicy& policy = {});

}  // namespace routing::core::candidates
