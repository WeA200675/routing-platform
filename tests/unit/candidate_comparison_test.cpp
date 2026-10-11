#include <algorithm>
#include <cassert>
#include <cmath>
#include <iostream>
#include <string>

#include "routing/core/evaluation/candidate_comparison.hpp"

namespace {

bool nearly_equal(
    double left,
    double right,
    double tolerance = 1e-9) {
  return std::abs(left - right) <= tolerance;
}

bool has_insight(
    const routing::core::evaluation::
        CandidateComparison& comparison,
    const std::string& key) {
  return std::any_of(
      comparison.insights.begin(),
      comparison.insights.end(),
      [&key](const auto& insight) {
        return insight.key == key;
      });
}

}  // namespace

int main() {
  using namespace routing::core::evaluation;

  RouteEvaluation reference;

  reference.route_id = "fast-shortcut";
  reference.reported_distance_m = 3000.0;
  reference.reported_duration_s = 240.0;
  reference.segment_data_available = true;
  reference.score_available = true;
  reference.allowed = true;

  reference.total_seconds_equivalent = 250.0;
  reference.uncertainty_seconds = 10.0;

  reference.analysis.major_road_distance_m =
      1500.0;
  reference.analysis.minor_road_distance_m =
      1500.0;
  reference.analysis.speed_30_or_lower_distance_m =
      900.0;
  reference.analysis.strongly_curvy_distance_m =
      600.0;
  reference.analysis.serpentine_distance_m =
      400.0;
  reference.analysis.urban_distance_m =
      1000.0;

  reference.functional_roads.residential_m =
      1200.0;

  reference.road_networks.federal_m =
      600.0;
  reference.road_networks.municipal_m =
      1400.0;

  reference.steep_gradient_distance_m =
      300.0;

  RouteEvaluation candidate;

  candidate.route_id = "major-road-option";
  candidate.reported_distance_m = 3400.0;
  candidate.reported_duration_s = 280.0;
  candidate.segment_data_available = true;
  candidate.score_available = true;
  candidate.allowed = true;

  candidate.total_seconds_equivalent = 245.0;
  candidate.uncertainty_seconds = 4.0;

  candidate.analysis.major_road_distance_m =
      3000.0;
  candidate.analysis.minor_road_distance_m =
      400.0;
  candidate.analysis.speed_30_or_lower_distance_m =
      0.0;
  candidate.analysis.strongly_curvy_distance_m =
      200.0;
  candidate.analysis.serpentine_distance_m =
      0.0;
  candidate.analysis.urban_distance_m =
      300.0;

  candidate.functional_roads.residential_m =
      200.0;

  candidate.road_networks.federal_m =
      2200.0;
  candidate.road_networks.municipal_m =
      300.0;

  candidate.steep_gradient_distance_m =
      100.0;

  const auto comparison =
      compare_candidates(
          reference,
          candidate);

  assert(
      comparison.outcome ==
      ComparisonOutcome::CandidatePreferred);

  assert(comparison.segment_metrics_comparable);
  assert(comparison.score_comparable);

  assert(nearly_equal(
      comparison.duration_delta_s,
      40.0));

  assert(nearly_equal(
      comparison.distance_delta_m,
      400.0));

  assert(nearly_equal(
      comparison.major_road_delta_m,
      1500.0));

  assert(nearly_equal(
      comparison.minor_road_delta_m,
      -1100.0));

  assert(nearly_equal(
      comparison.residential_delta_m,
      -1000.0));

  assert(nearly_equal(
      comparison.federal_road_delta_m,
      1600.0));

  assert(nearly_equal(
      comparison.municipal_road_delta_m,
      -1100.0));

  assert(nearly_equal(
      comparison.speed_30_or_lower_delta_m,
      -900.0));

  assert(nearly_equal(
      comparison.strongly_curvy_delta_m,
      -400.0));

  assert(nearly_equal(
      comparison.serpentine_delta_m,
      -400.0));

  assert(nearly_equal(
      comparison.steep_gradient_delta_m,
      -200.0));

  assert(nearly_equal(
      comparison.urban_delta_m,
      -700.0));

  assert(
      comparison.score_delta_seconds_equivalent.
          has_value());

  assert(nearly_equal(
      *comparison.score_delta_seconds_equivalent,
      -5.0));

  assert(has_insight(
      comparison,
      "longer_duration"));

  assert(has_insight(
      comparison,
      "longer_distance"));

  assert(has_insight(
      comparison,
      "more_major_road"));

  assert(has_insight(
      comparison,
      "less_minor_road"));

  assert(has_insight(
      comparison,
      "less_residential_road"));

  assert(has_insight(
      comparison,
      "more_federal_road"));

  assert(has_insight(
      comparison,
      "less_municipal_road"));

  assert(has_insight(
      comparison,
      "less_speed_30_or_lower"));

  assert(has_insight(
      comparison,
      "less_strongly_curvy"));

  assert(has_insight(
      comparison,
      "less_serpentine"));

  assert(has_insight(
      comparison,
      "less_steep_gradient"));

  assert(has_insight(
      comparison,
      "less_urban"));

  assert(has_insight(
      comparison,
      "lower_uncertainty"));

  assert(has_insight(
      comparison,
      "lower_total_cost"));

  std::cout
      << "Candidate comparison tests passed\n";

  return 0;
}
