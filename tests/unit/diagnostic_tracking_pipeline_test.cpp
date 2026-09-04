#include <algorithm>
#include <cassert>
#include <iostream>
#include <string>

#include "routing/core/diagnostics/anomaly_tracking_report.hpp"
#include "routing/core/diagnostics/diagnostic_evidence.hpp"
#include "routing/core/diagnostics/investigation_candidate.hpp"
#include "routing/core/testing/scenario_runner.hpp"

namespace {

routing::core::RoutePath make_route(
    const routing::core::RouteRequest& request) {
  using namespace routing::core;

  const std::string suffix =
      std::to_string(
          static_cast<int>(
              request.family));

  RoutePath route;

  route.route_id =
      "tracking-route-" +
      suffix;

  route.family =
      request.family;

  route.distance_m =
      1000.0;

  route.duration_s =
      50.0;

  route.geometry = {
      request.origin,
      request.destination,
  };

  StreetSegment segment;

  segment.id =
      "tracking-segment-" +
      suffix;

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

  segment.urban_score =
      0.10;

  segment.data_confidence =
      1.0;

  route.segments.push_back(
      segment);

  route.segment_ids.push_back(
      segment.id);

  route.engine_name =
      "tracking-fake";

  route.engine_version =
      "1";

  route.segment_data_status =
      RouteSegmentDataStatus::Complete;

  return route;
}

class FakeRoutingEngine final
    : public routing::core::IRoutingEngine {
 public:
  [[nodiscard]]
  std::string name() const override {
    return "tracking-fake";
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
        make_route(request));

    return result;
  }
};

const routing::core::diagnostics::AnomalyCluster*
find_urban_cluster(
    const routing::core::diagnostics::
        AnomalyTracker& tracker) {
  const auto found =
      std::find_if(
          tracker.clusters().begin(),
          tracker.clusters().end(),
          [](const auto& cluster) {
            return cluster.diagnostic_code ==
                "DATA_URBAN_POSITIVE_SIGNAL_ABSENT";
          });

  return found ==
          tracker.clusters().end()
      ? nullptr
      : &*found;
}

bool has_candidate(
    const std::vector<
        routing::core::diagnostics::
            InvestigationCandidate>& candidates,
    const std::string& code) {
  return std::any_of(
      candidates.begin(),
      candidates.end(),
      [&](const auto& candidate) {
        return candidate.diagnostic_code ==
            code;
      });
}

}  // namespace

int main() {
  using namespace routing::core;
  using namespace routing::core::diagnostics;
  using namespace routing::core::testing;

  FakeRoutingEngine engine;

  RoutingScenario scenario;

  scenario.id =
      "diagnostic-tracking:pipeline";

  scenario.title =
      "Diagnostic tracking observer-only pipeline";

  scenario.request.origin = {
      47.1410,
      9.5209,
  };

  scenario.request.destination = {
      47.2410,
      9.5310,
  };

  scenario.request.costing_profile =
      "auto";

  const auto scenario_result =
      run_routing_scenario(
          engine,
          scenario);

  assert(
      scenario_result.passed);

  assert(
      scenario_result.orchestration.success);

  assert(
      scenario_result.orchestration
          .selected_unique_index
          .has_value());

  const auto selected_index =
      *scenario_result.orchestration
           .selected_unique_index;

  const std::string winner_before_tracking =
      scenario_result.orchestration
          .unique_representatives[
              selected_index]
          .route
          .route_id;

  const double score_before_tracking =
      scenario_result.orchestration
          .unique_representatives[
              selected_index]
          .evaluation
          .total_seconds_equivalent;

  AnomalyTracker tracker;

  DiagnosticEvidenceContext first_context;

  first_context.source =
      DiagnosticEvidenceSource::Scenario;

  first_context.evidence_scope =
      DiagnosticEvidenceScope::LocalOnly;

  first_context.observation_id =
      "observation:scenario:1";

  first_context.source_ref =
      scenario.id;

  first_context.context_key =
      "li:vaduz-ruggell";

  first_context.version_ref =
      "fixture:v1";

  first_context.observed_at_ms =
      1000;

  tracker.ingest(
      make_diagnostic_evidence_records(
          scenario_result.diagnostics,
          first_context));

  const auto* urban_after_first =
      find_urban_cluster(
          tracker);

  assert(
      urban_after_first != nullptr);

  // Multiple routes from the same orchestration remain one
  // independent observation.
  assert(
      urban_after_first
          ->observation_ids
          .size() == 1);

  auto candidates =
      build_investigation_candidates(
          tracker.clusters());

  // INFO requires three independent observations.
  assert(
      !has_candidate(
          candidates,
          "DATA_URBAN_POSITIVE_SIGNAL_ABSENT"));

  auto second_context =
      first_context;

  second_context.source =
      DiagnosticEvidenceSource::RegressionCase;

  second_context.observation_id =
      "observation:regression:1";

  second_context.source_ref =
      "li:vaduz-ruggell:urban-signal-watch";

  second_context.version_ref =
      "fixture:v2";

  second_context.observed_at_ms =
      2000;

  tracker.ingest(
      make_diagnostic_evidence_records(
          scenario_result.diagnostics,
          second_context));

  auto third_context =
      first_context;

  third_context.source =
      DiagnosticEvidenceSource::RouteLab;

  third_context.observation_id =
      "observation:route-lab:1";

  third_context.source_ref =
      "route-lab:vaduz-ruggell";

  third_context.version_ref =
      "fixture:v3";

  third_context.observed_at_ms =
      3000;

  tracker.ingest(
      make_diagnostic_evidence_records(
          scenario_result.diagnostics,
          third_context));

  const auto* urban_cluster =
      find_urban_cluster(
          tracker);

  assert(
      urban_cluster != nullptr);

  assert(
      urban_cluster
          ->observation_ids
          .size() == 3);

  assert(
      urban_cluster
          ->version_refs
          .size() == 3);

  assert(
      urban_cluster->state ==
      InvestigationState::Observed);

  candidates =
      build_investigation_candidates(
          tracker.clusters());

  assert(
      has_candidate(
          candidates,
          "DATA_URBAN_POSITIVE_SIGNAL_ABSENT"));

  // Tracking must not mutate semantic routing output.
  assert(
      scenario_result.passed);

  assert(
      scenario_result.orchestration
          .unique_representatives[
              selected_index]
          .route
          .route_id ==
      winner_before_tracking);

  assert(
      scenario_result.orchestration
          .unique_representatives[
              selected_index]
          .evaluation
          .total_seconds_equivalent ==
      score_before_tracking);

  const std::string report =
      format_anomaly_tracking_report(
          tracker,
          candidates);

  assert(
      report.find(
          "DIAGNOSTIC ANOMALY TRACKING") !=
      std::string::npos);

  assert(
      report.find(
          "observer-only: yes") !=
      std::string::npos);

  assert(
      report.find(
          "automatic hypothesis creation: no") !=
      std::string::npos);

  assert(
      report.find(
          "automatic intelligence jobs: no") !=
      std::string::npos);

  assert(
      report.find(
          "automatic global promotion: no") !=
      std::string::npos);

  assert(
      report.find(
          "DATA_URBAN_POSITIVE_SIGNAL_ABSENT") !=
      std::string::npos);

  assert(
      report.find(
          "independent observations: 3") !=
      std::string::npos);

  std::cout
      << "Diagnostic tracking pipeline tests passed\n";

  return 0;
}
