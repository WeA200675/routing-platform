#pragma once

#include <string>

#include "routing/core/routing_engine.hpp"

namespace routing::adapters::valhalla::detail {

[[nodiscard]] std::string costing_name(
    const routing::core::RouteRequest& request);

[[nodiscard]] std::string build_route_request(
    const routing::core::RouteRequest& request);

}  // namespace routing::adapters::valhalla::detail
