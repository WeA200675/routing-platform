#include "routing/adapters/valhalla/detail/valhalla_route_request.hpp"

#include <cstdint>
#include <iomanip>
#include <limits>
#include <sstream>
#include <stdexcept>
#include <string>

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

}  // namespace

std::string costing_name(
    const routing::core::RouteRequest& request) {
  const std::string costing =
      request.costing_profile.value_or("auto");

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
          std::numeric_limits<std::uint32_t>::max())) {
    throw std::invalid_argument(
        "Too many Valhalla alternatives requested.");
  }

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
      << costing_name(request)
      << "\",\"units\":\"kilometers\"";

  // Valhalla calls this field "alternates".
  // It is the requested number of additional paths,
  // not the total route count.
  if (request.alternatives > 0) {
    json
        << ",\"alternates\":"
        << request.alternatives;
  }

  json << "}";

  return json.str();
}

}  // namespace routing::adapters::valhalla::detail
