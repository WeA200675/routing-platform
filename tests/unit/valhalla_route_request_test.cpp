#include <cassert>
#include <iostream>
#include <stdexcept>
#include <string>

#include "routing/adapters/valhalla/detail/valhalla_route_request.hpp"

namespace {

bool contains(
    const std::string& text,
    const std::string& needle) {
  return text.find(needle) !=
      std::string::npos;
}

}  // namespace

int main() {
  using routing::adapters::valhalla::detail::
      build_route_request;
  using routing::adapters::valhalla::detail::
      costing_name;
  using routing::core::RouteRequest;

  RouteRequest request;

  request.origin = {
      47.1410,
      9.5209,
  };

  request.destination = {
      47.1660,
      9.5100,
  };

  request.via_points.push_back({
      47.1530,
      9.5150,
  });

  request.costing_profile = "auto";
  request.alternatives = 2;

  const std::string json =
      build_route_request(request);

  assert(contains(
      json,
      "\"costing\":\"auto\""));

  assert(contains(
      json,
      "\"units\":\"kilometers\""));

  assert(contains(
      json,
      "\"alternates\":2"));

  assert(contains(
      json,
      "\"lat\":47.141"));

  assert(contains(
      json,
      "\"lat\":47.153"));

  assert(contains(
      json,
      "\"lat\":47.166"));

  RouteRequest default_request;

  default_request.origin = {
      47.1410,
      9.5209,
  };

  default_request.destination = {
      47.1660,
      9.5100,
  };

  const std::string default_json =
      build_route_request(
          default_request);

  assert(costing_name(
      default_request) == "auto");

  // 0 Alternativen bleibt kompatibel zum bisherigen
  // Request und schreibt das Feld nicht.
  assert(!contains(
      default_json,
      "\"alternates\""));

  bool invalid_coordinate_rejected = false;

  try {
    RouteRequest invalid =
        default_request;

    invalid.origin.latitude =
        91.0;

    (void)build_route_request(
        invalid);
  } catch (const std::invalid_argument&) {
    invalid_coordinate_rejected = true;
  }

  assert(invalid_coordinate_rejected);

  bool invalid_costing_rejected = false;

  try {
    RouteRequest invalid =
        default_request;

    invalid.costing_profile =
        "auto\"bad";

    (void)build_route_request(
        invalid);
  } catch (const std::invalid_argument&) {
    invalid_costing_rejected = true;
  }

  assert(invalid_costing_rejected);

  std::cout
      << "Valhalla route request tests passed\n";

  return 0;
}
