#include <cassert>
#include <iostream>
#include <stdexcept>

#include "routing/core/testing/regression_case.hpp"

namespace {

routing::core::testing::RoutingRegressionCase
make_valid_case() {
  using namespace routing::core::testing;

  RoutingRegressionCase regression_case;

  regression_case.case_id =
      "test:case";

  regression_case.case_version = 3;

  regression_case.title =
      "Regression case model test";

  regression_case.issue_key =
      "test.issue";

  regression_case.provenance.source =
      RegressionCaseSource::TesterFeedback;

  regression_case.provenance.source_ref =
      "feedback:42";

  regression_case.provenance.reporter_ref =
      "tester-alpha";

  regression_case.scenario.id =
      regression_case.case_id;

  regression_case.scenario.request.origin = {
      47.1410,
      9.5209,
  };

  regression_case.scenario.request.destination = {
      47.2410,
      9.5310,
  };

  return regression_case;
}

}  // namespace

int main() {
  using namespace routing::core::testing;

  auto valid =
      make_valid_case();

  validate_regression_case(
      valid);

  assert(
      regression_disposition_key(
          RegressionDisposition::Gating) ==
      "gating");

  assert(
      regression_case_source_key(
          RegressionCaseSource::TesterFeedback) ==
      "tester-feedback");

  {
    auto invalid = valid;
    invalid.schema_version = 2;

    bool rejected = false;

    try {
      validate_regression_case(
          invalid);
    } catch (const std::invalid_argument&) {
      rejected = true;
    }

    assert(rejected);
  }

  {
    auto invalid = valid;
    invalid.case_version = 0;

    bool rejected = false;

    try {
      validate_regression_case(
          invalid);
    } catch (const std::invalid_argument&) {
      rejected = true;
    }

    assert(rejected);
  }

  {
    auto invalid = valid;
    invalid.scenario.id =
        "different:id";

    bool rejected = false;

    try {
      validate_regression_case(
          invalid);
    } catch (const std::invalid_argument&) {
      rejected = true;
    }

    assert(rejected);
  }

  std::cout
      << "Regression case model tests passed\n";

  return 0;
}
