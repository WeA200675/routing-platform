#include <cmath>
#include <iostream>
#include <stdexcept>
#include <string>

#include "routing/adapters/valhalla/detail/valhalla_parsing.hpp"

namespace {

int fail(const std::string& message) {
  std::cerr << "FAIL: " << message << '\n';
  return 1;
}

bool nearly_equal(
    const double left,
    const double right,
    const double tolerance = 1e-9) {
  return std::abs(left - right) <= tolerance;
}

}  // namespace

int main() {
  using routing::core::ManeuverType;
  using routing::adapters::valhalla::detail::decode_polyline6;
  using routing::adapters::valhalla::detail::map_maneuver_type;

  if (map_maneuver_type(1) != ManeuverType::Start) {
    return fail("Valhalla type 1 should map to Start.");
  }

  if (map_maneuver_type(4) != ManeuverType::Arrive) {
    return fail("Valhalla type 4 should map to Arrive.");
  }

  if (map_maneuver_type(10) != ManeuverType::TurnRight) {
    return fail("Valhalla type 10 should map to TurnRight.");
  }

  if (map_maneuver_type(15) != ManeuverType::TurnLeft) {
    return fail("Valhalla type 15 should map to TurnLeft.");
  }

  if (map_maneuver_type(25) != ManeuverType::Merge) {
    return fail("Valhalla type 25 should map to Merge.");
  }

  if (map_maneuver_type(26) != ManeuverType::RoundaboutEnter) {
    return fail(
        "Valhalla type 26 should map to RoundaboutEnter.");
  }

  if (map_maneuver_type(27) != ManeuverType::RoundaboutExit) {
    return fail(
        "Valhalla type 27 should map to RoundaboutExit.");
  }

  if (map_maneuver_type(999) != ManeuverType::Unknown) {
    return fail("Unknown Valhalla type should remain Unknown.");
  }

  const auto empty = decode_polyline6("");

  if (!empty.empty()) {
    return fail("Empty polyline should decode to no points.");
  }

  // Polyline6:
  // first point  = (0.0,       0.0)
  // second point = (0.000001, -0.000001)
  const auto points = decode_polyline6("??A@");

  if (points.size() != 2) {
    return fail(
        "Expected two decoded points, got " +
        std::to_string(points.size()));
  }

  if (!nearly_equal(points[0].latitude, 0.0) ||
      !nearly_equal(points[0].longitude, 0.0)) {
    return fail("Unexpected first decoded coordinate.");
  }

  if (!nearly_equal(points[1].latitude, 0.000001) ||
      !nearly_equal(points[1].longitude, -0.000001)) {
    return fail("Unexpected second decoded coordinate.");
  }

  bool truncated_polyline_rejected = false;

  try {
    (void)decode_polyline6("_");
  } catch (const std::runtime_error&) {
    truncated_polyline_rejected = true;
  }

  if (!truncated_polyline_rejected) {
    return fail("Truncated polyline should be rejected.");
  }

  std::cout
      << "PASS: Valhalla parsing helpers\n"
      << "  maneuver mapping: OK\n"
      << "  polyline6 decoding: OK\n"
      << "  malformed input: OK\n";

  return 0;
}