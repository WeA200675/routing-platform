#include <cassert>
#include <iostream>
#include <string>
#include <unordered_set>

#include "routing/core/testing/regression_catalog.hpp"
#include "routing/core/testing/regression_suite.hpp"

int main() {
  using namespace routing::core::testing;

  const auto cases =
      builtin_regression_cases();

  assert(cases.size() >= 2);

  validate_regression_suite(
      cases);

  bool found_gating = false;
  bool found_observe = false;
  bool found_urban_watch = false;

  std::unordered_set<std::string>
      ids;

  for (const auto& regression_case :
       cases) {
    assert(
        ids.insert(
            regression_case.case_id)
            .second);

    assert(
        regression_case.case_version >
        0);

    assert(
        regression_case.scenario.id ==
        regression_case.case_id);

    assert(
        regression_case.disposition !=
        RegressionDisposition::Disabled);

    if (regression_case.disposition ==
        RegressionDisposition::Gating) {
      found_gating = true;
    }

    if (regression_case.disposition ==
        RegressionDisposition::ObserveOnly) {
      found_observe = true;
    }

    if (regression_case.issue_key ==
        "data.urban.zero-liechtenstein") {
      found_urban_watch = true;

      assert(
          regression_case.disposition ==
          RegressionDisposition::ObserveOnly);
    }
  }

  assert(found_gating);
  assert(found_observe);
  assert(found_urban_watch);

  std::cout
      << "Regression catalog tests passed\n";

  return 0;
}
