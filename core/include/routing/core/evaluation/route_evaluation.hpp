#pragma once

#include <string>
#include <vector>

#include "routing/core/cost_engine.hpp"
#include "routing/core/route_analysis.hpp"
#include "routing/core/routing_engine.hpp"

namespace routing::core::evaluation {

struct FunctionalRoadDistances {
  double motorway_m = 0.0;
  double trunk_m = 0.0;
  double primary_m = 0.0;
  double secondary_m = 0.0;
  double tertiary_m = 0.0;
  double unclassified_m = 0.0;
  double residential_m = 0.0;
  double service_m = 0.0;
  double track_m = 0.0;
  double unknown_m = 0.0;
};

struct RoadNetworkDistances {
  double federal_m = 0.0;
  double state_m = 0.0;
  double county_m = 0.0;
  double municipal_m = 0.0;
  double other_m = 0.0;
  double unknown_m = 0.0;
};

struct RouteCostContribution {
  std::string rule_id;
  std::string reason;
  double seconds_equivalent = 0.0;
};

struct RouteEvaluationOptions {
  // Absolute Steigung in Prozent.
  double steep_gradient_threshold_pct = 8.0;
};

struct RouteEvaluation {
  std::string route_id;

  CandidateFamily family =
      CandidateFamily::ProfileOptimal;

  // Werte des Routing-Backends.
  double reported_distance_m = 0.0;
  double reported_duration_s = 0.0;

  // Keine Segmentdaten bedeutet unbekannt,
  // nicht "0 problematische Meter".
  bool segment_data_available = false;

  // CostEngine konnte auf Segmentebene ausgewertet werden.
  bool score_available = false;

  // False, sobald ein Segment aufgrund harter Constraints
  // oder einer Exclude-Regel nicht erlaubt ist.
  bool allowed = true;

  RouteAnalysis analysis;

  FunctionalRoadDistances functional_roads;
  RoadNetworkDistances road_networks;

  double steep_gradient_distance_m = 0.0;
  double unknown_gradient_distance_m = 0.0;

  // Aggregation des bestehenden CostEngine.
  double expected_travel_seconds = 0.0;
  double preference_seconds = 0.0;
  double uncertainty_seconds = 0.0;
  double total_seconds_equivalent = 0.0;

  std::vector<RouteCostContribution> contributions;
};

[[nodiscard]]
RouteEvaluation evaluate_route(
    const RoutePath& route,
    const VehicleProfile& vehicle,
    const RuleSet& rules,
    const RoutingContext& context,
    RouteEvaluationOptions options = {});

}  // namespace routing::core::evaluation
