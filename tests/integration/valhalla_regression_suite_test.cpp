#include <cstdlib>
#include <fstream>
#include <iostream>
#include <sstream>
#include <string>

#include "routing/adapters/valhalla/valhalla_routing_engine.hpp"
#include "routing/core/testing/regression_catalog.hpp"
#include "routing/core/testing/regression_report.hpp"

namespace {

int fail(
    const std::string& message) {
  std::cerr
      << "FAIL: "
      << message
      << '\n';

  return 1;
}

}  // namespace

int main() {
  const char* config_path =
      std::getenv(
          "ROUTING_PLATFORM_VALHALLA_TEST_CONFIG");

  if (config_path == nullptr ||
      std::string(config_path).empty()) {
    std::cout
        << "SKIP: ROUTING_PLATFORM_VALHALLA_TEST_CONFIG "
        << "is not set.\n";

    return 77;
  }

  std::ifstream config_file(
      config_path);

  if (!config_file) {
    return fail(
        std::string(
            "Could not open Valhalla config: ") +
        config_path);
  }

  std::ostringstream config_buffer;
  config_buffer << config_file.rdbuf();

  routing::adapters::valhalla::
      ValhallaRoutingEngine engine(
          {config_buffer.str()});

  if (!engine.ready()) {
    return fail(
        "ValhallaRoutingEngine is not ready.");
  }

  using namespace routing::core::testing;

  const auto cases =
      builtin_regression_cases();

  const auto result =
      run_regression_suite(
          engine,
          cases);

  std::cout
      << format_regression_suite_report(
             result,
             false);

  if (!result.passed) {
    return fail(
        "At least one gating routing regression failed.");
  }

  if (result.gating_case_count < 1) {
    return fail(
        "Expected at least one gating regression.");
  }

  if (result.observe_case_count < 1) {
    return fail(
        "Expected at least one observe-only regression.");
  }

  if (result.executed_case_count < 2) {
    return fail(
        "Expected at least two executed regressions.");
  }

  return 0;
}
