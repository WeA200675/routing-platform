#pragma once

#include <cstddef>
#include <cstdint>
#include <string_view>
#include <vector>

#include "routing/core/routing_engine.hpp"

namespace routing::core::candidates {

enum class CandidateGenerationMode : std::uint8_t {
  Direct = 0,
  DiversePoolPostEvaluate,
  Deferred,
};

enum class CandidateGenerationBias : std::uint8_t {
  DistanceFirst = 0,
  PreferHighHierarchy,
  AvoidTracks,
  AvoidLivingStreets,
  AvoidServiceRoads,
  ReduceManeuvers,
};

enum class FamilyRepresentativeMetric : std::uint8_t {
  FirstRoute = 0,

  // Existing CostEngine through RouteEvaluation.
  CoreCost,

  // Descriptive Street-Model metrics.
  MajorRoadShare,
  UrbanShare,
  CurvyShare,
  SteepGradientShare,
};

struct CandidateFamilyPlan {
  CandidateFamily family =
      CandidateFamily::ProfileOptimal;

  CandidateGenerationMode generation_mode =
      CandidateGenerationMode::Direct;

  // Number of additional routes requested from a backend.
  std::size_t alternatives_requested = 0;

  std::vector<CandidateGenerationBias>
      generation_biases;

  FamilyRepresentativeMetric
      representative_metric =
          FamilyRepresentativeMetric::FirstRoute;

  // For post-evaluation metrics:
  // unknown data is never treated as zero exposure.
  double minimum_known_coverage = 0.80;

  // False means the family exists in the public model but
  // we deliberately refuse to fake an implementation.
  bool implemented = true;

  // True only if backend generation itself fully expresses
  // the family semantics.
  //
  // MajorRoads is intentionally false: e.g. a backend
  // highway preference does not equal our Street Model's
  // complete major-road / road-network semantics.
  bool backend_bias_semantically_complete = false;

  // Stable explanation key, not localized UI text.
  std::string_view rationale_key;
};

[[nodiscard]]
std::string_view candidate_family_key(
    CandidateFamily family);

[[nodiscard]]
CandidateFamilyPlan candidate_family_plan(
    CandidateFamily family);

[[nodiscard]]
std::vector<CandidateFamilyPlan>
all_candidate_family_plans();

[[nodiscard]]
bool has_generation_bias(
    const CandidateFamilyPlan& plan,
    CandidateGenerationBias bias);

[[nodiscard]]
RouteRequest make_candidate_request(
    const RouteRequest& base_request,
    const CandidateFamilyPlan& plan);

}  // namespace routing::core::candidates
