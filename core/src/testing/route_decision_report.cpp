#include "routing/core/testing/route_decision_report.hpp"

#include <iomanip>
#include <sstream>
#include <string>

#include "routing/core/candidates/candidate_family_plan.hpp"
#include "routing/core/testing/route_metric.hpp"

namespace routing::core::testing {

namespace {

const char* family_status_name(
    const candidates::FamilyRoutingStatus status) {
  using candidates::FamilyRoutingStatus;

  switch (status) {
    case FamilyRoutingStatus::
        RoutedRepresentativeSelected:
      return "selected";

    case FamilyRoutingStatus::
        RoutedNoRepresentative:
      return "no-representative";

    case FamilyRoutingStatus::
        RoutingFailed:
      return "routing-failed";

    case FamilyRoutingStatus::
        EmptyRoutingResult:
      return "empty";
  }

  return "unknown";
}

std::string percentage(
    const RouteMetricValue& value) {
  if (!value.available) {
    return "n/a";
  }

  std::ostringstream result;

  result
      << std::fixed
      << std::setprecision(1)
      << value.value * 100.0
      << "%";

  if (value.known_coverage < 0.999999) {
    result
        << " (coverage "
        << std::setprecision(1)
        << value.known_coverage * 100.0
        << "%)";
  }

  return result.str();
}

void append_families(
    std::ostringstream& output,
    const std::vector<CandidateFamily>& families) {
  for (std::size_t index = 0;
       index < families.size();
       ++index) {
    if (index != 0) {
      output << ",";
    }

    output
        << candidates::candidate_family_key(
               families[index]);
  }
}

}  // namespace

std::string format_candidate_orchestration_report(
    const candidates::CandidateOrchestrationResult& result) {
  std::ostringstream output;

  output
      << "ROUTE DECISION REPORT\n"
      << "success: "
      << (result.success ? "yes" : "no")
      << "\n";

  if (!result.success) {
    output
        << "error: "
        << result.error_code
        << " - "
        << result.error_message
        << "\n";
  }

  output
      << "generated routes: "
      << result.generated_route_count
      << "\n"
      << "family representatives: "
      << result.portfolio.entries.size()
      << "\n"
      << "unique representatives: "
      << result.unique_representatives.size()
      << "\n"
      << "decision reason: "
      << result.reason_key
      << "\n\n";

  output
      << "rule generation signals [seconds/km]\n"
      << "  major roads: "
      << result.rule_profile
             .prefer_major_roads_seconds_per_km
      << "\n"
      << "  residential: "
      << result.rule_profile
             .avoid_residential_seconds_per_km
      << "\n"
      << "  speed <=30: "
      << result.rule_profile
             .avoid_speed_30_seconds_per_km
      << "\n"
      << "  urban: "
      << result.rule_profile
             .avoid_urban_seconds_per_km
      << "\n"
      << "  curvature: "
      << result.rule_profile
             .avoid_curvature_seconds_per_km
      << "\n"
      << "  serpentine: "
      << result.rule_profile
             .avoid_serpentine_seconds_per_km
      << "\n"
      << "  gradient: "
      << result.rule_profile
             .avoid_gradient_seconds_per_km
      << "\n\n";

  output << "family runs\n";

  for (const auto& run :
       result.family_runs) {
    output
        << "  "
        << candidates::candidate_family_key(
               run.plan.family)
        << ": "
        << family_status_name(
               run.status)
        << ", routes="
        << run.routes.size();

    if (!run.representative
             .selected_route_id
             .empty()) {
      output
          << ", representative="
          << run.representative
                 .selected_route_id;
    }

    if (!run.error_code.empty()) {
      output
          << ", error="
          << run.error_code;
    }

    output << "\n";
  }

  output << "\nunique candidates\n";

  for (std::size_t index = 0;
       index < result.unique_representatives.size();
       ++index) {
    const auto& candidate =
        result.unique_representatives[index];

    const bool selected =
        result.selected_unique_index.has_value() &&
        *result.selected_unique_index == index;

    const auto major =
        measure_route_metric(
            candidate.evaluation,
            RouteMetric::MajorRoadShare);

    const auto minor =
        measure_route_metric(
            candidate.evaluation,
            RouteMetric::MinorRoadShare);

    const auto speed_30 =
        measure_route_metric(
            candidate.evaluation,
            RouteMetric::Speed30OrLowerShare);

    const auto curvy =
        measure_route_metric(
            candidate.evaluation,
            RouteMetric::StronglyCurvyShare);

    const auto urban =
        measure_route_metric(
            candidate.evaluation,
            RouteMetric::UrbanShare);

    const auto steep =
        measure_route_metric(
            candidate.evaluation,
            RouteMetric::SteepGradientShare);

    output
        << (selected ? "  * SELECTED " : "  - ")
        << candidate.route.route_id
        << " families=[";

    append_families(
        output,
        candidate.represented_families);

    output
        << "]"
        << " distance="
        << std::fixed
        << std::setprecision(0)
        << candidate.route.distance_m
        << "m"
        << " duration="
        << std::setprecision(1)
        << candidate.route.duration_s
        << "s"
        << " score="
        << candidate.evaluation
               .total_seconds_equivalent
        << "\n"
        << "      major="
        << percentage(major)
        << " minor="
        << percentage(minor)
        << " <=30="
        << percentage(speed_30)
        << " curvy="
        << percentage(curvy)
        << " urban="
        << percentage(urban)
        << " steep="
        << percentage(steep)
        << "\n";
  }

  return output.str();
}

std::string format_routing_scenario_report(
    const RoutingScenarioResult& result) {
  std::ostringstream output;

  output
      << "ROUTING SCENARIO: "
      << result.scenario_id
      << "\n"
      << "scenario result: "
      << (result.passed ? "PASS" : "FAIL")
      << "\n\n"
      << format_candidate_orchestration_report(
             result.orchestration)
      << "\nassertions\n";

  for (const auto& assertion :
       result.assertions) {
    output
        << "  ["
        << (assertion.passed ? "PASS" : "FAIL")
        << "] "
        << assertion.key
        << " - "
        << assertion.detail
        << "\n";
  }

  return output.str();
}

}  // namespace routing::core::testing
