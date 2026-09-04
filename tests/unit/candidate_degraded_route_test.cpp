#include <cassert>
#include <iostream>
#include <string>

#include "routing/core/candidates/candidate_orchestrator.hpp"

namespace {

routing::core::RoutePath degraded_route(
    const routing::core::RouteRequest& request) {
  using namespace routing::core;

  RoutePath route;

  route.route_id = "degraded-fast-looking";
  route.family = request.family;

  // Deliberately looks much better in backend summary data.
  // It must still not win without semantic segment data.
  route.distance_m = 100.0;
  route.duration_s = 1.0;

  route.geometry = {
      request.origin,
      request.destination,
  };

  route.engine_name = "fake";
  route.engine_version = "1";

  route.segment_data_status =
      RouteSegmentDataStatus::Unavailable;

  route.diagnostics.push_back({
      "ROUTE_SEGMENT_ENRICHMENT_FAILED",
      "Synthetic failure",
  });

  return route;
}

routing::core::RoutePath usable_route(
    const routing::core::RouteRequest& request) {
  using namespace routing::core;

  RoutePath route;

  route.route_id = "usable-semantic-route";
  route.family = request.family;

  route.distance_m = 1000.0;
  route.duration_s = 60.0;

  route.geometry = {
      request.origin,
      request.destination,
  };

  StreetSegment segment;

  segment.id = "usable-path";
  segment.length_m = 1000.0;

  segment.functional_road_class =
      FunctionalRoadClass::Primary;

  segment.road_network_class =
      RoadNetworkClass::FederalRoad;

  segment.speed_limit_kmh = 80.0;
  segment.practical_speed_kmh = 80.0;

  segment.curvature_score = 0.10;
  segment.serpentine_score = 0.10;
  segment.gradient_abs_pct = 1.0;
  segment.urban_score = 0.10;
  segment.data_confidence = 1.0;

  route.segments.push_back(segment);
  route.segment_ids.push_back(segment.id);

  route.engine_name = "fake";
  route.engine_version = "1";

  route.segment_data_status =
      RouteSegmentDataStatus::Complete;

  return route;
}

class PartialRoutingEngine final
    : public routing::core::IRoutingEngine {
 public:
  [[nodiscard]]
  std::string name() const override {
    return "partial";
  }

  [[nodiscard]]
  std::string version() const override {
    return "1";
  }

  [[nodiscard]]
  bool ready() const override {
    return true;
  }

  [[nodiscard]]
  routing::core::RoutingResult route(
      const routing::core::RouteRequest& request) const override {
    routing::core::RoutingResult result;

    result.success = true;

    result.routes.push_back(
        degraded_route(request));

    result.routes.push_back(
        usable_route(request));

    return result;
  }
};

}  // namespace

int main() {
  using namespace routing::core;
  using namespace routing::core::candidates;

  PartialRoutingEngine engine;

  CandidateOrchestrator orchestrator(
      engine);

  RouteRequest request;

  request.origin = {
      47.1410,
      9.5209,
  };

  request.destination = {
      47.2410,
      9.5310,
  };

  VehicleProfile vehicle;
  RuleSet rules;
  RoutingContext context;

  // ProfileOptimal is mandatory and is sufficient to verify
  // mixed-quality alternatives inside one family call.
  CandidateFamilySelectionPolicy policy;

  policy.include_fastest_reference = false;
  policy.include_shortest_reference = false;

  const auto result =
      orchestrator.route(
          request,
          vehicle,
          rules,
          context,
          policy);

  assert(result.success);

  assert(
      result.generated_route_count == 2);

  assert(
      result.degraded_route_count == 1);

  assert(
      result.usable_route_count == 1);

  assert(
      result.family_runs.size() == 1);

  const auto& run =
      result.family_runs.front();

  assert(
      run.degraded_route_count == 1);

  assert(
      run.usable_route_count == 1);

  assert(
      run.status ==
      FamilyRoutingStatus::
          RoutedRepresentativeSelected);

  assert(
      run.routes.size() == 2);

  assert(
      run.evaluations.size() == 2);

  // The degraded route remains visible.
  assert(
      run.routes.front().route_id ==
      "degraded-fast-looking");

  assert(
      run.routes.front()
          .segment_data_status ==
      RouteSegmentDataStatus::Unavailable);

  // But missing Street Model data must mean no semantic score.
  assert(
      !run.evaluations.front()
           .score_available);

  assert(
      result.selected_unique_index
          .has_value());

  const auto& winner =
      result.unique_representatives[
          *result.selected_unique_index];

  // Despite its much slower backend summary, this route is the
  // only semantically evaluable candidate and therefore wins.
  assert(
      winner.route.route_id ==
      "usable-semantic-route");

  assert(
      winner.route.segment_ids.size() == 1);

  assert(
      winner.route.segment_ids.front() ==
      "usable-path");

  assert(
      winner.evaluation.score_available);

  std::cout
      << "Candidate degraded-route tests passed\n";

  return 0;
}
