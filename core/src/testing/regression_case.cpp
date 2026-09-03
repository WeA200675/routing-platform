#include "routing/core/testing/regression_case.hpp"

#include <stdexcept>

namespace routing::core::testing {

std::string_view regression_disposition_key(
    const RegressionDisposition disposition) {
  switch (disposition) {
    case RegressionDisposition::Gating:
      return "gating";

    case RegressionDisposition::ObserveOnly:
      return "observe-only";

    case RegressionDisposition::Disabled:
      return "disabled";
  }

  throw std::invalid_argument(
      "Unknown regression disposition.");
}

std::string_view regression_case_source_key(
    const RegressionCaseSource source) {
  switch (source) {
    case RegressionCaseSource::Manual:
      return "manual";

    case RegressionCaseSource::TesterFeedback:
      return "tester-feedback";

    case RegressionCaseSource::DriveSession:
      return "drive-session";

    case RegressionCaseSource::Imported:
      return "imported";
  }

  throw std::invalid_argument(
      "Unknown regression case source.");
}

void validate_regression_case(
    const RoutingRegressionCase& regression_case) {
  if (regression_case.schema_version != 1) {
    throw std::invalid_argument(
        "Unsupported regression case schema version.");
  }

  if (regression_case.case_id.empty()) {
    throw std::invalid_argument(
        "Regression case requires a stable case_id.");
  }

  if (regression_case.case_version == 0) {
    throw std::invalid_argument(
        "Regression case version must be greater than zero.");
  }

  if (regression_case.title.empty()) {
    throw std::invalid_argument(
        "Regression case requires a title.");
  }

  if (regression_case.scenario.id !=
      regression_case.case_id) {
    throw std::invalid_argument(
        "Regression case_id and scenario.id must match.");
  }

  validate_routing_scenario(
      regression_case.scenario);
}

}  // namespace routing::core::testing
