#pragma once

#include <string>
#include <string_view>
#include <vector>

#include "routing/core/routing_engine.hpp"

namespace routing::adapters::valhalla::detail {

[[nodiscard]] std::string build_trace_attributes_request(
    const std::vector<routing::core::GeoPoint>& geometry,
    std::string_view costing_profile);

}  // namespace routing::adapters::valhalla::detail
