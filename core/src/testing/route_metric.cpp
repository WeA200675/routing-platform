#include "routing/core/testing/route_metric.hpp"

#include <algorithm>
#include <stdexcept>

namespace routing::core::testing {

namespace {

double clamp01(
    const double value) {
  return std::clamp(
      value,
      0.0,
      1.0);
}

RouteMetricValue unavailable() {
  return {};
}

RouteMetricValue exposure_metric(
    const double analyzed_distance_m,
    const double unknown_distance_m,
    const double exposure_distance_m) {
  if (analyzed_distance_m <= 0.0) {
    return unavailable();
  }

  const double unknown =
      std::clamp(
          unknown_distance_m,
          0.0,
          analyzed_distance_m);

  const double known =
      analyzed_distance_m -
      unknown;

  RouteMetricValue result;

  result.known_coverage =
      clamp01(
          known /
          analyzed_distance_m);

  if (known <= 0.0) {
    return result;
  }

  result.available = true;

  result.value =
      clamp01(
          std::clamp(
              exposure_distance_m,
              0.0,
              known) /
          known);

  return result;
}

RouteMetricValue coverage_metric(
    const double analyzed_distance_m,
    const double unknown_distance_m) {
  if (analyzed_distance_m <= 0.0) {
    return unavailable();
  }

  RouteMetricValue result;

  result.available = true;
  result.known_coverage = 1.0;

  const double unknown =
      std::clamp(
          unknown_distance_m,
          0.0,
          analyzed_distance_m);

  result.value =
      clamp01(
          (analyzed_distance_m - unknown) /
          analyzed_distance_m);

  return result;
}

}  // namespace

std::string_view route_metric_key(
    const RouteMetric metric) {
  switch (metric) {
    case RouteMetric::MajorRoadShare:
      return "major_road_share";

    case RouteMetric::MinorRoadShare:
      return "minor_road_share";

    case RouteMetric::Speed30OrLowerShare:
      return "speed_30_or_lower_share";

    case RouteMetric::StronglyCurvyShare:
      return "strongly_curvy_share";

    case RouteMetric::SerpentineShare:
      return "serpentine_share";

    case RouteMetric::UrbanShare:
      return "urban_share";

    case RouteMetric::SteepGradientShare:
      return "steep_gradient_share";

    case RouteMetric::KnownRoadClassCoverage:
      return "known_road_class_coverage";

    case RouteMetric::KnownSpeedLimitCoverage:
      return "known_speed_limit_coverage";

    case RouteMetric::KnownCurvatureCoverage:
      return "known_curvature_coverage";

    case RouteMetric::KnownSerpentineCoverage:
      return "known_serpentine_coverage";

    case RouteMetric::KnownUrbanCoverage:
      return "known_urban_coverage";

    case RouteMetric::KnownGradientCoverage:
      return "known_gradient_coverage";

    case RouteMetric::UnknownConfidenceShare:
      return "unknown_confidence_share";
  }

  throw std::invalid_argument(
      "Unknown route metric.");
}

RouteMetricValue measure_route_metric(
    const evaluation::RouteEvaluation& evaluation,
    const RouteMetric metric) {
  if (!evaluation.segment_data_available) {
    return unavailable();
  }

  const double analyzed =
      evaluation.analysis
          .analyzed_distance_m;

  switch (metric) {
    case RouteMetric::MajorRoadShare:
      return exposure_metric(
          analyzed,
          evaluation.analysis
              .unknown_road_class_distance_m,
          evaluation.analysis
              .major_road_distance_m);

    case RouteMetric::MinorRoadShare:
      return exposure_metric(
          analyzed,
          evaluation.analysis
              .unknown_road_class_distance_m,
          evaluation.analysis
              .minor_road_distance_m);

    case RouteMetric::Speed30OrLowerShare:
      return exposure_metric(
          analyzed,
          evaluation.analysis
              .unknown_speed_limit_distance_m,
          evaluation.analysis
              .speed_30_or_lower_distance_m);

    case RouteMetric::StronglyCurvyShare:
      return exposure_metric(
          analyzed,
          evaluation.analysis
              .unknown_curvature_distance_m,
          evaluation.analysis
              .strongly_curvy_distance_m);

    case RouteMetric::SerpentineShare:
      return exposure_metric(
          analyzed,
          evaluation.analysis
              .unknown_serpentine_distance_m,
          evaluation.analysis
              .serpentine_distance_m);

    case RouteMetric::UrbanShare:
      return exposure_metric(
          analyzed,
          evaluation.analysis
              .unknown_urban_distance_m,
          evaluation.analysis
              .urban_distance_m);

    case RouteMetric::SteepGradientShare:
      return exposure_metric(
          analyzed,
          evaluation
              .unknown_gradient_distance_m,
          evaluation
              .steep_gradient_distance_m);

    case RouteMetric::KnownRoadClassCoverage:
      return coverage_metric(
          analyzed,
          evaluation.analysis
              .unknown_road_class_distance_m);

    case RouteMetric::KnownSpeedLimitCoverage:
      return coverage_metric(
          analyzed,
          evaluation.analysis
              .unknown_speed_limit_distance_m);

    case RouteMetric::KnownCurvatureCoverage:
      return coverage_metric(
          analyzed,
          evaluation.analysis
              .unknown_curvature_distance_m);

    case RouteMetric::KnownSerpentineCoverage:
      return coverage_metric(
          analyzed,
          evaluation.analysis
              .unknown_serpentine_distance_m);

    case RouteMetric::KnownUrbanCoverage:
      return coverage_metric(
          analyzed,
          evaluation.analysis
              .unknown_urban_distance_m);

    case RouteMetric::KnownGradientCoverage:
      return coverage_metric(
          analyzed,
          evaluation
              .unknown_gradient_distance_m);

    case RouteMetric::UnknownConfidenceShare: {
      if (analyzed <= 0.0) {
        return unavailable();
      }

      RouteMetricValue result;

      result.available = true;
      result.known_coverage = 1.0;

      result.value =
          clamp01(
              evaluation.analysis
                  .unknown_confidence_distance_m /
              analyzed);

      return result;
    }
  }

  return unavailable();
}

}  // namespace routing::core::testing
