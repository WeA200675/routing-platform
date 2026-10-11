#include "routing/core/candidates/rule_candidate_profile.hpp"

#include <algorithm>
#include <cmath>
#include <limits>
#include <stdexcept>
#include <utility>
#include <vector>

namespace routing::core::candidates {

namespace {

constexpr double kProbeLengthM = 1000.0;

struct ProbeScore {
  bool allowed = true;
  double raw_rule_seconds = 0.0;
};

double raw_rule_seconds(
    const SegmentCost& cost) {
  double result = 0.0;

  for (const auto& contribution :
       cost.contributions) {
    result +=
        contribution.seconds_equivalent;
  }

  return result;
}

ProbeScore evaluate_probe_segment(
    const StreetSegment& segment,
    const VehicleProfile& vehicle,
    const RuleSet& rules,
    const RoutingContext& context) {
  CostEngine engine;

  const auto cost =
      engine.evaluate(
          segment,
          vehicle,
          rules,
          context);

  ProbeScore result;
  result.allowed = cost.allowed;
  result.raw_rule_seconds =
      raw_rule_seconds(cost);

  return result;
}

StreetSegment neutral_segment() {
  StreetSegment segment;

  segment.id =
      "candidate-rule-probe";

  segment.length_m =
      kProbeLengthM;

  segment.access_allowed = true;
  segment.hard_user_excluded = false;

  // Secondary is intentionally neither our major-road group
  // nor one of the explicitly minor Residential/Service/Track classes.
  segment.functional_road_class =
      FunctionalRoadClass::Secondary;

  segment.road_network_class =
      RoadNetworkClass::MunicipalRoad;

  segment.speed_limit_kmh = 80.0;
  segment.practical_speed_kmh = 80.0;

  segment.curvature_score = 0.10;
  segment.serpentine_score = 0.10;
  segment.gradient_abs_pct = 2.0;
  segment.urban_score = 0.10;

  segment.data_confidence = 1.0;

  return segment;
}

RulePreferenceProbe make_probe(
    const StreetSegment& baseline,
    const StreetSegment& variant,
    const VehicleProfile& vehicle,
    const RuleSet& rules,
    const RoutingContext& context) {
  const auto baseline_score =
      evaluate_probe_segment(
          baseline,
          vehicle,
          rules,
          context);

  const auto variant_score =
      evaluate_probe_segment(
          variant,
          vehicle,
          rules,
          context);

  RulePreferenceProbe result;

  result.baseline_allowed =
      baseline_score.allowed;

  result.variant_allowed =
      variant_score.allowed;

  result.baseline_raw_rule_seconds =
      baseline_score.raw_rule_seconds;

  result.variant_raw_rule_seconds =
      variant_score.raw_rule_seconds;

  return result;
}

// Variant represents something the user may want to avoid.
double avoidance_signal(
    const RulePreferenceProbe& probe) {
  if (!probe.baseline_allowed) {
    // Baseline itself is excluded. The pair no longer gives us an
    // isolated directional preference.
    return 0.0;
  }

  if (!probe.variant_allowed) {
    return std::numeric_limits<double>::infinity();
  }

  return std::max(
      0.0,
      probe.variant_raw_rule_seconds -
          probe.baseline_raw_rule_seconds);
}

// Variant represents something the user may want to prefer.
double preferred_variant_signal(
    const RulePreferenceProbe& probe) {
  if (!probe.variant_allowed) {
    return 0.0;
  }

  if (!probe.baseline_allowed) {
    return std::numeric_limits<double>::infinity();
  }

  return std::max(
      0.0,
      probe.baseline_raw_rule_seconds -
          probe.variant_raw_rule_seconds);
}

void append_reason(
    CandidateFamilyActivation& activation,
    const std::string& reason) {
  if (std::find(
          activation.reason_keys.begin(),
          activation.reason_keys.end(),
          reason) ==
      activation.reason_keys.end()) {
    activation.reason_keys.push_back(
        reason);
  }
}

CandidateFamilyActivation* find_activation(
    std::vector<CandidateFamilyActivation>& activations,
    const CandidateFamily family) {
  for (auto& activation :
       activations) {
    if (activation.family == family) {
      return &activation;
    }
  }

  return nullptr;
}

void upsert_activation(
    std::vector<CandidateFamilyActivation>& activations,
    const CandidateFamily family,
    const double signal,
    const bool mandatory,
    const bool forced,
    const std::string& reason) {
  if (auto* existing =
          find_activation(
              activations,
              family)) {
    existing->signal_seconds_per_km =
        std::max(
            existing->signal_seconds_per_km,
            signal);

    existing->mandatory =
        existing->mandatory ||
        mandatory;

    existing->forced =
        existing->forced ||
        forced;

    append_reason(
        *existing,
        reason);

    return;
  }

  CandidateFamilyActivation activation;

  activation.family = family;
  activation.signal_seconds_per_km =
      signal;

  activation.mandatory = mandatory;
  activation.forced = forced;

  activation.reason_keys.push_back(
      reason);

  activations.push_back(
      std::move(activation));
}

struct AutomaticCandidate {
  CandidateFamily family =
      CandidateFamily::ProfileOptimal;

  double signal = 0.0;
  std::vector<std::string> reasons;
};

void add_reason_if_active(
    std::vector<std::string>& reasons,
    const double signal,
    const double threshold,
    const char* reason) {
  if (signal >= threshold) {
    reasons.emplace_back(reason);
  }
}

}  // namespace

RuleCandidateProfile compile_rule_candidate_profile(
    const RulePreferenceProbeSet& probes) {
  RuleCandidateProfile result;

  result.probes = probes;

  result.prefer_major_roads_seconds_per_km =
      preferred_variant_signal(
          probes.major_road);

  result.avoid_residential_seconds_per_km =
      avoidance_signal(
          probes.residential);

  result.avoid_speed_30_seconds_per_km =
      avoidance_signal(
          probes.speed_30);

  result.avoid_urban_seconds_per_km =
      avoidance_signal(
          probes.urban);

  result.avoid_curvature_seconds_per_km =
      avoidance_signal(
          probes.curvature);

  result.avoid_serpentine_seconds_per_km =
      avoidance_signal(
          probes.serpentine);

  result.avoid_gradient_seconds_per_km =
      avoidance_signal(
          probes.gradient);

  return result;
}

RuleCandidateProfile derive_rule_candidate_profile(
    const VehicleProfile& vehicle,
    const RuleSet& rules,
    const RoutingContext& context) {
  const auto baseline =
      neutral_segment();

  RulePreferenceProbeSet probes;

  {
    auto major = baseline;

    major.functional_road_class =
        FunctionalRoadClass::Primary;

    major.road_network_class =
        RoadNetworkClass::FederalRoad;

    probes.major_road =
        make_probe(
            baseline,
            major,
            vehicle,
            rules,
            context);
  }

  {
    auto residential = baseline;

    residential.functional_road_class =
        FunctionalRoadClass::Residential;

    probes.residential =
        make_probe(
            baseline,
            residential,
            vehicle,
            rules,
            context);
  }

  {
    auto speed_30 = baseline;

    speed_30.speed_limit_kmh = 30.0;
    speed_30.practical_speed_kmh = 30.0;

    probes.speed_30 =
        make_probe(
            baseline,
            speed_30,
            vehicle,
            rules,
            context);
  }

  {
    auto urban = baseline;

    urban.urban_score = 0.90;

    probes.urban =
        make_probe(
            baseline,
            urban,
            vehicle,
            rules,
            context);
  }

  {
    auto curvature = baseline;

    curvature.curvature_score = 0.90;

    probes.curvature =
        make_probe(
            baseline,
            curvature,
            vehicle,
            rules,
            context);
  }

  {
    auto serpentine = baseline;

    serpentine.serpentine_score = 0.90;

    probes.serpentine =
        make_probe(
            baseline,
            serpentine,
            vehicle,
            rules,
            context);
  }

  {
    auto gradient = baseline;

    gradient.gradient_abs_pct = 12.0;

    probes.gradient =
        make_probe(
            baseline,
            gradient,
            vehicle,
            rules,
            context);
  }

  return compile_rule_candidate_profile(
      probes);
}

std::vector<CandidateFamilyActivation>
select_candidate_families(
    const RuleCandidateProfile& profile,
    const CandidateFamilySelectionPolicy& policy) {
  if (!std::isfinite(
          policy.activation_threshold_seconds_per_km) ||
      policy.activation_threshold_seconds_per_km < 0.0) {
    throw std::invalid_argument(
        "Candidate family activation threshold "
        "must be finite and non-negative.");
  }

  std::vector<CandidateFamilyActivation>
      result;

  // ProfileOptimal is always included.
  upsert_activation(
      result,
      CandidateFamily::ProfileOptimal,
      0.0,
      true,
      false,
      "candidate.activation.profile_optimal");

  if (policy.include_fastest_reference) {
    upsert_activation(
        result,
        CandidateFamily::Fastest,
        0.0,
        true,
        false,
        "candidate.activation.fastest_reference");
  }

  if (policy.include_shortest_reference) {
    upsert_activation(
        result,
        CandidateFamily::Shortest,
        0.0,
        true,
        false,
        "candidate.activation.shortest_reference");
  }

  for (const auto family :
       policy.forced_families) {
    const auto plan =
        candidate_family_plan(
            family);

    if (!plan.implemented) {
      throw std::invalid_argument(
          "Cannot force deferred candidate family: " +
          std::string(
              candidate_family_key(
                  family)));
    }

    upsert_activation(
        result,
        family,
        0.0,
        false,
        true,
        "candidate.activation.forced");
  }

  const double threshold =
      policy.activation_threshold_seconds_per_km;

  std::vector<AutomaticCandidate>
      automatic;

  {
    AutomaticCandidate candidate;

    candidate.family =
        CandidateFamily::MajorRoads;

    candidate.signal =
        std::max({
            profile
                .prefer_major_roads_seconds_per_km,
            profile
                .avoid_residential_seconds_per_km,
            profile
                .avoid_speed_30_seconds_per_km,
        });

    add_reason_if_active(
        candidate.reasons,
        profile
            .prefer_major_roads_seconds_per_km,
        threshold,
        "candidate.rule.prefer_major_roads");

    add_reason_if_active(
        candidate.reasons,
        profile
            .avoid_residential_seconds_per_km,
        threshold,
        "candidate.rule.avoid_residential");

    add_reason_if_active(
        candidate.reasons,
        profile
            .avoid_speed_30_seconds_per_km,
        threshold,
        "candidate.rule.avoid_speed_30");

    automatic.push_back(
        std::move(candidate));
  }

  {
    AutomaticCandidate candidate;

    candidate.family =
        CandidateFamily::Comfort;

    candidate.signal =
        std::max({
            profile
                .avoid_residential_seconds_per_km,
            profile
                .avoid_speed_30_seconds_per_km,
            profile
                .avoid_curvature_seconds_per_km,
            profile
                .avoid_serpentine_seconds_per_km,
            profile
                .avoid_gradient_seconds_per_km,
        });

    add_reason_if_active(
        candidate.reasons,
        profile
            .avoid_residential_seconds_per_km,
        threshold,
        "candidate.rule.avoid_residential");

    add_reason_if_active(
        candidate.reasons,
        profile
            .avoid_speed_30_seconds_per_km,
        threshold,
        "candidate.rule.avoid_speed_30");

    add_reason_if_active(
        candidate.reasons,
        profile
            .avoid_curvature_seconds_per_km,
        threshold,
        "candidate.rule.avoid_curvature");

    add_reason_if_active(
        candidate.reasons,
        profile
            .avoid_serpentine_seconds_per_km,
        threshold,
        "candidate.rule.avoid_serpentine");

    add_reason_if_active(
        candidate.reasons,
        profile
            .avoid_gradient_seconds_per_km,
        threshold,
        "candidate.rule.avoid_gradient");

    automatic.push_back(
        std::move(candidate));
  }

  {
    AutomaticCandidate candidate;

    candidate.family =
        CandidateFamily::LowUrban;

    candidate.signal =
        profile
            .avoid_urban_seconds_per_km;

    add_reason_if_active(
        candidate.reasons,
        candidate.signal,
        threshold,
        "candidate.rule.avoid_urban");

    automatic.push_back(
        std::move(candidate));
  }

  {
    AutomaticCandidate candidate;

    candidate.family =
        CandidateFamily::LowCurvature;

    candidate.signal =
        std::max(
            profile
                .avoid_curvature_seconds_per_km,
            profile
                .avoid_serpentine_seconds_per_km);

    add_reason_if_active(
        candidate.reasons,
        profile
            .avoid_curvature_seconds_per_km,
        threshold,
        "candidate.rule.avoid_curvature");

    add_reason_if_active(
        candidate.reasons,
        profile
            .avoid_serpentine_seconds_per_km,
        threshold,
        "candidate.rule.avoid_serpentine");

    automatic.push_back(
        std::move(candidate));
  }

  {
    AutomaticCandidate candidate;

    candidate.family =
        CandidateFamily::LowGradient;

    candidate.signal =
        profile
            .avoid_gradient_seconds_per_km;

    add_reason_if_active(
        candidate.reasons,
        candidate.signal,
        threshold,
        "candidate.rule.avoid_gradient");

    automatic.push_back(
        std::move(candidate));
  }

  std::sort(
      automatic.begin(),
      automatic.end(),
      [](const AutomaticCandidate& left,
         const AutomaticCandidate& right) {
        if (left.signal !=
            right.signal) {
          return left.signal >
              right.signal;
        }

        return static_cast<int>(
                   left.family) <
            static_cast<int>(
                right.family);
      });

  for (const auto& candidate :
       automatic) {
    if (candidate.signal < threshold) {
      continue;
    }

    if (find_activation(
            result,
            candidate.family) !=
        nullptr) {
      auto* existing =
          find_activation(
              result,
              candidate.family);

      existing->signal_seconds_per_km =
          std::max(
              existing
                  ->signal_seconds_per_km,
              candidate.signal);

      for (const auto& reason :
           candidate.reasons) {
        append_reason(
            *existing,
            reason);
      }

      continue;
    }

    if (policy.max_families > 0 &&
        result.size() >=
            policy.max_families) {
      continue;
    }

    CandidateFamilyActivation activation;

    activation.family =
        candidate.family;

    activation.signal_seconds_per_km =
        candidate.signal;

    activation.reason_keys =
        candidate.reasons;

    result.push_back(
        std::move(activation));
  }

  std::sort(
      result.begin(),
      result.end(),
      [](const CandidateFamilyActivation& left,
         const CandidateFamilyActivation& right) {
        return static_cast<int>(
                   left.family) <
            static_cast<int>(
                right.family);
      });

  return result;
}

}  // namespace routing::core::candidates
