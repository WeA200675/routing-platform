#include "routing/core/evaluation/candidate_comparison.hpp"

#include <cmath>
#include <utility>

namespace routing::core::evaluation {

namespace {

constexpr double kDistanceEpsilonM = 1.0;
constexpr double kTimeEpsilonS = 0.001;

void add_insight(
    CandidateComparison& comparison,
    std::string key,
    double magnitude,
    std::string unit) {
  comparison.insights.push_back({
      std::move(key),
      magnitude,
      std::move(unit),
  });
}

}  // namespace

CandidateComparison compare_candidates(
    const RouteEvaluation& reference,
    const RouteEvaluation& candidate) {
  CandidateComparison result;

  result.reference_route_id =
      reference.route_id;

  result.candidate_route_id =
      candidate.route_id;

  result.distance_delta_m =
      candidate.reported_distance_m -
      reference.reported_distance_m;

  result.duration_delta_s =
      candidate.reported_duration_s -
      reference.reported_duration_s;

  if (result.duration_delta_s >
      kTimeEpsilonS) {
    add_insight(
        result,
        "longer_duration",
        result.duration_delta_s,
        "s");
  } else if (result.duration_delta_s <
             -kTimeEpsilonS) {
    add_insight(
        result,
        "shorter_duration",
        -result.duration_delta_s,
        "s");
  }

  if (result.distance_delta_m >
      kDistanceEpsilonM) {
    add_insight(
        result,
        "longer_distance",
        result.distance_delta_m,
        "m");
  } else if (result.distance_delta_m <
             -kDistanceEpsilonM) {
    add_insight(
        result,
        "shorter_distance",
        -result.distance_delta_m,
        "m");
  }

  result.segment_metrics_comparable =
      reference.segment_data_available &&
      candidate.segment_data_available;

  if (result.segment_metrics_comparable) {
    result.major_road_delta_m =
        candidate.analysis.major_road_distance_m -
        reference.analysis.major_road_distance_m;

    result.minor_road_delta_m =
        candidate.analysis.minor_road_distance_m -
        reference.analysis.minor_road_distance_m;

    result.residential_delta_m =
        candidate.functional_roads.residential_m -
        reference.functional_roads.residential_m;

    result.federal_road_delta_m =
        candidate.road_networks.federal_m -
        reference.road_networks.federal_m;

    result.municipal_road_delta_m =
        candidate.road_networks.municipal_m -
        reference.road_networks.municipal_m;

    result.speed_30_or_lower_delta_m =
        candidate.analysis.speed_30_or_lower_distance_m -
        reference.analysis.speed_30_or_lower_distance_m;

    result.strongly_curvy_delta_m =
        candidate.analysis.strongly_curvy_distance_m -
        reference.analysis.strongly_curvy_distance_m;

    result.serpentine_delta_m =
        candidate.analysis.serpentine_distance_m -
        reference.analysis.serpentine_distance_m;

    result.steep_gradient_delta_m =
        candidate.steep_gradient_distance_m -
        reference.steep_gradient_distance_m;

    result.urban_delta_m =
        candidate.analysis.urban_distance_m -
        reference.analysis.urban_distance_m;

    result.uncertainty_delta_s =
        candidate.uncertainty_seconds -
        reference.uncertainty_seconds;

    if (result.major_road_delta_m >
        kDistanceEpsilonM) {
      add_insight(
          result,
          "more_major_road",
          result.major_road_delta_m,
          "m");
    }

    if (result.minor_road_delta_m <
        -kDistanceEpsilonM) {
      add_insight(
          result,
          "less_minor_road",
          -result.minor_road_delta_m,
          "m");
    }

    if (result.residential_delta_m <
        -kDistanceEpsilonM) {
      add_insight(
          result,
          "less_residential_road",
          -result.residential_delta_m,
          "m");
    }

    if (result.federal_road_delta_m >
        kDistanceEpsilonM) {
      add_insight(
          result,
          "more_federal_road",
          result.federal_road_delta_m,
          "m");
    }

    if (result.municipal_road_delta_m <
        -kDistanceEpsilonM) {
      add_insight(
          result,
          "less_municipal_road",
          -result.municipal_road_delta_m,
          "m");
    }

    if (result.speed_30_or_lower_delta_m <
        -kDistanceEpsilonM) {
      add_insight(
          result,
          "less_speed_30_or_lower",
          -result.speed_30_or_lower_delta_m,
          "m");
    }

    if (result.strongly_curvy_delta_m <
        -kDistanceEpsilonM) {
      add_insight(
          result,
          "less_strongly_curvy",
          -result.strongly_curvy_delta_m,
          "m");
    }

    if (result.serpentine_delta_m <
        -kDistanceEpsilonM) {
      add_insight(
          result,
          "less_serpentine",
          -result.serpentine_delta_m,
          "m");
    }

    if (result.steep_gradient_delta_m <
        -kDistanceEpsilonM) {
      add_insight(
          result,
          "less_steep_gradient",
          -result.steep_gradient_delta_m,
          "m");
    }

    if (result.urban_delta_m <
        -kDistanceEpsilonM) {
      add_insight(
          result,
          "less_urban",
          -result.urban_delta_m,
          "m");
    }

    if (result.uncertainty_delta_s <
        -kTimeEpsilonS) {
      add_insight(
          result,
          "lower_uncertainty",
          -result.uncertainty_delta_s,
          "s_equivalent");
    }
  }

  result.score_comparable =
      reference.score_available &&
      candidate.score_available;

  if (!result.score_comparable) {
    result.outcome =
        ComparisonOutcome::Unavailable;
    return result;
  }

  if (!reference.allowed &&
      !candidate.allowed) {
    result.outcome =
        ComparisonOutcome::BothDisallowed;
    return result;
  }

  if (!reference.allowed &&
      candidate.allowed) {
    result.outcome =
        ComparisonOutcome::CandidatePreferred;
    return result;
  }

  if (reference.allowed &&
      !candidate.allowed) {
    result.outcome =
        ComparisonOutcome::ReferencePreferred;
    return result;
  }

  const double score_delta =
      candidate.total_seconds_equivalent -
      reference.total_seconds_equivalent;

  result.score_delta_seconds_equivalent =
      score_delta;

  if (score_delta <
      -kTimeEpsilonS) {
    result.outcome =
        ComparisonOutcome::CandidatePreferred;

    add_insight(
        result,
        "lower_total_cost",
        -score_delta,
        "s_equivalent");
  } else if (score_delta >
             kTimeEpsilonS) {
    result.outcome =
        ComparisonOutcome::ReferencePreferred;

    add_insight(
        result,
        "higher_total_cost",
        score_delta,
        "s_equivalent");
  } else {
    result.outcome =
        ComparisonOutcome::Equivalent;
  }

  return result;
}

}  // namespace routing::core::evaluation
