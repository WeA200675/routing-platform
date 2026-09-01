#include "routing/adapters/valhalla/detail/valhalla_parsing.hpp"

#include <cstddef>
#include <cstdint>
#include <stdexcept>
#include <string>
#include <vector>

namespace routing::adapters::valhalla::detail {

routing::core::ManeuverType map_maneuver_type(
    const std::int32_t type) {
  using routing::core::ManeuverType;

  switch (type) {
    case 1:
    case 2:
    case 3:
      return ManeuverType::Start;

    case 4:
    case 5:
    case 6:
      return ManeuverType::Arrive;

    case 7:
    case 8:
    case 17:
    case 22:
      return ManeuverType::Continue;

    case 9:
    case 10:
    case 11:
    case 18:
    case 23:
      return ManeuverType::TurnRight;

    case 12:
    case 13:
      return ManeuverType::UTurn;

    case 14:
    case 15:
    case 16:
    case 19:
    case 24:
      return ManeuverType::TurnLeft;

    case 20:
    case 21:
      return ManeuverType::Exit;

    case 25:
      return ManeuverType::Merge;

    case 26:
      return ManeuverType::RoundaboutEnter;

    case 27:
      return ManeuverType::RoundaboutExit;

    default:
      return ManeuverType::Unknown;
  }
}

std::vector<routing::core::GeoPoint> decode_polyline6(
    const std::string& encoded) {
  constexpr double kScale = 1'000'000.0;

  std::vector<routing::core::GeoPoint> points;

  std::size_t index = 0;
  std::int64_t latitude = 0;
  std::int64_t longitude = 0;

  auto decode_value =
      [&encoded, &index](std::int64_t previous) -> std::int64_t {
    std::uint64_t result = 0;
    unsigned int shift = 0;

    while (true) {
      if (index >= encoded.size()) {
        throw std::runtime_error(
            "Invalid truncated Valhalla polyline.");
      }

      const auto byte =
          static_cast<unsigned int>(
              static_cast<unsigned char>(encoded[index++])) -
          63U;

      result |=
          static_cast<std::uint64_t>(byte & 0x1FU) << shift;

      if (byte < 0x20U) {
        break;
      }

      shift += 5U;

      if (shift >= 64U) {
        throw std::runtime_error(
            "Invalid Valhalla polyline coordinate.");
      }
    }

    const std::int64_t delta =
        (result & 1U) != 0U
            ? ~static_cast<std::int64_t>(result >> 1U)
            : static_cast<std::int64_t>(result >> 1U);

    return previous + delta;
  };

  while (index < encoded.size()) {
    latitude = decode_value(latitude);
    longitude = decode_value(longitude);

    routing::core::GeoPoint point;
    point.latitude =
        static_cast<double>(latitude) / kScale;
    point.longitude =
        static_cast<double>(longitude) / kScale;

    points.push_back(point);
  }

  return points;
}

}  // namespace routing::adapters::valhalla::detail