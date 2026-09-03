#include <cassert>
#include <cmath>
#include <iostream>

#include "routing/core/testing/route_metric.hpp"

namespace {

bool nearly_equal(
    const double left,
    const double right,
    const double tolerance = 1e-9) {
  return std::abs(left - right) <=
      tolerance;
}

}  // namespace

int main() {
  using namespace routing::core::testing;

  routing::core::evaluation::
      RouteEvaluation evaluation;

  evaluation.segment_data_available =
      true;

  evaluation.analysis
      .analyzed_distance_m =
          1000.0;

  evaluation.analysis
      .major_road_distance_m =
          600.0;

  evaluation.analysis
      .minor_road_distance_m =
          200.0;

  evaluation.analysis
      .unknown_road_class_distance_m =
          200.0;

  evaluation.analysis
      .speed_30_or_lower_distance_m =
          180.0;

  evaluation.analysis
      .unknown_speed_limit_distance_m =
          100.0;

  evaluation.analysis
      .strongly_curvy_distance_m =
          150.0;

  evaluation.analysis
      .unknown_curvature_distance_m =
          250.0;

  evaluation.analysis
      .serpentine_distance_m =
          75.0;

  evaluation.analysis
      .unknown_serpentine_distance_m =
          250.0;

  evaluation.analysis
      .urban_distance_m =
          90.0;

  evaluation.analysis
      .unknown_urban_distance_m =
          100.0;

  evaluation
      .steep_gradient_distance_m =
          150.0;

  evaluation
      .unknown_gradient_distance_m =
          250.0;

  evaluation.analysis
      .unknown_confidence_distance_m =
          125.0;

  const auto major =
      measure_route_metric(
          evaluation,
          RouteMetric::MajorRoadShare);

  assert(major.available);
  assert(
      nearly_equal(
          major.known_coverage,
          0.80));

  assert(
      nearly_equal(
          major.value,
          0.75));

  const auto speed_30 =
      measure_route_metric(
          evaluation,
          RouteMetric::Speed30OrLowerShare);

  assert(speed_30.available);

  assert(
      nearly_equal(
          speed_30.known_coverage,
          0.90));

  assert(
      nearly_equal(
          speed_30.value,
          0.20));

  const auto steep =
      measure_route_metric(
          evaluation,
          RouteMetric::SteepGradientShare);

  assert(steep.available);

  assert(
      nearly_equal(
          steep.known_coverage,
          0.75));

  assert(
      nearly_equal(
          steep.value,
          0.20));

  const auto road_coverage =
      measure_route_metric(
          evaluation,
          RouteMetric::KnownRoadClassCoverage);

  assert(road_coverage.available);
  assert(
      nearly_equal(
          road_coverage.value,
          0.80));

  assert(
      nearly_equal(
          road_coverage.known_coverage,
          1.0));

  const auto unknown_confidence =
      measure_route_metric(
          evaluation,
          RouteMetric::UnknownConfidenceShare);

  assert(unknown_confidence.available);

  assert(
      nearly_equal(
          unknown_confidence.value,
          0.125));

  // Critical semantic guard:
  // zero measured urban distance with 100% unknown data must NOT
  // become "0% urban".
  auto unknown_urban =
      evaluation;

  unknown_urban.analysis
      .urban_distance_m = 0.0;

  unknown_urban.analysis
      .unknown_urban_distance_m =
          1000.0;

  const auto unknown_urban_metric =
      measure_route_metric(
          unknown_urban,
          RouteMetric::UrbanShare);

  assert(
      !unknown_urban_metric.available);

  assert(
      nearly_equal(
          unknown_urban_metric
              .known_coverage,
          0.0));

  std::cout
      << "Route metric tests passed\n";

  return 0;
}
