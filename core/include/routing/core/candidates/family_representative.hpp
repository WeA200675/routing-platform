#pragma once

#include <cstddef>
#include <cstdint>
#include <optional>
#include <string>
#include <vector>

#include "routing/core/candidates/candidate_family_plan.hpp"
#include "routing/core/evaluation/route_evaluation.hpp"

namespace routing::core::candidates {

enum class FamilyRepresentativeStatus : std::uint8_t {
  Selected = 0,
  DeferredFamily,
  NoCandidates,
  NoAllowedCandidate,
  InsufficientData,
};

struct FamilyMetricEvidence {
  std::string route_id;

  // True only when this route is actually eligible for
  // the family metric.
  bool eligible = false;

  // 0..1.
  double known_coverage = 0.0;

  // Metric-specific:
  // CoreCost      -> seconds-equivalent, lower is better
  // MajorRoadShare-> ratio 0..1, higher is better
  // UrbanShare    -> ratio 0..1, lower is better
  // CurvyShare    -> ratio 0..1, lower is better
  // SteepGradient -> ratio 0..1, lower is better
  double metric_value = 0.0;
};

struct FamilyRepresentativeDecision {
  CandidateFamily family =
      CandidateFamily::ProfileOptimal;

  FamilyRepresentativeStatus status =
      FamilyRepresentativeStatus::NoCandidates;

  std::optional<std::size_t>
      selected_index;

  std::string selected_route_id;

  // Stable explanation code.
  std::string reason_key;

  std::vector<FamilyMetricEvidence>
      evidence;
};

[[nodiscard]]
FamilyRepresentativeDecision
select_family_representative(
    const CandidateFamilyPlan& plan,
    const std::vector<
        evaluation::RouteEvaluation>&
        evaluations);

}  // namespace routing::core::candidates
