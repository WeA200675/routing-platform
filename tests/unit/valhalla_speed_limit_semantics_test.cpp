#include <cassert>
#include <cmath>
#include <string>

#include "routing/adapters/valhalla/detail/valhalla_edge_attributes.hpp"
#include "routing/adapters/valhalla/valhalla_street_segment_mapper.hpp"
#include "routing/core/route_analysis.hpp"

namespace {

bool nearly_equal(
    const double a,
    const double b,
    const double epsilon = 1e-9) {
  return std::abs(a - b) <= epsilon;
}

}  // namespace

int main() {
  using routing::adapters::valhalla::
      map_valhalla_edge_to_street_segment;
  using routing::adapters::valhalla::detail::
      parse_trace_edge_attributes_json;
  using routing::core::analyze_route_segments;

  const std::string json = R"json(
{
  "edges": [
    {
      "id": 1,
      "length": 0.1,
      "speed_limit": 0
    },
    {
      "id": 2,
      "length": 0.2,
      "speed_limit": "unlimited"
    },
    {
      "id": 3,
      "length": 0.3,
      "speed_limit": 30
    }
  ]
}
)json";

  const auto edges =
      parse_trace_edge_attributes_json(json);

  assert(edges.size() == 3);

  assert(!edges[0].speed_limit_kmh.has_value());
  assert(!edges[0].speed_limit_unlimited);

  assert(!edges[1].speed_limit_kmh.has_value());
  assert(edges[1].speed_limit_unlimited);

  assert(edges[2].speed_limit_kmh.has_value());
  assert(nearly_equal(*edges[2].speed_limit_kmh, 30.0));
  assert(!edges[2].speed_limit_unlimited);

  const auto unknown =
      map_valhalla_edge_to_street_segment(edges[0]);

  const auto unlimited =
      map_valhalla_edge_to_street_segment(edges[1]);

  const auto thirty =
      map_valhalla_edge_to_street_segment(edges[2]);

  const auto unknown_analysis =
      analyze_route_segments({unknown});

  assert(nearly_equal(
      unknown_analysis.unknown_speed_limit_distance_m,
      100.0));

  const auto unlimited_analysis =
      analyze_route_segments({unlimited});

  assert(nearly_equal(
      unlimited_analysis.unknown_speed_limit_distance_m,
      0.0));

  assert(nearly_equal(
      unlimited_analysis.speed_30_or_lower_distance_m,
      0.0));

  const auto thirty_analysis =
      analyze_route_segments({thirty});

  assert(nearly_equal(
      thirty_analysis.speed_30_or_lower_distance_m,
      300.0));

  return 0;
}
