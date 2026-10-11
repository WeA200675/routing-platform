#pragma once

#include <cstdint>
#include <string>
#include <vector>

#include "routing/core/routing_engine.hpp"

namespace routing::adapters::valhalla::detail {

[[nodiscard]] routing::core::ManeuverType map_maneuver_type(
    std::int32_t type);

[[nodiscard]] std::vector<routing::core::GeoPoint> decode_polyline6(
    const std::string& encoded);

}  // namespace routing::adapters::valhalla::detail