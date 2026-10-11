#include "routing/core/testing/regression_catalog.hpp"

#include "routing/core/candidates/candidate_family_plan.hpp"

namespace routing::core::testing {

namespace {

RoutingScenario liechtenstein_base_scenario(
    const std::string& id) {
  RoutingScenario scenario;

  scenario.id = id;

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

  return scenario;
}

void force_all_implemented_families(
    RoutingScenario& scenario) {
  using namespace candidates;

  scenario.family_policy
      .include_fastest_reference =
          true;

  scenario.family_policy
      .include_shortest_reference =
          true;

  for (const auto& plan :
       all_candidate_family_plans()) {
    if (!plan.implemented) {
      continue;
    }

    if (plan.family ==
            CandidateFamily::Fastest ||
        plan.family ==
            CandidateFamily::Shortest ||
        plan.family ==
            CandidateFamily::ProfileOptimal) {
      continue;
    }

    scenario.family_policy
        .forced_families
        .push_back(
            plan.family);
  }
}

RoutingRegressionCase candidate_baseline() {
  RoutingRegressionCase regression_case;

  regression_case.case_id =
      "li:vaduz-ruggell:candidate-baseline";

  regression_case.case_version = 1;

  regression_case.title =
      "Vaduz to Ruggell candidate portfolio baseline";

  regression_case.issue_key =
      "routing.candidate-diversity.baseline";

  regression_case.disposition =
      RegressionDisposition::Gating;

  regression_case.provenance.source =
      RegressionCaseSource::Manual;

  regression_case.provenance.source_ref =
      "foundation-route-lab";

  regression_case.provenance.note =
      "Initial real Valhalla candidate regression corridor.";

  regression_case.scenario =
      liechtenstein_base_scenario(
          regression_case.case_id);

  force_all_implemented_families(
      regression_case.scenario);

  regression_case.scenario.expectations
      .minimum_generated_routes =
          8;

  regression_case.scenario.expectations
      .minimum_family_representatives =
          5;

  regression_case.scenario.expectations
      .minimum_unique_representatives =
          2;

  regression_case.scenario.expectations
      .maximum_selected_distance_m =
          25000.0;

  regression_case.scenario.expectations
      .maximum_selected_duration_s =
          1800.0;

  ScenarioMetricExpectation road_data;

  road_data.metric =
      RouteMetric::KnownRoadClassCoverage;

  road_data.minimum_value = 0.80;
  road_data.minimum_known_coverage = 1.0;

  regression_case.scenario.expectations
      .selected_route_metrics
      .push_back(
          road_data);

  return regression_case;
}

RoutingRegressionCase urban_signal_watch() {
  RoutingRegressionCase regression_case;

  regression_case.case_id =
      "li:vaduz-ruggell:urban-signal-watch";

  regression_case.case_version = 1;

  regression_case.title =
      "Watch suspicious zero urban signal on Vaduz-Ruggell";

  regression_case.issue_key =
      "data.urban.zero-liechtenstein";

  regression_case.disposition =
      RegressionDisposition::ObserveOnly;

  regression_case.provenance.source =
      RegressionCaseSource::Manual;

  regression_case.provenance.source_ref =
      "valhalla-enrichment-observation";

  regression_case.provenance.note =
      "Urban distance previously observed as zero. "
      "Do not silently accept zero as truth.";

  regression_case.scenario =
      liechtenstein_base_scenario(
          regression_case.case_id);

  regression_case.scenario.family_policy
      .include_fastest_reference =
          true;

  regression_case.scenario.family_policy
      .include_shortest_reference =
          false;

  regression_case.scenario.expectations
      .minimum_generated_routes =
          2;

  regression_case.scenario.expectations
      .minimum_family_representatives =
          2;

  regression_case.scenario.expectations
      .minimum_unique_representatives =
          1;

  ScenarioMetricExpectation urban;

  urban.metric =
      RouteMetric::UrbanShare;

  // This is deliberately ObserveOnly. If current data still reports
  // every segment as non-urban, this case remains visibly red without
  // blocking the suite. If enrichment improves, it can become green
  // naturally and later be promoted to a stronger invariant.
  urban.minimum_value = 0.01;
  urban.minimum_known_coverage = 0.80;

  regression_case.scenario.expectations
      .selected_route_metrics
      .push_back(
          urban);

  return regression_case;
}

}  // namespace

std::vector<RoutingRegressionCase>
builtin_regression_cases() {
  return {
      candidate_baseline(),
      urban_signal_watch(),
  };
}

}  // namespace routing::core::testing
