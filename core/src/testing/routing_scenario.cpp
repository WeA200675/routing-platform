#include "routing/core/testing/routing_scenario.hpp"

#include <cmath>
#include <stdexcept>

namespace routing::core::testing {

namespace {

void validate_point(
    const GeoPoint& point) {
  if (!std::isfinite(point.latitude) ||
      point.latitude < -90.0 ||
      point.latitude > 90.0) {
    throw std::invalid_argument(
        "Scenario latitude must be between -90 and 90.");
  }

  if (!std::isfinite(point.longitude) ||
      point.longitude < -180.0 ||
      point.longitude > 180.0) {
    throw std::invalid_argument(
        "Scenario longitude must be between -180 and 180.");
  }
}

void validate_optional_non_negative(
    const std::optional<double>& value,
    const char* name) {
  if (!value.has_value()) {
    return;
  }

  if (!std::isfinite(*value) ||
      *value < 0.0) {
    throw std::invalid_argument(
        std::string(name) +
        " must be finite and non-negative.");
  }
}

void validate_ratio(
    const double value,
    const char* name) {
  if (!std::isfinite(value) ||
      value < 0.0 ||
      value > 1.0) {
    throw std::invalid_argument(
        std::string(name) +
        " must be between 0 and 1.");
  }
}

}  // namespace

void validate_routing_scenario(
    const RoutingScenario& scenario) {
  if (scenario.id.empty()) {
    throw std::invalid_argument(
        "Routing scenario requires a stable id.");
  }

  validate_point(
      scenario.request.origin);

  validate_point(
      scenario.request.destination);

  for (const auto& via :
       scenario.request.via_points) {
    validate_point(via);
  }

  if (scenario.request.costing_profile.has_value() &&
      scenario.request.costing_profile->empty()) {
    throw std::invalid_argument(
        "Scenario costing profile must not be empty.");
  }

  validate_optional_non_negative(
      scenario.expectations
          .maximum_selected_distance_m,
      "Maximum selected distance");

  validate_optional_non_negative(
      scenario.expectations
          .maximum_selected_duration_s,
      "Maximum selected duration");

  for (const auto& expectation :
       scenario.expectations
           .selected_route_metrics) {
    validate_ratio(
        expectation.minimum_known_coverage,
        "Minimum known coverage");

    if (expectation.minimum_value.has_value()) {
      validate_ratio(
          *expectation.minimum_value,
          "Metric minimum value");
    }

    if (expectation.maximum_value.has_value()) {
      validate_ratio(
          *expectation.maximum_value,
          "Metric maximum value");
    }

    if (expectation.minimum_value.has_value() &&
        expectation.maximum_value.has_value() &&
        *expectation.minimum_value >
            *expectation.maximum_value) {
      throw std::invalid_argument(
          "Metric minimum must not exceed maximum.");
    }
  }
}

}  // namespace routing::core::testing
