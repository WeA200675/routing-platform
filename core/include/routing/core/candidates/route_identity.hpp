#pragma once

#include <string>

#include "routing/core/routing_engine.hpp"

namespace routing::core::candidates {

// Deterministic physical-path identity.
//
// Candidate family and backend route_id are intentionally NOT part
// of the identity because the same path may be returned by multiple
// family requests.
[[nodiscard]]
std::string route_path_signature(
    const RoutePath& route);

}  // namespace routing::core::candidates
