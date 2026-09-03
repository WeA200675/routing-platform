#include "routing/core/evaluation/route_evaluation.hpp"

#include <algorithm>
#include <cmath>
#include <limits>
#include <stdexcept>

namespace routing::core::evaluation {

namespace {

void add_functional_distance(
    FunctionalRoadDistances& distances,
    FunctionalRoadClass road_class,
    double length_m) {
  switch (road_class) {
    case FunctionalRoadClass::Motorway:
      distances.motorway_m += length_m;
      break;
    case FunctionalRoadClass::Trunk:
      distances.trunk_m += length_m;
      break;
    case FunctionalRoadClass::Primary:
      distances.primary_m += length_m;
      break;
    case FunctionalRoadClass::Secondary:
      distances.secondary_m += length_m;
      break;
    case FunctionalRoadClass::Tertiary:
      distances.tertiary_m += length_m;
      break;
    case FunctionalRoadClass::Unclassified:
      distances.unclassified_m += length_m;
      break;
    case FunctionalRoadClass::Residential:
      distances.residential_m += length_m;
      break;
    case FunctionalRoadClass::Service:
      distances.service_m += length_m;
      break;
    case FunctionalRoadClass::Track:
      distances.track_m += length_m;
      break;
    case FunctionalRoadClass::Unknown:
      distances.unknown_m += length_m;
      break;
  }
}

void add_network_distance(
    RoadNetworkDistances& distances,
    RoadNetworkClass road_class,
    double length_m) {
  switch (road_class) {
    case RoadNetworkClass::FederalRoad:
      distances.federal_m += length_m;
      break;
    case RoadNetworkClass::StateRoad:
      distances.state_m += length_m;
      break;
    case RoadNetworkClass::CountyRoad:
      distances.county_m += length_m;
      break;
    case RoadNetworkClass::MunicipalRoad:
      distances.municipal_m += length_m;
      break;
    case RoadNetworkClass::Other:
      distances.other_m += length_m;
      break;
    case RoadNetworkClass::Unknown:
      distances.unknown_m += length_m;
      break;
  }
}

void add_contribution(
    std::vector<RouteCostContribution>& target,
    const CostContribution& contribution) {
  const auto existing =
      std::find_if(
          target.begin(),
          target.end(),
          [&contribution](
              const RouteCostContribution& current) {
            return current.rule_id ==
                       contribution.rule_id &&
                   current.reason ==
                       contribution.reason;
          });

  if (existing != target.end()) {
    existing->seconds_equivalent +=
        contribution.seconds_equivalent;
    return;
  }

  target.push_back({
      contribution.rule_id,
      contribution.reason,
      contribution.seconds_equivalent,
  });
}

void validate_route_metadata(
    const RoutePath& route) {
  if (route.route_id.empty()) {
    throw std::invalid_argument(
        "Route evaluation requires a route id.");
  }

  if (!std::isfinite(route.distance_m) ||
      route.distance_m < 0.0) {
    throw std::invalid_argument(
        "Route distance must be finite and non-negative.");
  }

  if (!std::isfinite(route.duration_s) ||
      route.duration_s < 0.0) {
    throw std::invalid_argument(
        "Route duration must be finite and non-negative.");
  }
}

}  // namespace

RouteEvaluation evaluate_route(
    const RoutePath& route,
    const VehicleProfile& vehicle,
    const RuleSet& rules,
    const RoutingContext& context,
    RouteEvaluationOptions options) {
  validate_route_metadata(route);

  if (!std::isfinite(
          options.steep_gradient_threshold_pct) ||
      options.steep_gradient_threshold_pct < 0.0) {
    throw std::invalid_argument(
        "Steep gradient threshold must be finite and non-negative.");
  }

  RouteEvaluation result;

  result.route_id = route.route_id;
  result.family = route.family;
  result.reported_distance_m = route.distance_m;
  result.reported_duration_s = route.duration_s;

  // Nicht angereichert != problemfrei.
  if (route.segments.empty()) {
    result.segment_data_available = false;
    result.score_available = false;
    return result;
  }

  result.segment_data_available = true;

  result.analysis =
      analyze_route_segments(
          route.segments);

  CostEngine cost_engine;

  for (const auto& segment : route.segments) {
    if (!std::isfinite(segment.length_m) ||
        segment.length_m < 0.0) {
      throw std::invalid_argument(
          "Route segment length must be finite and non-negative.");
    }

    const double length_m =
        segment.length_m;

    add_functional_distance(
        result.functional_roads,
        segment.functional_road_class,
        length_m);

    add_network_distance(
        result.road_networks,
        segment.road_network_class,
        length_m);

    if (!segment.gradient_abs_pct.has_value()) {
      result.unknown_gradient_distance_m +=
          length_m;
    } else {
      const double gradient =
          *segment.gradient_abs_pct;

      if (!std::isfinite(gradient) ||
          gradient < 0.0) {
        throw std::invalid_argument(
            "GradientAbsPct must be finite and non-negative.");
      }

      if (gradient >=
          options.steep_gradient_threshold_pct) {
        result.steep_gradient_distance_m +=
            length_m;
      }
    }

    const SegmentCost cost =
        cost_engine.evaluate(
            segment,
            vehicle,
            rules,
            context);

    if (!cost.allowed) {
      result.allowed = false;
    }

    if (!std::isfinite(
            cost.expected_travel_seconds) ||
        !std::isfinite(
            cost.preference_seconds) ||
        !std::isfinite(
            cost.uncertainty_seconds)) {
      throw std::runtime_error(
          "CostEngine returned non-finite route cost component.");
    }

    result.expected_travel_seconds +=
        cost.expected_travel_seconds;

    result.preference_seconds +=
        cost.preference_seconds;

    result.uncertainty_seconds +=
        cost.uncertainty_seconds;

    for (const auto& contribution :
         cost.contributions) {
      add_contribution(
          result.contributions,
          contribution);
    }
  }

  result.score_available = true;

  if (!result.allowed) {
    result.total_seconds_equivalent =
        std::numeric_limits<double>::infinity();
  } else {
    result.total_seconds_equivalent =
        result.expected_travel_seconds +
        result.preference_seconds +
        result.uncertainty_seconds;
  }

  return result;
}

}  // namespace routing::core::evaluation
