#pragma once

#include <string>

#include "routing/core/drive/drive_session.hpp"
#include "routing/core/routing_engine.hpp"

namespace routing::core::drive {

[[nodiscard]] std::string
candidate_family_id(
    CandidateFamily family);

[[nodiscard]] RouteRequestSnapshot
make_route_request_snapshot(
    const RouteRequest& request);

[[nodiscard]] RouteSnapshot
make_route_snapshot(
    const RoutePath& route);

}  // namespace routing::core::drive
