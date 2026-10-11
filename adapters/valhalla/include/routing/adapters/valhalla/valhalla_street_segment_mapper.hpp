#pragma once

#include <string_view>

#include "routing/adapters/valhalla/detail/valhalla_edge_attributes.hpp"
#include "routing/core/model.hpp"

namespace routing::adapters::valhalla {

[[nodiscard]] routing::core::FunctionalRoadClass
map_valhalla_functional_road_class(std::string_view road_class);

[[nodiscard]] routing::core::StreetSegment
map_valhalla_edge_to_street_segment(
    const detail::ValhallaEdgeAttributes& edge);

}  // namespace routing::adapters::valhalla
