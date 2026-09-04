#pragma once

#include <algorithm>
#include <cmath>
#include <string>
#include <utility>
#include <vector>

#include "routing/core/candidates/candidate_orchestrator.hpp"
#include "routing/core/diagnostics/routing_diagnostic.hpp"
#include "routing/core/evaluation/route_evaluation.hpp"

namespace routing::core::diagnostics {

struct DiagnosticPolicy {
  // Same conservative default currently used by scenario metric
  // expectations: values below this are explicitly low coverage.
  double minimum_known_coverage = 0.80;

  // An "all known values show no positive urban signal" observation is
  // only emitted when coverage is very high.
  //
  // It is INFO only. It does not mean that the source data is wrong.
  double all_known_zero_observation_coverage = 0.95;

  // Existing real-Valhalla integration tests already accept this range
  // because trace edge lengths and route summary lengths are not exactly
  // the same representation.
  double minimum_segment_distance_ratio = 0.50;
  double maximum_segment_distance_ratio = 1.75;
};

namespace detail {

inline RoutingDiagnostic route_diagnostic(
    const RoutePath& route,
    std::string code,
    const DiagnosticSeverity severity,
    const DiagnosticCategory category,
    std::string explanation_key,
    std::string detail) {
  RoutingDiagnostic diagnostic;

  diagnostic.code =
      std::move(code);

  diagnostic.severity =
      severity;

  diagnostic.category =
      category;

  diagnostic.scope =
      DiagnosticScope::Route;

  diagnostic.family =
      route.family;

  diagnostic.route_id =
      route.route_id;

  diagnostic.explanation_key =
      std::move(explanation_key);

  diagnostic.detail =
      std::move(detail);

  return diagnostic;
}

inline RoutingDiagnostic family_diagnostic(
    const CandidateFamily family,
    std::string code,
    const DiagnosticSeverity severity,
    std::string explanation_key,
    std::string detail) {
  RoutingDiagnostic diagnostic;

  diagnostic.code =
      std::move(code);

  diagnostic.severity =
      severity;

  diagnostic.category =
      DiagnosticCategory::CandidateSet;

  diagnostic.scope =
      DiagnosticScope::Family;

  diagnostic.family =
      family;

  diagnostic.explanation_key =
      std::move(explanation_key);

  diagnostic.detail =
      std::move(detail);

  return diagnostic;
}

inline RoutingDiagnostic orchestration_diagnostic(
    std::string code,
    const DiagnosticSeverity severity,
    std::string explanation_key,
    std::string detail) {
  RoutingDiagnostic diagnostic;

  diagnostic.code =
      std::move(code);

  diagnostic.severity =
      severity;

  diagnostic.category =
      DiagnosticCategory::CandidateSet;

  diagnostic.scope =
      DiagnosticScope::Orchestration;

  diagnostic.explanation_key =
      std::move(explanation_key);

  diagnostic.detail =
      std::move(detail);

  return diagnostic;
}

inline void evidence(
    RoutingDiagnostic& diagnostic,
    std::string key,
    const double value,
    std::string unit) {
  DiagnosticEvidence item;

  item.key =
      std::move(key);

  item.value =
      value;

  item.unit =
      std::move(unit);

  diagnostic.evidence.push_back(
      std::move(item));
}

inline double known_coverage(
    const double analyzed_distance_m,
    const double unknown_distance_m) {
  if (!std::isfinite(analyzed_distance_m) ||
      analyzed_distance_m <= 0.0) {
    return 0.0;
  }

  if (!std::isfinite(unknown_distance_m)) {
    return 0.0;
  }

  const double bounded_unknown =
      std::clamp(
          unknown_distance_m,
          0.0,
          analyzed_distance_m);

  return std::clamp(
      (analyzed_distance_m -
       bounded_unknown) /
          analyzed_distance_m,
      0.0,
      1.0);
}

inline void append_coverage_diagnostic(
    std::vector<RoutingDiagnostic>& result,
    const RoutePath& route,
    const double analyzed_distance_m,
    const double unknown_distance_m,
    const double minimum_known_coverage,
    const char* code,
    const char* explanation_key,
    const char* attribute_name) {
  if (!std::isfinite(analyzed_distance_m) ||
      analyzed_distance_m <= 0.0) {
    return;
  }

  const double coverage =
      known_coverage(
          analyzed_distance_m,
          unknown_distance_m);

  if (coverage + 1e-12 >=
      minimum_known_coverage) {
    return;
  }

  auto diagnostic =
      route_diagnostic(
          route,
          code,
          DiagnosticSeverity::Warning,
          DiagnosticCategory::DataCoverage,
          explanation_key,
          std::string(attribute_name) +
              " known coverage is " +
              std::to_string(coverage) +
              "; missing data is preserved as unknown.");

  evidence(
      diagnostic,
      "known_coverage",
      coverage,
      "ratio");

  evidence(
      diagnostic,
      "unknown_distance_m",
      std::clamp(
          std::isfinite(unknown_distance_m)
              ? unknown_distance_m
              : analyzed_distance_m,
          0.0,
          analyzed_distance_m),
      "m");

  evidence(
      diagnostic,
      "analyzed_distance_m",
      analyzed_distance_m,
      "m");

  result.push_back(
      std::move(diagnostic));
}

inline double gradient_known_distance_m(
    const RoutePath& route) {
  double known = 0.0;

  for (const auto& segment :
       route.segments) {
    if (!segment.gradient_abs_pct.has_value() ||
        !std::isfinite(
            *segment.gradient_abs_pct)) {
      continue;
    }

    if (!std::isfinite(segment.length_m) ||
        segment.length_m <= 0.0) {
      continue;
    }

    known +=
        segment.length_m;
  }

  return known;
}

}  // namespace detail


// ---------------------------------------------------------------
// Per-route diagnostics
// ---------------------------------------------------------------

[[nodiscard]]
inline std::vector<RoutingDiagnostic>
collect_route_diagnostics(
    const RoutePath& route,
    const evaluation::RouteEvaluation& evaluation,
    const DiagnosticPolicy& policy = {}) {
  std::vector<RoutingDiagnostic>
      result;

  const double minimum_coverage =
      std::clamp(
          policy.minimum_known_coverage,
          0.0,
          1.0);

  const double zero_observation_coverage =
      std::clamp(
          policy.all_known_zero_observation_coverage,
          0.0,
          1.0);

  // Preserve backend/enrichment diagnostics as platform diagnostics.
  for (const auto& source :
       route.diagnostics) {
    auto diagnostic =
        detail::route_diagnostic(
            route,
            source.code.empty()
                ? "ROUTE_BACKEND_DIAGNOSTIC"
                : source.code,
            DiagnosticSeverity::Warning,
            DiagnosticCategory::Enrichment,
            "diagnostic.route.backend",
            source.message);

    result.push_back(
        std::move(diagnostic));
  }

  if (route.segment_data_status ==
      RouteSegmentDataStatus::Unavailable) {
    result.push_back(
        detail::route_diagnostic(
            route,
            "ROUTE_SEGMENT_DATA_UNAVAILABLE",
            DiagnosticSeverity::Warning,
            DiagnosticCategory::Enrichment,
            "diagnostic.route.segment_data_unavailable",
            "Routing geometry exists, but semantic Street Model "
            "segment enrichment is unavailable."));
  }

  if (route.segment_data_status ==
          RouteSegmentDataStatus::Complete &&
      route.segments.empty()) {
    result.push_back(
        detail::route_diagnostic(
            route,
            "ROUTE_SEGMENT_DATA_INCONSISTENT",
            DiagnosticSeverity::Error,
            DiagnosticCategory::Enrichment,
            "diagnostic.route.segment_data_inconsistent",
            "Route is marked as completely enriched but contains "
            "no Street Model segments."));
  }

  if (route.segment_data_status ==
          RouteSegmentDataStatus::Unavailable &&
      !route.segments.empty()) {
    result.push_back(
        detail::route_diagnostic(
            route,
            "ROUTE_SEGMENT_DATA_INCONSISTENT",
            DiagnosticSeverity::Error,
            DiagnosticCategory::Enrichment,
            "diagnostic.route.segment_data_inconsistent",
            "Route is marked as unavailable but still contains "
            "Street Model segments."));
  }

  if (!evaluation.segment_data_available) {
    result.push_back(
        detail::route_diagnostic(
            route,
            "ROUTE_SEMANTIC_DATA_UNAVAILABLE",
            DiagnosticSeverity::Warning,
            DiagnosticCategory::Enrichment,
            "diagnostic.route.semantic_data_unavailable",
            "No semantic route segment data is available for "
            "route evaluation."));
  }

  if (!evaluation.score_available) {
    result.push_back(
        detail::route_diagnostic(
            route,
            "ROUTE_SCORE_UNAVAILABLE",
            DiagnosticSeverity::Warning,
            DiagnosticCategory::CandidateSet,
            "diagnostic.route.score_unavailable",
            "No semantic CostEngine route score is available; "
            "this route cannot become a scored winner."));
  }

  const double analyzed =
      evaluation.analysis
          .analyzed_distance_m;

  if (!evaluation.segment_data_available ||
      !std::isfinite(analyzed) ||
      analyzed <= 0.0) {
    return result;
  }

  // -------------------------------------------------------------
  // Known / unknown coverage.
  //
  // These use existing RouteAnalysis / RouteEvaluation facts.
  // They do not independently reclassify route semantics.
  // -------------------------------------------------------------

  detail::append_coverage_diagnostic(
      result,
      route,
      analyzed,
      evaluation.analysis
          .unknown_road_class_distance_m,
      minimum_coverage,
      "DATA_COVERAGE_FUNCTIONAL_ROAD_CLASS_LOW",
      "diagnostic.data.coverage.functional_road_class_low",
      "Functional road class");

  detail::append_coverage_diagnostic(
      result,
      route,
      analyzed,
      evaluation.road_networks
          .unknown_m,
      minimum_coverage,
      "DATA_COVERAGE_ROAD_NETWORK_LOW",
      "diagnostic.data.coverage.road_network_low",
      "Road network class");

  detail::append_coverage_diagnostic(
      result,
      route,
      analyzed,
      evaluation.analysis
          .unknown_speed_limit_distance_m,
      minimum_coverage,
      "DATA_COVERAGE_SPEED_LIMIT_LOW",
      "diagnostic.data.coverage.speed_limit_low",
      "Speed limit");

  detail::append_coverage_diagnostic(
      result,
      route,
      analyzed,
      evaluation.analysis
          .unknown_curvature_distance_m,
      minimum_coverage,
      "DATA_COVERAGE_CURVATURE_LOW",
      "diagnostic.data.coverage.curvature_low",
      "Curvature");

  detail::append_coverage_diagnostic(
      result,
      route,
      analyzed,
      evaluation.analysis
          .unknown_serpentine_distance_m,
      minimum_coverage,
      "DATA_COVERAGE_SERPENTINE_LOW",
      "diagnostic.data.coverage.serpentine_low",
      "Serpentine");

  detail::append_coverage_diagnostic(
      result,
      route,
      analyzed,
      evaluation.analysis
          .unknown_urban_distance_m,
      minimum_coverage,
      "DATA_COVERAGE_URBAN_LOW",
      "diagnostic.data.coverage.urban_low",
      "Urban");

  detail::append_coverage_diagnostic(
      result,
      route,
      analyzed,
      evaluation.analysis
          .unknown_confidence_distance_m,
      minimum_coverage,
      "DATA_COVERAGE_CONFIDENCE_LOW",
      "diagnostic.data.coverage.confidence_low",
      "Data confidence");

  const double gradient_known =
      std::clamp(
          detail::gradient_known_distance_m(
              route),
          0.0,
          analyzed);

  const double gradient_unknown =
      analyzed -
      gradient_known;

  detail::append_coverage_diagnostic(
      result,
      route,
      analyzed,
      gradient_unknown,
      minimum_coverage,
      "DATA_COVERAGE_GRADIENT_LOW",
      "diagnostic.data.coverage.gradient_low",
      "Gradient");

  // -------------------------------------------------------------
  // Factual signal observation.
  //
  // IMPORTANT:
  // Zero urban-positive distance is NOT interpreted as proof of a
  // bad map/source. It is only surfaced when urban coverage is high.
  // -------------------------------------------------------------

  const double urban_coverage =
      detail::known_coverage(
          analyzed,
          evaluation.analysis
              .unknown_urban_distance_m);

  if (urban_coverage + 1e-12 >=
          zero_observation_coverage &&
      std::abs(
          evaluation.analysis
              .urban_distance_m) <=
          1e-9) {
    auto diagnostic =
        detail::route_diagnostic(
            route,
            "DATA_URBAN_POSITIVE_SIGNAL_ABSENT",
            DiagnosticSeverity::Info,
            DiagnosticCategory::DataSignal,
            "diagnostic.data.urban.positive_signal_absent",
            "Observed urban-positive distance is zero while urban "
            "coverage is high. This is an observation only and is "
            "not proof that the source data is incorrect.");

    detail::evidence(
        diagnostic,
        "known_coverage",
        urban_coverage,
        "ratio");

    detail::evidence(
        diagnostic,
        "urban_positive_distance_m",
        evaluation.analysis
            .urban_distance_m,
        "m");

    detail::evidence(
        diagnostic,
        "analyzed_distance_m",
        analyzed,
        "m");

    result.push_back(
        std::move(diagnostic));
  }

  // -------------------------------------------------------------
  // Enrichment distance sanity observation.
  // -------------------------------------------------------------

  if (std::isfinite(route.distance_m) &&
      route.distance_m > 0.0) {
    const double ratio =
        analyzed /
        route.distance_m;

    const double minimum_ratio =
        std::max(
            0.0,
            policy.minimum_segment_distance_ratio);

    const double maximum_ratio =
        std::max(
            minimum_ratio,
            policy.maximum_segment_distance_ratio);

    if (!std::isfinite(ratio) ||
        ratio < minimum_ratio ||
        ratio > maximum_ratio) {
      auto diagnostic =
          detail::route_diagnostic(
              route,
              "DATA_SEGMENT_DISTANCE_IMPLAUSIBLE",
              DiagnosticSeverity::Warning,
              DiagnosticCategory::DataCoverage,
              "diagnostic.data.segment_distance_implausible",
              "Analyzed Street Model distance is implausible "
              "relative to the backend route summary.");

      detail::evidence(
          diagnostic,
          "segment_to_route_distance_ratio",
          ratio,
          "ratio");

      detail::evidence(
          diagnostic,
          "analyzed_distance_m",
          analyzed,
          "m");

      detail::evidence(
          diagnostic,
          "reported_distance_m",
          route.distance_m,
          "m");

      result.push_back(
          std::move(diagnostic));
    }
  }

  return result;
}


// ---------------------------------------------------------------
// Candidate-orchestration diagnostics
// ---------------------------------------------------------------

[[nodiscard]]
inline std::vector<RoutingDiagnostic>
collect_candidate_orchestration_diagnostics(
    const candidates::CandidateOrchestrationResult&
        orchestration,
    const DiagnosticPolicy& policy = {}) {
  using candidates::FamilyRoutingStatus;

  std::vector<RoutingDiagnostic>
      result;

  if (orchestration.degraded_route_count > 0) {
    auto diagnostic =
        detail::orchestration_diagnostic(
            "CANDIDATE_SET_DEGRADED_ROUTES",
            DiagnosticSeverity::Warning,
            "diagnostic.candidate_set.degraded_routes",
            "One or more backend routes survived routing but have "
            "unavailable semantic enrichment.");

    detail::evidence(
        diagnostic,
        "degraded_route_count",
        static_cast<double>(
            orchestration
                .degraded_route_count),
        "count");

    detail::evidence(
        diagnostic,
        "generated_route_count",
        static_cast<double>(
            orchestration
                .generated_route_count),
        "count");

    result.push_back(
        std::move(diagnostic));
  }

  if (orchestration.generated_route_count > 0 &&
      orchestration.usable_route_count == 0) {
    auto diagnostic =
        detail::orchestration_diagnostic(
            "CANDIDATE_SET_NO_USABLE_ROUTE",
            DiagnosticSeverity::Error,
            "diagnostic.candidate_set.no_usable_route",
            "Routes were generated, but none has an allowed finite "
            "semantic CostEngine score.");

    detail::evidence(
        diagnostic,
        "generated_route_count",
        static_cast<double>(
            orchestration
                .generated_route_count),
        "count");

    detail::evidence(
        diagnostic,
        "usable_route_count",
        static_cast<double>(
            orchestration
                .usable_route_count),
        "count");

    result.push_back(
        std::move(diagnostic));
  }

  if (!orchestration.success) {
    std::string detail =
        "Candidate orchestration did not produce a successful result.";

    if (!orchestration.error_code.empty()) {
      detail +=
          " " +
          orchestration.error_code;

      if (!orchestration.error_message.empty()) {
        detail +=
            ": " +
            orchestration.error_message;
      }
    }

    result.push_back(
        detail::orchestration_diagnostic(
            "CANDIDATE_ORCHESTRATION_FAILED",
            DiagnosticSeverity::Error,
            "diagnostic.candidate_set.orchestration_failed",
            std::move(detail)));
  }

  for (const auto& run :
       orchestration.family_runs) {
    switch (run.status) {
      case FamilyRoutingStatus::RoutingFailed: {
        std::string detail =
            "Candidate-family routing failed.";

        if (!run.error_code.empty()) {
          detail +=
              " " +
              run.error_code;

          if (!run.error_message.empty()) {
            detail +=
                ": " +
                run.error_message;
          }
        }

        result.push_back(
            detail::family_diagnostic(
                run.plan.family,
                "CANDIDATE_FAMILY_ROUTING_FAILED",
                DiagnosticSeverity::Warning,
                "diagnostic.candidate_set.family_routing_failed",
                std::move(detail)));

        break;
      }

      case FamilyRoutingStatus::EmptyRoutingResult:
        result.push_back(
            detail::family_diagnostic(
                run.plan.family,
                "CANDIDATE_FAMILY_EMPTY",
                DiagnosticSeverity::Info,
                "diagnostic.candidate_set.family_empty",
                "Routing backend returned no routes for this "
                "candidate family."));
        break;

      case FamilyRoutingStatus::RoutedNoRepresentative:
        result.push_back(
            detail::family_diagnostic(
                run.plan.family,
                "CANDIDATE_FAMILY_NO_REPRESENTATIVE",
                DiagnosticSeverity::Warning,
                "diagnostic.candidate_set.family_no_representative",
                "Routes were returned for this family but no "
                "semantic representative could be selected."));
        break;

      case FamilyRoutingStatus::RoutedRepresentativeSelected:
        break;
    }

    for (std::size_t index = 0;
         index < run.routes.size();
         ++index) {
      if (index >=
          run.evaluations.size()) {
        auto diagnostic =
            detail::family_diagnostic(
                run.plan.family,
                "CANDIDATE_ROUTE_EVALUATION_MISSING",
                DiagnosticSeverity::Error,
                "diagnostic.candidate_set.route_evaluation_missing",
                "A generated route has no matching RouteEvaluation.");

        diagnostic.scope =
            DiagnosticScope::Route;

        diagnostic.route_id =
            run.routes[index].route_id;

        result.push_back(
            std::move(diagnostic));

        continue;
      }

      auto route_diagnostics =
          collect_route_diagnostics(
              run.routes[index],
              run.evaluations[index],
              policy);

      result.insert(
          result.end(),
          std::make_move_iterator(
              route_diagnostics.begin()),
          std::make_move_iterator(
              route_diagnostics.end()));
    }
  }

  return result;
}

}  // namespace routing::core::diagnostics
