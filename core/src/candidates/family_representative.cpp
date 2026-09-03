#include "routing/core/candidates/family_representative.hpp"

#include <algorithm>
#include <cmath>
#include <limits>

namespace routing::core::candidates {

namespace {

constexpr double kMetricEpsilon = 1e-12;

struct MetricMeasurement {
  bool available = false;
  double known_coverage = 0.0;
  double value = 0.0;
};

double clamp01(
    const double value) {
  return std::clamp(
      value,
      0.0,
      1.0);
}

MetricMeasurement measure(
    const CandidateFamilyPlan& plan,
    const evaluation::RouteEvaluation& evaluation) {
  MetricMeasurement result;

  switch (plan.representative_metric) {
    case FamilyRepresentativeMetric::FirstRoute:
      result.available = true;
      result.known_coverage = 1.0;
      result.value = 0.0;
      return result;

    case FamilyRepresentativeMetric::CoreCost:
      if (!evaluation.score_available ||
          !std::isfinite(
              evaluation.total_seconds_equivalent)) {
        return result;
      }

      result.available = true;
      result.known_coverage = 1.0;
      result.value =
          evaluation.total_seconds_equivalent;

      return result;

    case FamilyRepresentativeMetric::MajorRoadShare: {
      if (!evaluation.segment_data_available ||
          evaluation.analysis.analyzed_distance_m <= 0.0) {
        return result;
      }

      const double analyzed =
          evaluation.analysis.analyzed_distance_m;

      const double unknown =
          std::clamp(
              evaluation.analysis
                  .unknown_road_class_distance_m,
              0.0,
              analyzed);

      const double known =
          analyzed - unknown;

      result.known_coverage =
          clamp01(
              known / analyzed);

      if (known <= 0.0) {
        return result;
      }

      result.available = true;

      result.value =
          clamp01(
              evaluation.analysis
                  .major_road_distance_m /
              known);

      return result;
    }

    case FamilyRepresentativeMetric::UrbanShare: {
      if (!evaluation.segment_data_available ||
          evaluation.analysis.analyzed_distance_m <= 0.0) {
        return result;
      }

      const double analyzed =
          evaluation.analysis.analyzed_distance_m;

      const double unknown =
          std::clamp(
              evaluation.analysis
                  .unknown_urban_distance_m,
              0.0,
              analyzed);

      const double known =
          analyzed - unknown;

      result.known_coverage =
          clamp01(
              known / analyzed);

      if (known <= 0.0) {
        return result;
      }

      result.available = true;

      result.value =
          clamp01(
              evaluation.analysis
                  .urban_distance_m /
              known);

      return result;
    }

    case FamilyRepresentativeMetric::CurvyShare: {
      if (!evaluation.segment_data_available ||
          evaluation.analysis.analyzed_distance_m <= 0.0) {
        return result;
      }

      const double analyzed =
          evaluation.analysis.analyzed_distance_m;

      const double unknown =
          std::clamp(
              evaluation.analysis
                  .unknown_curvature_distance_m,
              0.0,
              analyzed);

      const double known =
          analyzed - unknown;

      result.known_coverage =
          clamp01(
              known / analyzed);

      if (known <= 0.0) {
        return result;
      }

      result.available = true;

      result.value =
          clamp01(
              evaluation.analysis
                  .strongly_curvy_distance_m /
              known);

      return result;
    }

    case FamilyRepresentativeMetric::
        SteepGradientShare: {
      if (!evaluation.segment_data_available ||
          evaluation.analysis.analyzed_distance_m <= 0.0) {
        return result;
      }

      const double analyzed =
          evaluation.analysis.analyzed_distance_m;

      const double unknown =
          std::clamp(
              evaluation.unknown_gradient_distance_m,
              0.0,
              analyzed);

      const double known =
          analyzed - unknown;

      result.known_coverage =
          clamp01(
              known / analyzed);

      if (known <= 0.0) {
        return result;
      }

      result.available = true;

      result.value =
          clamp01(
              evaluation
                  .steep_gradient_distance_m /
              known);

      return result;
    }
  }

  return result;
}

bool metric_prefers_higher(
    const FamilyRepresentativeMetric metric) {
  return metric ==
      FamilyRepresentativeMetric::
          MajorRoadShare;
}

bool better_tie_break(
    const evaluation::RouteEvaluation& candidate,
    const evaluation::RouteEvaluation& current) {
  if (candidate.total_seconds_equivalent <
      current.total_seconds_equivalent -
          kMetricEpsilon) {
    return true;
  }

  if (candidate.total_seconds_equivalent >
      current.total_seconds_equivalent +
          kMetricEpsilon) {
    return false;
  }

  if (candidate.reported_duration_s <
      current.reported_duration_s -
          kMetricEpsilon) {
    return true;
  }

  if (candidate.reported_duration_s >
      current.reported_duration_s +
          kMetricEpsilon) {
    return false;
  }

  if (candidate.reported_distance_m <
      current.reported_distance_m -
          kMetricEpsilon) {
    return true;
  }

  if (candidate.reported_distance_m >
      current.reported_distance_m +
          kMetricEpsilon) {
    return false;
  }

  return candidate.route_id <
      current.route_id;
}

std::string reason_for(
    const FamilyRepresentativeMetric metric) {
  switch (metric) {
    case FamilyRepresentativeMetric::FirstRoute:
      return "family.first_route";

    case FamilyRepresentativeMetric::CoreCost:
      return "family.lowest_core_cost";

    case FamilyRepresentativeMetric::MajorRoadShare:
      return "family.highest_major_road_share";

    case FamilyRepresentativeMetric::UrbanShare:
      return "family.lowest_urban_share";

    case FamilyRepresentativeMetric::CurvyShare:
      return "family.lowest_curvy_share";

    case FamilyRepresentativeMetric::
        SteepGradientShare:
      return "family.lowest_steep_gradient_share";
  }

  return "family.unknown_metric";
}

}  // namespace

FamilyRepresentativeDecision
select_family_representative(
    const CandidateFamilyPlan& plan,
    const std::vector<
        evaluation::RouteEvaluation>&
        evaluations) {
  FamilyRepresentativeDecision result;

  result.family =
      plan.family;

  if (!plan.implemented ||
      plan.generation_mode ==
          CandidateGenerationMode::Deferred) {
    result.status =
        FamilyRepresentativeStatus::
            DeferredFamily;

    result.reason_key =
        "family.deferred";

    return result;
  }

  if (evaluations.empty()) {
    result.status =
        FamilyRepresentativeStatus::
            NoCandidates;

    result.reason_key =
        "family.no_candidates";

    return result;
  }

  bool any_allowed_scored = false;

  std::optional<std::size_t>
      best_index;

  double best_metric = 0.0;

  for (std::size_t index = 0;
       index < evaluations.size();
       ++index) {
    const auto& evaluation =
        evaluations[index];

    FamilyMetricEvidence evidence;
    evidence.route_id =
        evaluation.route_id;

    if (!evaluation.allowed ||
        !evaluation.score_available ||
        !std::isfinite(
            evaluation.total_seconds_equivalent)) {
      result.evidence.push_back(
          evidence);

      continue;
    }

    any_allowed_scored = true;

    const auto measurement =
        measure(
            plan,
            evaluation);

    evidence.known_coverage =
        measurement.known_coverage;

    evidence.metric_value =
        measurement.value;

    if (!measurement.available ||
        measurement.known_coverage +
                kMetricEpsilon <
            plan.minimum_known_coverage) {
      result.evidence.push_back(
          evidence);

      continue;
    }

    evidence.eligible = true;

    result.evidence.push_back(
        evidence);

    if (!best_index.has_value()) {
      best_index = index;
      best_metric =
          measurement.value;

      continue;
    }

    if (plan.representative_metric ==
        FamilyRepresentativeMetric::
            FirstRoute) {
      continue;
    }

    bool candidate_better = false;

    if (metric_prefers_higher(
            plan.representative_metric)) {
      candidate_better =
          measurement.value >
          best_metric +
              kMetricEpsilon;
    } else {
      candidate_better =
          measurement.value <
          best_metric -
              kMetricEpsilon;
    }

    const bool metric_equal =
        std::abs(
            measurement.value -
            best_metric) <=
        kMetricEpsilon;

    if (candidate_better ||
        (metric_equal &&
         better_tie_break(
             evaluation,
             evaluations[
                 *best_index]))) {
      best_index = index;
      best_metric =
          measurement.value;
    }
  }

  if (!best_index.has_value()) {
    if (any_allowed_scored) {
      result.status =
          FamilyRepresentativeStatus::
              InsufficientData;

      result.reason_key =
          "family.insufficient_known_coverage";
    } else {
      result.status =
          FamilyRepresentativeStatus::
              NoAllowedCandidate;

      result.reason_key =
          "family.no_allowed_candidate";
    }

    return result;
  }

  result.status =
      FamilyRepresentativeStatus::Selected;

  result.selected_index =
      best_index;

  result.selected_route_id =
      evaluations[
          *best_index].route_id;

  result.reason_key =
      reason_for(
          plan.representative_metric);

  return result;
}

}  // namespace routing::core::candidates
