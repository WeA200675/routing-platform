#pragma once

#include <cstdint>
#include <string>
#include <string_view>

#include "routing/core/testing/routing_scenario.hpp"

namespace routing::core::testing {

enum class RegressionDisposition : std::uint8_t {
  Gating = 0,
  ObserveOnly = 1,
  Disabled = 2,
};

enum class RegressionCaseSource : std::uint8_t {
  Manual = 0,
  TesterFeedback = 1,
  DriveSession = 2,
  Imported = 3,
};

struct RegressionCaseProvenance {
  RegressionCaseSource source =
      RegressionCaseSource::Manual;

  // Stable reference to the originating issue, feedback item,
  // DriveSession, import record, etc.
  std::string source_ref;

  // Optional pseudonymous reporter/tester reference.
  std::string reporter_ref;

  std::string note;
};

struct RoutingRegressionCase {
  // Version of this data contract.
  std::uint32_t schema_version = 1;

  // Stable identity across revisions.
  std::string case_id;

  // Increment whenever expectations, route definition,
  // rules or other case semantics intentionally change.
  std::uint32_t case_version = 1;

  std::string title;
  std::string issue_key;

  RegressionDisposition disposition =
      RegressionDisposition::Gating;

  RegressionCaseProvenance provenance;

  RoutingScenario scenario;
};

[[nodiscard]]
std::string_view regression_disposition_key(
    RegressionDisposition disposition);

[[nodiscard]]
std::string_view regression_case_source_key(
    RegressionCaseSource source);

void validate_regression_case(
    const RoutingRegressionCase& regression_case);

}  // namespace routing::core::testing
