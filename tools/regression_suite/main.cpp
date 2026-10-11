#include <algorithm>
#include <cstdlib>
#include <fstream>
#include <iostream>
#include <sstream>
#include <stdexcept>
#include <string>
#include <vector>

#include "routing/adapters/valhalla/valhalla_routing_engine.hpp"
#include "routing/core/testing/regression_catalog.hpp"
#include "routing/core/testing/regression_report.hpp"
#include "routing/core/testing/regression_suite.hpp"

namespace {

struct Options {
  std::string config_path;

  bool list_only = false;
  bool verbose = false;

  std::vector<std::string>
      case_ids;
};

void usage() {
  std::cout
      << "routing_regression_suite\n\n"
      << "Usage:\n"
      << "  routing_regression_suite [options]\n\n"
      << "Options:\n"
      << "  --config PATH   Valhalla JSON config\n"
      << "  --list          List built-in regression cases\n"
      << "  --case ID       Run only this case; repeatable\n"
      << "  --verbose       Include full scenario reports\n"
      << "  -h, --help      Show help\n\n"
      << "If --config is omitted, "
      << "ROUTING_PLATFORM_VALHALLA_TEST_CONFIG is used.\n";
}

Options parse_options(
    const int argc,
    char** argv) {
  Options options;

  for (int index = 1;
       index < argc;
       ++index) {
    const std::string argument =
        argv[index];

    if (argument == "--help" ||
        argument == "-h") {
      usage();
      std::exit(0);
    }

    if (argument == "--config") {
      if (index + 1 >= argc) {
        throw std::invalid_argument(
            "--config requires a path.");
      }

      options.config_path =
          argv[++index];

      continue;
    }

    if (argument == "--list") {
      options.list_only = true;
      continue;
    }

    if (argument == "--verbose") {
      options.verbose = true;
      continue;
    }

    if (argument == "--case") {
      if (index + 1 >= argc) {
        throw std::invalid_argument(
            "--case requires an id.");
      }

      options.case_ids.push_back(
          argv[++index]);

      continue;
    }

    throw std::invalid_argument(
        "Unknown argument: " +
        argument);
  }

  return options;
}

std::string read_file(
    const std::string& path) {
  std::ifstream input(path);

  if (!input) {
    throw std::runtime_error(
        "Could not open: " +
        path);
  }

  std::ostringstream buffer;
  buffer << input.rdbuf();

  return buffer.str();
}

std::vector<routing::core::testing::
                RoutingRegressionCase>
filter_cases(
    const std::vector<routing::core::testing::
                          RoutingRegressionCase>& all,
    const std::vector<std::string>& requested) {
  if (requested.empty()) {
    return all;
  }

  std::vector<routing::core::testing::
                  RoutingRegressionCase>
      filtered;

  for (const auto& id :
       requested) {
    const auto found =
        std::find_if(
            all.begin(),
            all.end(),
            [&](const auto& regression_case) {
              return regression_case.case_id ==
                  id;
            });

    if (found == all.end()) {
      throw std::invalid_argument(
          "Unknown regression case: " +
          id);
    }

    filtered.push_back(
        *found);
  }

  return filtered;
}

}  // namespace

int main(
    const int argc,
    char** argv) {
  try {
    using namespace routing::core::testing;

    const auto options =
        parse_options(
            argc,
            argv);

    const auto all_cases =
        builtin_regression_cases();

    if (options.list_only) {
      for (const auto& regression_case :
           all_cases) {
        std::cout
            << regression_case.case_id
            << " v"
            << regression_case.case_version
            << " ["
            << regression_disposition_key(
                   regression_case.disposition)
            << "]"
            << " source="
            << regression_case_source_key(
                   regression_case
                       .provenance.source)
            << "\n"
            << "  "
            << regression_case.title
            << "\n";
      }

      return 0;
    }

    auto cases =
        filter_cases(
            all_cases,
            options.case_ids);

    std::string config_path =
        options.config_path;

    if (config_path.empty()) {
      const char* environment =
          std::getenv(
              "ROUTING_PLATFORM_VALHALLA_TEST_CONFIG");

      if (environment != nullptr) {
        config_path =
            environment;
      }
    }

    if (config_path.empty()) {
      throw std::invalid_argument(
          "No Valhalla config. Use --config or "
          "ROUTING_PLATFORM_VALHALLA_TEST_CONFIG.");
    }

    routing::adapters::valhalla::
        ValhallaRoutingEngine engine({
            read_file(
                config_path),
        });

    if (!engine.ready()) {
      std::cerr
          << "Valhalla routing engine is not ready.\n";

      return 2;
    }

    const auto result =
        run_regression_suite(
            engine,
            cases);

    std::cout
        << format_regression_suite_report(
               result,
               options.verbose);

    return result.passed
        ? 0
        : 1;

  } catch (const std::exception& error) {
    std::cerr
        << "ERROR: "
        << error.what()
        << "\n\n";

    usage();

    return 2;
  }
}
