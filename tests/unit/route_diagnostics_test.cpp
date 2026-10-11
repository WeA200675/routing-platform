#include <algorithm>
#include <cassert>
#include <iostream>
#include <string>
#include <vector>

#include "routing/core/diagnostics/route_diagnostics.hpp"
#include "routing/core/evaluation/route_evaluation.hpp"

namespace {

routing::core::RoutePath make_known_route() {
  using namespace routing::core;

  RoutePath route;

  route.route_id =
      "known-route";

  route.family =
      CandidateFamily::ProfileOptimal;

  route.distance_m = 1000.0;
  route.duration_s = 50.0;

  route.geometry = {
      {47.1410, 9.5209},
      {47.1510, 9.5209},
  };

  StreetSegment segment;

  segment.id =
      "segment-known";

  segment.length_m =
      1000.0;

  segment.functional_road_class =
      FunctionalRoadClass::Primary;

  segment.road_network_class =
      RoadNetworkClass::FederalRoad;

  segment.speed_limit_kmh =
      80.0;

  segment.practical_speed_kmh =
      80.0;

  segment.curvature_score =
      0.10;

  segment.serpentine_score =
      0.10;

  segment.gradient_abs_pct =
      1.0;

  // Known, but below the urban classification threshold.
  segment.urban_score =
      0.10;

  segment.data_confidence =
      1.0;

  route.segments.push_back(
      segment);

  route.segment_ids.push_back(
      segment.id);

  route.engine_name =
      "fake";

  route.engine_version =
      "1";

  route.segment_data_status =
      RouteSegmentDataStatus::Complete;

  return route;
}

bool has_code(
    const std::vector<
        routing::core::diagnostics::RoutingDiagnostic>& diagnostics,
    const std::string& code) {
  return std::any_of(
      diagnostics.begin(),
      diagnostics.end(),
      [&](const auto& diagnostic) {
        return diagnostic.code ==
            code;
      });
}

}  // namespace

int main() {
  using namespace routing::core;
  using namespace routing::core::diagnostics;

  VehicleProfile vehicle;
  RuleSet rules;
  RoutingContext context;

  // -------------------------------------------------------------
  // High urban coverage + zero positive urban signal:
  // factual INFO observation only.
  // -------------------------------------------------------------

  const auto known_route =
      make_known_route();

  const auto known_evaluation =
      evaluation::evaluate_route(
          known_route,
          vehicle,
          rules,
          context);

  assert(
      known_evaluation
          .segment_data_available);

  assert(
      known_evaluation
          .score_available);

  const auto known_diagnostics =
      collect_route_diagnostics(
          known_route,
          known_evaluation);

  assert(
      has_code(
          known_diagnostics,
          "DATA_URBAN_POSITIVE_SIGNAL_ABSENT"));

  assert(
      !has_code(
          known_diagnostics,
          "DATA_COVERAGE_URBAN_LOW"));

  // -------------------------------------------------------------
  // Unknown urban data must NOT become a zero-urban observation.
  // It must become a coverage diagnostic.
  // -------------------------------------------------------------

  auto unknown_urban_route =
      known_route;

  unknown_urban_route.route_id =
      "unknown-urban-route";

  unknown_urban_route
      .segments.front()
      .urban_score
      .reset();

  const auto unknown_urban_evaluation =
      evaluation::evaluate_route(
          unknown_urban_route,
          vehicle,
          rules,
          context);

  const auto unknown_urban_diagnostics =
      collect_route_diagnostics(
          unknown_urban_route,
          unknown_urban_evaluation);

  assert(
      has_code(
          unknown_urban_diagnostics,
          "DATA_COVERAGE_URBAN_LOW"));

  assert(
      !has_code(
          unknown_urban_diagnostics,
          "DATA_URBAN_POSITIVE_SIGNAL_ABSENT"));

  // -------------------------------------------------------------
  // Degraded route remains a routing result, but diagnostics expose
  // why it is not semantically scoreable.
  // -------------------------------------------------------------

  RoutePath degraded;

  degraded.route_id =
      "degraded-route";

  degraded.family =
      CandidateFamily::Fastest;

  degraded.distance_m =
      1200.0;

  degraded.duration_s =
      70.0;

  degraded.segment_data_status =
      RouteSegmentDataStatus::Unavailable;

  degraded.diagnostics.push_back({
      "ROUTE_SEGMENT_ENRICHMENT_FAILED",
      "Synthetic trace failure.",
  });

  const auto degraded_evaluation =
      evaluation::evaluate_route(
          degraded,
          vehicle,
          rules,
          context);

  assert(
      !degraded_evaluation
           .segment_data_available);

  assert(
      !degraded_evaluation
           .score_available);

  const auto degraded_diagnostics =
      collect_route_diagnostics(
          degraded,
          degraded_evaluation);

  assert(
      has_code(
          degraded_diagnostics,
          "ROUTE_SEGMENT_ENRICHMENT_FAILED"));

  assert(
      has_code(
          degraded_diagnostics,
          "ROUTE_SEGMENT_DATA_UNAVAILABLE"));

  assert(
      has_code(
          degraded_diagnostics,
          "ROUTE_SCORE_UNAVAILABLE"));

  std::cout
      << "Route diagnostics tests passed\n";

  return 0;
}
