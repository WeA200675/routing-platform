#pragma once

#include <cstdint>
#include <optional>
#include <string>
#include <vector>

#include "routing/core/evaluation/route_evaluation.hpp"

namespace routing::core::evaluation {

enum class ComparisonOutcome : std::uint8_t {
  Unavailable = 0,
  ReferencePreferred,
  CandidatePreferred,
  Equivalent,
  BothDisallowed,
};

struct ComparisonInsight {
  // Stabiler semantischer Key fuer UI, Logs und Tests.
  std::string key;

  // Immer positive Groesse des Unterschieds.
  double magnitude = 0.0;

  // "m", "s" oder "s_equivalent".
  std::string unit;
};

struct CandidateComparison {
  std::string reference_route_id;
  std::string candidate_route_id;

  double distance_delta_m = 0.0;
  double duration_delta_s = 0.0;

  bool segment_metrics_comparable = false;
  bool score_comparable = false;

  std::optional<double>
      score_delta_seconds_equivalent;

  double major_road_delta_m = 0.0;
  double minor_road_delta_m = 0.0;

  double residential_delta_m = 0.0;

  double federal_road_delta_m = 0.0;
  double municipal_road_delta_m = 0.0;

  double speed_30_or_lower_delta_m = 0.0;
  double strongly_curvy_delta_m = 0.0;
  double serpentine_delta_m = 0.0;
  double steep_gradient_delta_m = 0.0;
  double urban_delta_m = 0.0;

  double uncertainty_delta_s = 0.0;

  ComparisonOutcome outcome =
      ComparisonOutcome::Unavailable;

  std::vector<ComparisonInsight> insights;
};

[[nodiscard]]
CandidateComparison compare_candidates(
    const RouteEvaluation& reference,
    const RouteEvaluation& candidate);

}  // namespace routing::core::evaluation
