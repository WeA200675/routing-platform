#include "routing/adapters/valhalla/detail/valhalla_route_request.hpp"

#include "routing/core/candidates/candidate_family_plan.hpp"

#include <cstdint>
#include <iomanip>
#include <limits>
#include <sstream>
#include <stdexcept>
#include <string>
#include <string_view>

namespace routing::adapters::valhalla::detail {

namespace {

void validate_point(
    const routing::core::GeoPoint& point) {
  if (point.latitude < -90.0 ||
      point.latitude > 90.0) {
    throw std::invalid_argument(
        "Latitude must be between -90 and 90 degrees.");
  }

  if (point.longitude < -180.0 ||
      point.longitude > 180.0) {
    throw std::invalid_argument(
        "Longitude must be between -180 and 180 degrees.");
  }
}

void append_location(
    std::ostringstream& json,
    const routing::core::GeoPoint& point) {
  validate_point(point);

  json
      << "{\"lat\":"
      << std::setprecision(15)
      << point.latitude
      << ",\"lon\":"
      << std::setprecision(15)
      << point.longitude
      << "}";
}

void append_auto_costing_options(
    std::ostringstream& json,
    const routing::core::candidates::
        CandidateFamilyPlan& plan) {
  using routing::core::candidates::
      CandidateGenerationBias;
  using routing::core::candidates::
      has_generation_bias;

  std::ostringstream options;
  bool has_option = false;

  const auto append_key =
      [&options, &has_option](
          const std::string_view key) {
        if (has_option) {
          options << ",";
        }

        options
            << "\""
            << key
            << "\":";

        has_option = true;
      };

  if (has_generation_bias(
          plan,
          CandidateGenerationBias::
              DistanceFirst)) {
    append_key("shortest");
    options << "true";
  }

  if (has_generation_bias(
          plan,
          CandidateGenerationBias::
              PreferHighHierarchy)) {
    // Search bias only.
    // This must never be interpreted as equivalent
    // to our complete MajorRoads semantics.
    append_key("use_highways");
    options << "0.9";
  }

  if (has_generation_bias(
          plan,
          CandidateGenerationBias::
              AvoidTracks)) {
    append_key("use_tracks");
    options << "0";
  }

  if (has_generation_bias(
          plan,
          CandidateGenerationBias::
              AvoidLivingStreets)) {
    append_key(
        "use_living_streets");
    options << "0";
  }

  if (has_generation_bias(
          plan,
          CandidateGenerationBias::
              AvoidServiceRoads)) {
    append_key("service_factor");
    options << "3";
  }

  if (has_generation_bias(
          plan,
          CandidateGenerationBias::
              ReduceManeuvers)) {
    append_key("maneuver_penalty");
    options << "25";
  }

  if (!has_option) {
    return;
  }

  json
      << ",\"costing_options\":{"
      << "\"auto\":{"
      << options.str()
      << "}}";
}

}  // namespace

std::string costing_name(
    const routing::core::RouteRequest& request) {
  const std::string costing =
      request.costing_profile.value_or(
          "auto");

  if (costing.empty() ||
      costing.find_first_not_of(
          "abcdefghijklmnopqrstuvwxyz"
          "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
          "0123456789_-") !=
          std::string::npos) {
    throw std::invalid_argument(
        "Invalid Valhalla costing profile.");
  }

  return costing;
}

std::string build_route_request(
    const routing::core::RouteRequest& request) {
  if (request.alternatives >
      static_cast<std::size_t>(
          std::numeric_limits<
              std::uint32_t>::max())) {
    throw std::invalid_argument(
        "Too many Valhalla alternatives requested.");
  }

  const auto plan =
      routing::core::candidates::
          candidate_family_plan(
              request.family);

  if (!plan.implemented ||
      plan.generation_mode ==
          routing::core::candidates::
              CandidateGenerationMode::
                  Deferred) {
    throw std::invalid_argument(
        "Candidate family is not implemented "
        "for Valhalla generation: " +
        std::string(
            routing::core::candidates::
                candidate_family_key(
                    request.family)));
  }

  const std::string costing =
      costing_name(request);

  std::ostringstream json;

  json << "{\"locations\":[";

  append_location(
      json,
      request.origin);

  for (const auto& via :
       request.via_points) {
    json << ",";

    append_location(
        json,
        via);
  }

  json << ",";

  append_location(
      json,
      request.destination);

  json
      << "],\"costing\":\""
      << costing
      << "\",\"units\":\"kilometers\"";

  // Valhalla calls the requested number of
  // additional paths "alternates".
  if (request.alternatives > 0) {
    json
        << ",\"alternates\":"
        << request.alternatives;
  }

  // Our currently implemented generation biases
  // are automotive. Other backend profiles are
  // preserved without silently injecting auto options.
  if (costing == "auto") {
    append_auto_costing_options(
        json,
        plan);
  }

  json << "}";

  return json.str();
}

}  // namespace routing::adapters::valhalla::detail
