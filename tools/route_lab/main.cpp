#include <cstdlib>
#include <fstream>
#include <iostream>
#include <optional>
#include <sstream>
#include <stdexcept>
#include <string>
#include <vector>

#include "routing/adapters/valhalla/valhalla_routing_engine.hpp"
#include "routing/core/candidates/candidate_family_plan.hpp"
#include "routing/core/testing/route_decision_report.hpp"

namespace {

struct Options {
  std::string config_path;

  bool has_origin = false;
  bool has_destination = false;

  routing::core::GeoPoint origin;
  routing::core::GeoPoint destination;

  std::vector<routing::core::GeoPoint>
      via_points;

  bool all_families = false;
  bool include_shortest = false;

  std::size_t minimum_unique = 1;

  std::optional<double>
      maximum_distance_m;

  std::optional<double>
      maximum_duration_s;
};

void usage() {
  std::cout
      << "routing_route_lab\n\n"
      << "Usage:\n"
      << "  routing_route_lab "
      << "--from LAT LON --to LAT LON [options]\n\n"
      << "Options:\n"
      << "  --config PATH          Valhalla JSON config\n"
      << "  --via LAT LON          Repeatable via point\n"
      << "  --all-families         Run all implemented families\n"
      << "  --shortest             Include shortest reference\n"
      << "  --min-unique N         Required unique candidate paths\n"
      << "  --max-distance-km KM   Regression upper bound\n"
      << "  --max-duration-min MIN Regression upper bound\n"
      << "\n"
      << "If --config is omitted, "
      << "ROUTING_PLATFORM_VALHALLA_TEST_CONFIG is used.\n";
}

double parse_double(
    const char* text,
    const char* name) {
  try {
    std::size_t consumed = 0;

    const double value =
        std::stod(
            text,
            &consumed);

    if (consumed !=
        std::string(text).size()) {
      throw std::invalid_argument(
          "trailing characters");
    }

    return value;
  } catch (...) {
    throw std::invalid_argument(
        std::string("Invalid ") +
        name +
        ": " +
        text);
  }
}

std::size_t parse_size(
    const char* text,
    const char* name) {
  try {
    std::size_t consumed = 0;

    const auto value =
        std::stoull(
            text,
            &consumed);

    if (consumed !=
        std::string(text).size()) {
      throw std::invalid_argument(
          "trailing characters");
    }

    return static_cast<std::size_t>(
        value);
  } catch (...) {
    throw std::invalid_argument(
        std::string("Invalid ") +
        name +
        ": " +
        text);
  }
}

routing::core::GeoPoint parse_point(
    const char* latitude,
    const char* longitude) {
  return {
      parse_double(
          latitude,
          "latitude"),
      parse_double(
          longitude,
          "longitude"),
  };
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

    if (argument == "--from") {
      if (index + 2 >= argc) {
        throw std::invalid_argument(
            "--from requires LAT LON.");
      }

      options.origin =
          parse_point(
              argv[index + 1],
              argv[index + 2]);

      options.has_origin = true;
      index += 2;

      continue;
    }

    if (argument == "--to") {
      if (index + 2 >= argc) {
        throw std::invalid_argument(
            "--to requires LAT LON.");
      }

      options.destination =
          parse_point(
              argv[index + 1],
              argv[index + 2]);

      options.has_destination = true;
      index += 2;

      continue;
    }

    if (argument == "--via") {
      if (index + 2 >= argc) {
        throw std::invalid_argument(
            "--via requires LAT LON.");
      }

      options.via_points.push_back(
          parse_point(
              argv[index + 1],
              argv[index + 2]));

      index += 2;

      continue;
    }

    if (argument == "--all-families") {
      options.all_families = true;
      continue;
    }

    if (argument == "--shortest") {
      options.include_shortest = true;
      continue;
    }

    if (argument == "--min-unique") {
      if (index + 1 >= argc) {
        throw std::invalid_argument(
            "--min-unique requires N.");
      }

      options.minimum_unique =
          parse_size(
              argv[++index],
              "minimum unique count");

      continue;
    }

    if (argument == "--max-distance-km") {
      if (index + 1 >= argc) {
        throw std::invalid_argument(
            "--max-distance-km requires KM.");
      }

      options.maximum_distance_m =
          parse_double(
              argv[++index],
              "maximum distance") *
          1000.0;

      continue;
    }

    if (argument == "--max-duration-min") {
      if (index + 1 >= argc) {
        throw std::invalid_argument(
            "--max-duration-min requires MIN.");
      }

      options.maximum_duration_s =
          parse_double(
              argv[++index],
              "maximum duration") *
          60.0;

      continue;
    }

    throw std::invalid_argument(
        "Unknown argument: " +
        argument);
  }

  if (!options.has_origin ||
      !options.has_destination) {
    throw std::invalid_argument(
        "--from and --to are required.");
  }

  if (options.config_path.empty()) {
    const char* environment =
        std::getenv(
            "ROUTING_PLATFORM_VALHALLA_TEST_CONFIG");

    if (environment != nullptr) {
      options.config_path =
          environment;
    }
  }

  if (options.config_path.empty()) {
    throw std::invalid_argument(
        "No Valhalla config. Use --config or "
        "ROUTING_PLATFORM_VALHALLA_TEST_CONFIG.");
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

}  // namespace

int main(
    const int argc,
    char** argv) {
  try {
    const auto options =
        parse_options(
            argc,
            argv);

    routing::adapters::valhalla::
        ValhallaRoutingEngine engine({
            read_file(
                options.config_path),
        });

    if (!engine.ready()) {
      std::cerr
          << "Valhalla routing engine is not ready.\n";

      return 2;
    }

    using namespace routing::core;
    using namespace routing::core::candidates;
    using namespace routing::core::testing;

    RoutingScenario scenario;

    scenario.id =
        "route-lab:interactive";

    scenario.title =
        "Interactive Route Lab";

    scenario.request.origin =
        options.origin;

    scenario.request.destination =
        options.destination;

    scenario.request.via_points =
        options.via_points;

    scenario.request.costing_profile =
        "auto";

    scenario.family_policy
        .include_fastest_reference =
            true;

    scenario.family_policy
        .include_shortest_reference =
            options.include_shortest ||
            options.all_families;

    if (options.all_families) {
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

    scenario.expectations
        .minimum_generated_routes =
            1;

    scenario.expectations
        .minimum_family_representatives =
            1;

    scenario.expectations
        .minimum_unique_representatives =
            options.minimum_unique;

    scenario.expectations
        .maximum_selected_distance_m =
            options.maximum_distance_m;

    scenario.expectations
        .maximum_selected_duration_s =
            options.maximum_duration_s;

    const auto result =
        run_routing_scenario(
            engine,
            scenario);

    std::cout
        << format_routing_scenario_report(
               result);

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
