#include <cassert>
#include <cmath>
#include <iostream>
#include <limits>
#include <string>

#include "routing/core/candidates/rule_candidate_profile.hpp"

namespace {

bool contains_reason(
    const routing::core::candidates::
        CandidateFamilyActivation& activation,
    const std::string& reason) {
  for (const auto& candidate :
       activation.reason_keys) {
    if (candidate == reason) {
      return true;
    }
  }

  return false;
}

const routing::core::candidates::
    CandidateFamilyActivation*
find_activation(
    const std::vector<
        routing::core::candidates::
            CandidateFamilyActivation>& activations,
    const routing::core::CandidateFamily family) {
  for (const auto& activation :
       activations) {
    if (activation.family == family) {
      return &activation;
    }
  }

  return nullptr;
}

}  // namespace

int main() {
  using namespace routing::core;
  using namespace routing::core::candidates;

  RulePreferenceProbeSet probes;

  // Primary/Federal is 40 seconds/km better than neutral.
  probes.major_road.baseline_raw_rule_seconds =
      10.0;

  probes.major_road.variant_raw_rule_seconds =
      -30.0;

  // Residential is 70 seconds/km worse.
  probes.residential.baseline_raw_rule_seconds =
      0.0;

  probes.residential.variant_raw_rule_seconds =
      70.0;

  // 30 zone is hard excluded.
  probes.speed_30.baseline_allowed = true;
  probes.speed_30.variant_allowed = false;

  // Curvature is moderately penalized.
  probes.curvature.variant_raw_rule_seconds =
      25.0;

  // Serpentine is more strongly penalized.
  probes.serpentine.variant_raw_rule_seconds =
      55.0;

  // Gradient.
  probes.gradient.variant_raw_rule_seconds =
      35.0;

  // No urban preference in this example.
  probes.urban.variant_raw_rule_seconds =
      0.0;

  const auto profile =
      compile_rule_candidate_profile(
          probes);

  assert(
      profile
          .prefer_major_roads_seconds_per_km ==
      40.0);

  assert(
      profile
          .avoid_residential_seconds_per_km ==
      70.0);

  assert(
      std::isinf(
          profile
              .avoid_speed_30_seconds_per_km));

  assert(
      profile
          .avoid_curvature_seconds_per_km ==
      25.0);

  assert(
      profile
          .avoid_serpentine_seconds_per_km ==
      55.0);

  assert(
      profile
          .avoid_gradient_seconds_per_km ==
      35.0);

  CandidateFamilySelectionPolicy policy;

  policy.include_fastest_reference = true;
  policy.include_shortest_reference = false;
  policy.max_families = 7;

  const auto activations =
      select_candidate_families(
          profile,
          policy);

  const auto* fastest =
      find_activation(
          activations,
          CandidateFamily::Fastest);

  const auto* profile_optimal =
      find_activation(
          activations,
          CandidateFamily::ProfileOptimal);

  const auto* major =
      find_activation(
          activations,
          CandidateFamily::MajorRoads);

  const auto* comfort =
      find_activation(
          activations,
          CandidateFamily::Comfort);

  const auto* curvature =
      find_activation(
          activations,
          CandidateFamily::LowCurvature);

  const auto* gradient =
      find_activation(
          activations,
          CandidateFamily::LowGradient);

  assert(fastest != nullptr);
  assert(profile_optimal != nullptr);
  assert(major != nullptr);
  assert(comfort != nullptr);
  assert(curvature != nullptr);
  assert(gradient != nullptr);

  assert(fastest->mandatory);
  assert(profile_optimal->mandatory);

  assert(
      contains_reason(
          *major,
          "candidate.rule.prefer_major_roads"));

  assert(
      contains_reason(
          *major,
          "candidate.rule.avoid_residential"));

  assert(
      contains_reason(
          *major,
          "candidate.rule.avoid_speed_30"));

  assert(
      contains_reason(
          *curvature,
          "candidate.rule.avoid_serpentine"));

  // No Urban signal -> no LowUrban family.
  assert(
      find_activation(
          activations,
          CandidateFamily::LowUrban) ==
      nullptr);

  // Production CostEngine probing with no rules must not invent
  // preferences.
  VehicleProfile vehicle;
  RuleSet rules;
  RoutingContext context;

  const auto empty_profile =
      derive_rule_candidate_profile(
          vehicle,
          rules,
          context);

  assert(
      empty_profile
          .prefer_major_roads_seconds_per_km ==
      0.0);

  assert(
      empty_profile
          .avoid_residential_seconds_per_km ==
      0.0);

  assert(
      empty_profile
          .avoid_speed_30_seconds_per_km ==
      0.0);

  assert(
      empty_profile
          .avoid_urban_seconds_per_km ==
      0.0);

  assert(
      empty_profile
          .avoid_curvature_seconds_per_km ==
      0.0);

  assert(
      empty_profile
          .avoid_serpentine_seconds_per_km ==
      0.0);

  assert(
      empty_profile
          .avoid_gradient_seconds_per_km ==
      0.0);

  std::cout
      << "Rule candidate profile tests passed\n";

  return 0;
}
