#include <cmath>
#include <cstdint>
#include <cstdlib>
#include <fstream>
#include <iomanip>
#include <iostream>
#include <optional>
#include <sstream>
#include <string>
#include <vector>

#include "routing/adapters/valhalla/valhalla_routing_engine.hpp"
#include "routing/core/navigation/navigation_runtime.hpp"

namespace {

const char*
candidate_family_key(
    const routing::core::CandidateFamily family) {
  using routing::core::CandidateFamily;

  switch (family) {
    case CandidateFamily::Fastest:
      return "fastest";
    case CandidateFamily::Shortest:
      return "shortest";
    case CandidateFamily::ProfileOptimal:
      return "profile_optimal";
    case CandidateFamily::MajorRoads:
      return "major_roads";
    case CandidateFamily::Comfort:
      return "comfort";
    case CandidateFamily::LowUrban:
      return "low_urban";
    case CandidateFamily::LowCurvature:
      return "low_curvature";
    case CandidateFamily::LowGradient:
      return "low_gradient";
    case CandidateFamily::LowTraffic:
      return "low_traffic";
    case CandidateFamily::Energy:
      return "energy";
    case CandidateFamily::Scenic:
      return "scenic";
    case CandidateFamily::Stable:
      return "stable";
  }

  return "profile_optimal";
}


const char*
maneuver_type_key(
    const routing::core::ManeuverType type) {
  using routing::core::ManeuverType;

  switch (type) {
    case ManeuverType::Unknown:
      return "unknown";
    case ManeuverType::Start:
      return "start";
    case ManeuverType::Continue:
      return "continue";
    case ManeuverType::TurnLeft:
      return "turn_left";
    case ManeuverType::TurnRight:
      return "turn_right";
    case ManeuverType::UTurn:
      return "u_turn";
    case ManeuverType::Merge:
      return "merge";
    case ManeuverType::Exit:
      return "exit";
    case ManeuverType::RoundaboutEnter:
      return "roundabout_enter";
    case ManeuverType::RoundaboutExit:
      return "roundabout_exit";
    case ManeuverType::Arrive:
      return "arrive";
  }

  return "unknown";
}


const char*
segment_data_status_key(
    const routing::core::RouteSegmentDataStatus status) {
  using routing::core::RouteSegmentDataStatus;

  switch (status) {
    case RouteSegmentDataStatus::Unspecified:
      return "unspecified";
    case RouteSegmentDataStatus::Complete:
      return "complete";
    case RouteSegmentDataStatus::Unavailable:
      return "unavailable";
  }

  return "unspecified";
}


void
write_json_string(
    std::ostream& output,
    const std::string& value) {

  static constexpr char hex[] =
      "0123456789abcdef";

  output << '"';

  for (const char raw : value) {
    const auto byte =
        static_cast<unsigned char>(
            raw);

    switch (raw) {
      case '"':
        output << "\\\"";
        break;

      case '\\':
        output << "\\\\";
        break;

      case '\b':
        output << "\\b";
        break;

      case '\f':
        output << "\\f";
        break;

      case '\n':
        output << "\\n";
        break;

      case '\r':
        output << "\\r";
        break;

      case '\t':
        output << "\\t";
        break;

      default:
        if (byte < 0x20U) {
          output
              << "\\u00"
              << hex[
                     (byte >> 4U) &
                     0x0FU]
              << hex[
                     byte &
                     0x0FU];
        } else {
          output << raw;
        }
        break;
    }
  }

  output << '"';
}


template <typename T>
void
write_optional_integer(
    std::ostream& output,
    const std::optional<T>& value) {

  if (value.has_value()) {
    output
        << static_cast<long long>(
               *value);
  } else {
    output
        << "null";
  }
}


bool
export_navigation_route_contract(
    const routing::core::RoutePath& route,
    const std::string& path,
    std::string& error) {

  std::ofstream output(
      path,
      std::ios::binary |
          std::ios::trunc);

  if (!output) {
    error =
        "Could not open route export file: " +
        path;

    return false;
  }

  output
      << std::setprecision(17);

  output << "{\n";
  output << "  \"schemaVersion\": 1,\n";

  output << "  \"routeId\": ";
  write_json_string(
      output,
      route.route_id);
  output << ",\n";

  output << "  \"family\": ";
  write_json_string(
      output,
      candidate_family_key(
          route.family));
  output << ",\n";

  output
      << "  \"distanceM\": "
      << route.distance_m
      << ",\n";

  output
      << "  \"durationS\": "
      << route.duration_s
      << ",\n";

  output << "  \"engineName\": ";
  write_json_string(
      output,
      route.engine_name);
  output << ",\n";

  output << "  \"engineVersion\": ";
  write_json_string(
      output,
      route.engine_version);
  output << ",\n";

  output << "  \"segmentDataStatus\": ";
  write_json_string(
      output,
      segment_data_status_key(
          route.segment_data_status));
  output << ",\n";

  output << "  \"geometry\": [\n";

  for (std::size_t i = 0U;
       i < route.geometry.size();
       ++i) {
    const auto& point =
        route.geometry[i];

    output
        << "    ["
        << point.latitude
        << ", "
        << point.longitude
        << "]";

    if (i + 1U <
        route.geometry.size()) {
      output << ',';
    }

    output << '\n';
  }

  output << "  ],\n";

  output << "  \"maneuvers\": [\n";

  for (std::size_t i = 0U;
       i < route.maneuvers.size();
       ++i) {
    const auto& maneuver =
        route.maneuvers[i];

    output << "    {\n";

    output << "      \"type\": ";
    write_json_string(
        output,
        maneuver_type_key(
            maneuver.type));
    output << ",\n";

    output << "      \"instruction\": ";
    write_json_string(
        output,
        maneuver.instruction);
    output << ",\n";

    output
        << "      \"distanceM\": "
        << maneuver.distance_m
        << ",\n";

    output
        << "      \"durationS\": "
        << maneuver.duration_s
        << ",\n";

    output
        << "      \"beginShapeIndex\": "
        << maneuver.begin_shape_index
        << ",\n";

    output
        << "      \"endShapeIndex\": "
        << maneuver.end_shape_index
        << ",\n";

    output
        << "      \"bearingBeforeDeg\": ";

    write_optional_integer(
        output,
        maneuver.bearing_before_deg);

    output << ",\n";

    output
        << "      \"bearingAfterDeg\": ";

    write_optional_integer(
        output,
        maneuver.bearing_after_deg);

    output << ",\n";

    output
        << "      \"engineType\": ";

    write_optional_integer(
        output,
        maneuver.engine_type);

    output << ",\n";

    output
        << "      \"streetNames\": [";

    for (std::size_t street_index = 0U;
         street_index <
             maneuver.street_names.size();
         ++street_index) {
      if (street_index > 0U) {
        output << ", ";
      }

      write_json_string(
          output,
          maneuver.street_names[
              street_index]);
    }

    output << "]\n";
    output << "    }";

    if (i + 1U <
        route.maneuvers.size()) {
      output << ',';
    }

    output << '\n';
  }

  output << "  ],\n";

  output << "  \"diagnostics\": [\n";

  for (std::size_t i = 0U;
       i < route.diagnostics.size();
       ++i) {
    const auto& diagnostic =
        route.diagnostics[i];

    output << "    {\"code\": ";

    write_json_string(
        output,
        diagnostic.code);

    output << ", \"message\": ";

    write_json_string(
        output,
        diagnostic.message);

    output << '}';

    if (i + 1U <
        route.diagnostics.size()) {
      output << ',';
    }

    output << '\n';
  }

  output << "  ]\n";
  output << "}\n";

  output.flush();

  if (!output) {
    error =
        "Failed while writing route export file: " +
        path;

    return false;
  }

  return true;
}


double
environment_double_or(
    const char* name,
    const double fallback) {

  const char* raw =
      std::getenv(
          name);

  if (raw == nullptr ||
      std::string(raw).empty()) {
    return fallback;
  }

  std::size_t consumed =
      0U;

  const std::string value =
      raw;

  const double parsed =
      std::stod(
          value,
          &consumed);

  if (consumed !=
          value.size() ||
      !std::isfinite(
          parsed)) {
    throw std::runtime_error(
        std::string(
            "Invalid environment double: ") +
        name);
  }

  return parsed;
}


routing::core::CandidateFamily
environment_candidate_family() {
  using routing::core::CandidateFamily;

  const char* raw =
      std::getenv(
          "ROUTING_PLATFORM_ROUTE_FAMILY");

  const std::string value =
      (
          raw == nullptr ||
          std::string(raw).empty()
      )
          ? "profile_optimal"
          : std::string(raw);

  if (value == "fastest") {
    return CandidateFamily::Fastest;
  }

  if (value == "shortest") {
    return CandidateFamily::Shortest;
  }

  if (value == "profile_optimal") {
    return CandidateFamily::ProfileOptimal;
  }

  if (value == "major_roads") {
    return CandidateFamily::MajorRoads;
  }

  if (value == "comfort") {
    return CandidateFamily::Comfort;
  }

  if (value == "low_urban") {
    return CandidateFamily::LowUrban;
  }

  if (value == "low_curvature") {
    return CandidateFamily::LowCurvature;
  }

  if (value == "low_gradient") {
    return CandidateFamily::LowGradient;
  }

  if (value == "low_traffic") {
    return CandidateFamily::LowTraffic;
  }

  if (value == "energy") {
    return CandidateFamily::Energy;
  }

  if (value == "scenic") {
    return CandidateFamily::Scenic;
  }

  if (value == "stable") {
    return CandidateFamily::Stable;
  }

  throw std::runtime_error(
      "Unsupported ROUTING_PLATFORM_ROUTE_FAMILY: " +
      value);
}


std::vector<routing::core::GeoPoint>
environment_via_points() {
  std::vector<routing::core::GeoPoint>
      result;

  const char* raw =
      std::getenv(
          "ROUTING_PLATFORM_ROUTE_VIA");

  if (raw == nullptr ||
      std::string(raw).empty()) {
    return result;
  }

  std::istringstream items(
      raw);

  std::string item;

  while (
      std::getline(
          items,
          item,
          ';')
  ) {
    if (item.empty()) {
      continue;
    }

    const auto comma =
        item.find(',');

    if (comma ==
        std::string::npos) {
      throw std::runtime_error(
          "Invalid ROUTING_PLATFORM_ROUTE_VIA item.");
    }

    const std::string latitude =
        item.substr(
            0U,
            comma);

    const std::string longitude =
        item.substr(
            comma + 1U);

    std::size_t latitude_consumed =
        0U;

    std::size_t longitude_consumed =
        0U;

    const double lat =
        std::stod(
            latitude,
            &latitude_consumed);

    const double lon =
        std::stod(
            longitude,
            &longitude_consumed);

    if (latitude_consumed !=
            latitude.size() ||
        longitude_consumed !=
            longitude.size() ||
        !std::isfinite(lat) ||
        !std::isfinite(lon) ||
        lat < -90.0 ||
        lat > 90.0 ||
        lon < -180.0 ||
        lon > 180.0) {
      throw std::runtime_error(
          "Invalid ROUTING_PLATFORM_ROUTE_VIA coordinate.");
    }

    result.push_back(
        {
            lat,
            lon,
        });

    if (result.size() >
        16U) {
      throw std::runtime_error(
          "Too many ROUTING_PLATFORM_ROUTE_VIA points.");
    }
  }

  return result;
}


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
  using namespace routing::core;
  using namespace routing::core::navigation;


  const char* config_path =
      std::getenv(
          "ROUTING_PLATFORM_VALHALLA_TEST_CONFIG");

  if (config_path == nullptr ||
      std::string(config_path).empty()) {
    std::cout
        << "SKIP: ROUTING_PLATFORM_VALHALLA_TEST_CONFIG is not set.\n";

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


  std::ostringstream buffer;

  buffer
      << config_file.rdbuf();


  routing::adapters::valhalla::
      ValhallaRoutingEngine engine(
          {buffer.str()});

  if (!engine.ready()) {
    return fail(
        "ValhallaRoutingEngine is not ready.");
  }


  RouteRequest request;

  request.origin = {
      environment_double_or(
          "ROUTING_PLATFORM_ROUTE_ORIGIN_LAT",
          47.1410),

      environment_double_or(
          "ROUTING_PLATFORM_ROUTE_ORIGIN_LON",
          9.5209),
  };

  request.destination = {
      environment_double_or(
          "ROUTING_PLATFORM_ROUTE_DESTINATION_LAT",
          47.1660),

      environment_double_or(
          "ROUTING_PLATFORM_ROUTE_DESTINATION_LON",
          9.5100),
  };

  request.via_points =
      environment_via_points();

  request.family =
      environment_candidate_family();

  request.costing_profile =
      "auto";


  const auto routed =
      engine.route(
          request);

  if (!routed.success) {
    return fail(
        "Routing failed: " +
        routed.error_code +
        " - " +
        routed.error_message);
  }


  if (routed.routes.size() != 1) {
    return fail(
        "Expected exactly one route.");
  }


  const auto& route =
      routed.routes.front();

  if (route.geometry.size() < 2) {
    return fail(
        "Expected Valhalla route geometry.");
  }

  if (route.maneuvers.empty()) {
    return fail(
        "Expected Valhalla route maneuvers.");
  }


  const char* export_path =
      std::getenv(
          "ROUTING_PLATFORM_NAVIGATION_ROUTE_EXPORT");

  if (export_path != nullptr &&
      !std::string(
           export_path).empty()) {

    std::string export_error;

    if (!export_navigation_route_contract(
            route,
            export_path,
            export_error)) {
      return fail(
          export_error);
    }

    std::cout
        << "EXPORT: "
        << export_path
        << '\n';
  }


  NavigationSession navigation(
      "navigation:valhalla:1",
      route);


  const auto preview =
      navigation.snapshot();

  if (preview.route_preview == nullptr) {
    return fail(
        "Navigation preview is missing.");
  }

  if (preview.route_preview->route_id !=
      route.route_id) {
    return fail(
        "Navigation preview route id mismatch.");
  }

  if (preview.route_preview->geometry.size() !=
      route.geometry.size()) {
    return fail(
        "Navigation preview geometry size mismatch.");
  }

  if (preview.route_preview->maneuvers.size() !=
      route.maneuvers.size()) {
    return fail(
        "Navigation preview maneuver size mismatch.");
  }

  if (preview.route_preview->engine_name !=
      "valhalla") {
    return fail(
        "Navigation preview lost Valhalla engine identity.");
  }


  const auto started =
      navigation.start();

  if (started.state !=
      NavigationSessionState::Navigating) {
    return fail(
        "Navigation session did not start.");
  }


  const std::size_t segment_count =
      route.geometry.size() -
      1;

  const std::size_t final_segment =
      segment_count -
      1;

  const std::size_t middle_segment =
      final_segment /
      2;


  NavigationProgressUpdate middle;

  middle.shape_segment_index =
      middle_segment;

  middle.segment_fraction =
      0.5;


  const auto moving =
      navigation.update_progress(
          middle);

  if (!(moving.progress_fraction > 0.0 &&
        moving.progress_fraction < 1.0)) {
    return fail(
        "Expected in-route navigation progress.");
  }

  if (!(moving.remaining_distance_m >
            0.0 &&
        moving.remaining_distance_m <
            route.distance_m)) {
    return fail(
        "Expected reduced remaining distance.");
  }

  if (!(moving.remaining_duration_s >
            0.0 &&
        moving.remaining_duration_s <
            route.duration_s)) {
    return fail(
        "Expected reduced remaining duration.");
  }

  if (!moving.current_maneuver.has_value()) {
    return fail(
        "Expected current Valhalla maneuver.");
  }

  if (moving.routing_engine_invoked ||
      moving.candidate_selection_invoked ||
      moving.cost_engine_invoked ||
      moving.route_recomputed) {
    return fail(
        "Navigation runtime crossed routing boundary.");
  }


  NavigationProgressUpdate finish;

  finish.shape_segment_index =
      final_segment;

  finish.segment_fraction =
      1.0;


  const auto arrived =
      navigation.update_progress(
          finish);

  if (!arrived.arrived ||
      arrived.state !=
          NavigationSessionState::Arrived) {
    return fail(
        "Expected Arrived navigation state.");
  }

  if (std::abs(
          arrived.progress_fraction -
          1.0) >
      1e-12) {
    return fail(
        "Arrival progress is not 1.0.");
  }

  if (std::abs(
          arrived.remaining_distance_m) >
      1e-6) {
    return fail(
        "Arrival still has remaining distance.");
  }

  if (std::abs(
          arrived.remaining_duration_s) >
      1e-6) {
    return fail(
        "Arrival still has remaining duration.");
  }

  if (!arrived.current_maneuver_index.has_value()) {
    return fail(
        "Arrival maneuver is missing.");
  }

  if (*arrived.current_maneuver_index !=
      route.maneuvers.size() - 1) {
    return fail(
        "Arrival did not expose final maneuver.");
  }

  if (arrived.reroute_requested ||
      arrived.route_recomputed ||
      arrived.routing_engine_invoked ||
      arrived.candidate_selection_invoked ||
      arrived.cost_engine_invoked ||
      arrived.production_route_mutation_allowed) {
    return fail(
        "Arrival snapshot crossed immutable routing boundary.");
  }


  std::cout
      << "PASS: Navigation runtime over real Valhalla route\n"
      << "  route:       "
      << route.route_id
      << '\n'
      << "  geometry:    "
      << route.geometry.size()
      << " points\n"
      << "  maneuvers:   "
      << route.maneuvers.size()
      << '\n'
      << "  distance:    "
      << route.distance_m
      << " m\n"
      << "  duration:    "
      << route.duration_s
      << " s\n";

  return 0;
}
