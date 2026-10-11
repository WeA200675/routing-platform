#include "routing/core/candidates/route_identity.hpp"

#include <iomanip>
#include <sstream>

namespace routing::core::candidates {

std::string route_path_signature(
    const RoutePath& route) {
  std::ostringstream result;

  if (!route.segment_ids.empty()) {
    result << "segments:";

    for (const auto& id :
         route.segment_ids) {
      // Length-prefix avoids delimiter ambiguity.
      result
          << id.size()
          << ":"
          << id
          << ";";
    }

    return result.str();
  }

  if (!route.geometry.empty()) {
    result
        << "geometry:"
        << std::setprecision(15);

    for (const auto& point :
         route.geometry) {
      result
          << point.latitude
          << ","
          << point.longitude
          << ";";
    }

    return result.str();
  }

  result
      << "fallback:"
      << route.engine_name.size()
      << ":"
      << route.engine_name
      << ":"
      << route.route_id.size()
      << ":"
      << route.route_id;

  return result.str();
}

}  // namespace routing::core::candidates
