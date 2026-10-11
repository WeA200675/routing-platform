#include "routing/core/testing/scenario_runner.hpp"

#include "routing/core/diagnostics/route_diagnostics.hpp"

#include <cmath>
#include <iomanip>
#include <sstream>
#include <stdexcept>
#include <string>

namespace routing::core::testing {

namespace {

std::string number(
    const double value,
    const int precision = 3) {
  std::ostringstream result;

  result
      << std::fixed
      << std::setprecision(precision)
      << value;

  return result.str();
}

void append_assertion(
    RoutingScenarioResult& result,
    const bool passed,
    std::string key,
    std::string detail) {
  ScenarioAssertionResult assertion;

  assertion.passed = passed;
  assertion.key = std::move(key);
  assertion.detail = std::move(detail);

  result.assertions.push_back(
      std::move(assertion));
}

const evaluation::RouteEvaluation*
selected_evaluation(
    const candidates::CandidateOrchestrationResult&
        orchestration) {
  if (!orchestration
           .selected_unique_index
           .has_value()) {
    return nullptr;
  }

  const auto index =
      *orchestration
           .selected_unique_index;

  if (index >=
      orchestration
          .unique_representatives
          .size()) {
    return nullptr;
  }

  return &orchestration
              .unique_representatives[
                  index]
              .evaluation;
}

void append_metric_assertion(
    RoutingScenarioResult& result,
    const evaluation::RouteEvaluation* selected,
    const ScenarioMetricExpectation& expectation) {
  const std::string metric_key(
      route_metric_key(
          expectation.metric));

  if (selected == nullptr) {
    append_assertion(
        result,
        false,
        "selected.metric." + metric_key,
        "No selected route available.");

    return;
  }

  const auto measured =
      measure_route_metric(
          *selected,
          expectation.metric);

  if (!measured.available) {
    append_assertion(
        result,
        false,
        "selected.metric." + metric_key,
        "Metric unavailable.");

    return;
  }

  if (measured.known_coverage + 1e-12 <
      expectation.minimum_known_coverage) {
    append_assertion(
        result,
        false,
        "selected.metric." + metric_key,
        "Known coverage " +
            number(measured.known_coverage) +
            " is below required " +
            number(
                expectation
                    .minimum_known_coverage) +
            ".");

    return;
  }

  bool passed = true;

  if (expectation.minimum_value.has_value() &&
      measured.value + 1e-12 <
          *expectation.minimum_value) {
    passed = false;
  }

  if (expectation.maximum_value.has_value() &&
      measured.value - 1e-12 >
          *expectation.maximum_value) {
    passed = false;
  }

  std::string detail =
      "value=" +
      number(measured.value) +
      ", known_coverage=" +
      number(measured.known_coverage);

  if (expectation.minimum_value.has_value()) {
    detail +=
        ", min=" +
        number(
            *expectation.minimum_value);
  }

  if (expectation.maximum_value.has_value()) {
    detail +=
        ", max=" +
        number(
            *expectation.maximum_value);
  }

  append_assertion(
      result,
      passed,
      "selected.metric." + metric_key,
      std::move(detail));
}

}  // namespace

RoutingScenarioResult run_routing_scenario(
    const IRoutingEngine& routing_engine,
    const RoutingScenario& scenario) {
  RoutingScenarioResult result;

  result.scenario_id =
      scenario.id;

  try {
    validate_routing_scenario(
        scenario);

    append_assertion(
        result,
        true,
        "scenario.valid",
        "Scenario validation passed.");
  } catch (const std::exception& error) {
    append_assertion(
        result,
        false,
        "scenario.valid",
        error.what());

    result.passed = false;

    return result;
  }

  candidates::CandidateOrchestrator orchestrator(
      routing_engine);

  result.orchestration =
      orchestrator.route(
          scenario.request,
          scenario.vehicle,
          scenario.rules,
          scenario.context,
          scenario.family_policy);

  // Diagnostics are collected after routing/evaluation.
  // They do not alter orchestration, assertions or pass/fail semantics.
  result.diagnostics =
      routing::core::diagnostics::
          collect_candidate_orchestration_diagnostics(
              result.orchestration);

  if (scenario.expectations
          .require_orchestration_success) {
    append_assertion(
        result,
        result.orchestration.success,
        "orchestration.success",
        result.orchestration.success
            ? "Candidate orchestration succeeded."
            : result.orchestration.error_code +
                  ": " +
                  result.orchestration.error_message);
  }

  append_assertion(
      result,
      result.orchestration
              .generated_route_count >=
          scenario.expectations
              .minimum_generated_routes,
      "orchestration.minimum_generated_routes",
      "actual=" +
          std::to_string(
              result.orchestration
                  .generated_route_count) +
          ", required=" +
          std::to_string(
              scenario.expectations
                  .minimum_generated_routes));

  append_assertion(
      result,
      result.orchestration
              .portfolio.entries.size() >=
          scenario.expectations
              .minimum_family_representatives,
      "orchestration.minimum_family_representatives",
      "actual=" +
          std::to_string(
              result.orchestration
                  .portfolio.entries.size()) +
          ", required=" +
          std::to_string(
              scenario.expectations
                  .minimum_family_representatives));

  append_assertion(
      result,
      result.orchestration
              .unique_representatives.size() >=
          scenario.expectations
              .minimum_unique_representatives,
      "orchestration.minimum_unique_representatives",
      "actual=" +
          std::to_string(
              result.orchestration
                  .unique_representatives.size()) +
          ", required=" +
          std::to_string(
              scenario.expectations
                  .minimum_unique_representatives));

  const auto* selected =
      selected_evaluation(
          result.orchestration);

  if (scenario.expectations
          .require_selected_allowed) {
    const bool selected_ok =
        selected != nullptr &&
        selected->allowed &&
        selected->score_available &&
        std::isfinite(
            selected
                ->total_seconds_equivalent);

    append_assertion(
        result,
        selected_ok,
        "selected.allowed_and_scored",
        selected_ok
            ? "Selected route is allowed and scored."
            : "No allowed scored selected route.");
  }

  if (scenario.expectations
          .maximum_selected_distance_m
          .has_value()) {
    const bool passed =
        selected != nullptr &&
        selected->reported_distance_m <=
            *scenario.expectations
                 .maximum_selected_distance_m +
                1e-9;

    append_assertion(
        result,
        passed,
        "selected.maximum_distance",
        selected == nullptr
            ? "No selected route available."
            : "actual_m=" +
                  number(
                      selected
                          ->reported_distance_m,
                      1) +
                  ", maximum_m=" +
                  number(
                      *scenario.expectations
                           .maximum_selected_distance_m,
                      1));
  }

  if (scenario.expectations
          .maximum_selected_duration_s
          .has_value()) {
    const bool passed =
        selected != nullptr &&
        selected->reported_duration_s <=
            *scenario.expectations
                 .maximum_selected_duration_s +
                1e-9;

    append_assertion(
        result,
        passed,
        "selected.maximum_duration",
        selected == nullptr
            ? "No selected route available."
            : "actual_s=" +
                  number(
                      selected
                          ->reported_duration_s,
                      1) +
                  ", maximum_s=" +
                  number(
                      *scenario.expectations
                           .maximum_selected_duration_s,
                      1));
  }

  for (const auto& expectation :
       scenario.expectations
           .selected_route_metrics) {
    append_metric_assertion(
        result,
        selected,
        expectation);
  }

  result.passed = true;

  for (const auto& assertion :
       result.assertions) {
    if (!assertion.passed) {
      result.passed = false;
      break;
    }
  }

  return result;
}

}  // namespace routing::core::testing
