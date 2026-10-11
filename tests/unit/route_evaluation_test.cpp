#include <cassert>
#include <cmath>
#include <iostream>

#include "routing/core/evaluation/route_evaluation.hpp"

namespace {

bool nearly_equal(
    double left,
    double right,
    double tolerance = 1e-9) {
  return std::abs(left - right) <= tolerance;
}

}  // namespace

int main() {
  using namespace routing::core;
  using namespace routing::core::evaluation;

  RoutePath route;

  route.route_id = "evaluation-route";
  route.family = CandidateFamily::MajorRoads;
  route.distance_m = 1500.0;
  route.duration_s = 90.0;

  StreetSegment primary;

  primary.id = "primary";
  primary.length_m = 1000.0;
  primary.functional_road_class =
      FunctionalRoadClass::Primary;
  primary.road_network_class =
      RoadNetworkClass::FederalRoad;
  primary.speed_limit_kmh = 100.0;
  primary.curvature_score = 0.20;
  primary.serpentine_score = 0.10;
  primary.gradient_abs_pct = 2.0;
  primary.urban_score = 0.10;
  primary.data_confidence = 0.95;

  StreetSegment residential;

  residential.id = "residential";
  residential.length_m = 500.0;
  residential.functional_road_class =
      FunctionalRoadClass::Residential;
  residential.road_network_class =
      RoadNetworkClass::MunicipalRoad;
  residential.speed_limit_kmh = 30.0;
  residential.curvature_score = 0.80;
  residential.serpentine_score = 0.70;
  residential.gradient_abs_pct = 10.0;
  residential.urban_score = 0.90;
  residential.data_confidence = 0.85;

  route.segments = {
      primary,
      residential,
  };

  route.segment_ids = {
      primary.id,
      residential.id,
  };

  VehicleProfile vehicle;
  RuleSet rules;
  RoutingContext context;

  const auto evaluation =
      evaluate_route(
          route,
          vehicle,
          rules,
          context);

  assert(evaluation.route_id == route.route_id);
  assert(evaluation.segment_data_available);
  assert(evaluation.score_available);
  assert(evaluation.allowed);

  assert(
      evaluation.analysis.segment_count == 2);

  assert(nearly_equal(
      evaluation.analysis.major_road_distance_m,
      1000.0));

  assert(nearly_equal(
      evaluation.analysis.minor_road_distance_m,
      500.0));

  assert(nearly_equal(
      evaluation.analysis.speed_30_or_lower_distance_m,
      500.0));

  assert(nearly_equal(
      evaluation.functional_roads.primary_m,
      1000.0));

  assert(nearly_equal(
      evaluation.functional_roads.residential_m,
      500.0));

  assert(nearly_equal(
      evaluation.road_networks.federal_m,
      1000.0));

  assert(nearly_equal(
      evaluation.road_networks.municipal_m,
      500.0));

  assert(nearly_equal(
      evaluation.steep_gradient_distance_m,
      500.0));

  assert(nearly_equal(
      evaluation.unknown_gradient_distance_m,
      0.0));

  // RouteEvaluation muss exakt die Segmentkosten des
  // bestehenden CostEngine aggregieren.
  CostEngine engine;

  const auto first =
      engine.evaluate(
          primary,
          vehicle,
          rules,
          context);

  const auto second =
      engine.evaluate(
          residential,
          vehicle,
          rules,
          context);

  assert(nearly_equal(
      evaluation.expected_travel_seconds,
      first.expected_travel_seconds +
          second.expected_travel_seconds));

  assert(nearly_equal(
      evaluation.preference_seconds,
      first.preference_seconds +
          second.preference_seconds));

  assert(nearly_equal(
      evaluation.uncertainty_seconds,
      first.uncertainty_seconds +
          second.uncertainty_seconds));

  assert(nearly_equal(
      evaluation.total_seconds_equivalent,
      first.total_seconds_equivalent +
          second.total_seconds_equivalent));

  // Fehlende Segmentdaten sind unbekannt,
  // nicht automatisch problemfrei.
  RoutePath unenriched;

  unenriched.route_id = "unenriched";
  unenriched.distance_m = 1000.0;
  unenriched.duration_s = 60.0;

  const auto missing =
      evaluate_route(
          unenriched,
          vehicle,
          rules,
          context);

  assert(!missing.segment_data_available);
  assert(!missing.score_available);

  std::cout
      << "Route evaluation tests passed\n";

  return 0;
}
