#pragma once

#include <optional>
#include <string>
#include <string_view>

#include "routing/core/drive/drive_session.hpp"
#include "routing/core/routing_engine.hpp"

namespace routing::core::drive {

[[nodiscard]] std::string
candidate_family_id(
    CandidateFamily family);

// Reverse mapping for replay/import.
// Legacy hyphenated spellings are accepted for compatibility.
[[nodiscard]]
std::optional<CandidateFamily>
candidate_family_from_id(
    std::string_view id);

[[nodiscard]] RouteRequestSnapshot
make_route_request_snapshot(
    const RouteRequest& request);

[[nodiscard]] RouteSnapshot
make_route_snapshot(
    const RoutePath& route);

}  // namespace routing::core::drive
