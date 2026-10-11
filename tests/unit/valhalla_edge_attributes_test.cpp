#include <cassert>
#include <cmath>
#include <iostream>
#include <stdexcept>
#include <string>

#include "routing/adapters/valhalla/detail/valhalla_edge_attributes.hpp"

namespace {

bool nearly_equal(
    const double a,
    const double b,
    const double epsilon = 1e-9) {
  return std::abs(a - b) <= epsilon;
}

}  // namespace

int main() {
  using routing::adapters::valhalla::detail::
      parse_trace_edge_attributes_json;

  const std::string json = R"({
    "edges": [
      {
        "id": 123456,
        "way_id": 987654,
        "names": ["Example Road", "B 1"],
        "length": 1.25,
        "road_class": "primary",
        "use": "road",
        "speed": 78,
        "speed_limit": 100,
        "surface": "paved",
        "curvature": 3,
        "is_urban": false,
        "lane_count": 2,
        "weighted_grade": 1.5,
        "max_upward_grade": 4.0,
        "max_downward_grade": -3.0,
        "begin_shape_index": 4,
        "end_shape_index": 12
      },
      {
        "id": 123457,
        "length": 0.5,
        "road_class": "motorway",
        "speed_limit": "unlimited"
      },
      {
        "id": 123458
      }
    ]
  })";

  const auto edges =
      parse_trace_edge_attributes_json(json);

  assert(edges.size() == 3);

  const auto& first = edges[0];

  assert(first.id.has_value());
  assert(*first.id == 123456);

  assert(first.way_id.has_value());
  assert(*first.way_id == 987654);

  assert(first.names.size() == 2);
  assert(first.names[0] == "Example Road");
  assert(first.names[1] == "B 1");

  assert(first.length_m.has_value());
  assert(nearly_equal(*first.length_m, 1250.0));

  assert(first.road_class == "primary");
  assert(first.use == "road");

  assert(first.speed_kmh.has_value());
  assert(nearly_equal(*first.speed_kmh, 78.0));

  assert(first.speed_limit_kmh.has_value());
  assert(nearly_equal(*first.speed_limit_kmh, 100.0));
  assert(!first.speed_limit_unlimited);

  assert(first.surface == "paved");

  assert(first.curvature.has_value());
  assert(*first.curvature == 3);

  assert(first.is_urban.has_value());
  assert(!*first.is_urban);

  assert(first.lane_count.has_value());
  assert(*first.lane_count == 2);

  assert(first.weighted_grade.has_value());
  assert(nearly_equal(*first.weighted_grade, 1.5));

  assert(first.max_upward_grade.has_value());
  assert(nearly_equal(*first.max_upward_grade, 4.0));

  assert(first.max_downward_grade.has_value());
  assert(nearly_equal(*first.max_downward_grade, -3.0));

  assert(first.begin_shape_index.has_value());
  assert(*first.begin_shape_index == 4);

  assert(first.end_shape_index.has_value());
  assert(*first.end_shape_index == 12);

  const auto& unlimited = edges[1];

  assert(unlimited.speed_limit_unlimited);
  assert(!unlimited.speed_limit_kmh.has_value());
  assert(unlimited.length_m.has_value());
  assert(nearly_equal(*unlimited.length_m, 500.0));

  const auto& sparse = edges[2];

  assert(sparse.id.has_value());
  assert(!sparse.way_id.has_value());
  assert(!sparse.length_m.has_value());
  assert(!sparse.speed_limit_kmh.has_value());
  assert(!sparse.speed_limit_unlimited);

  bool missing_edges_threw = false;

  try {
    (void)parse_trace_edge_attributes_json(
        R"({"shape":"abc"})");
  } catch (const std::runtime_error&) {
    missing_edges_threw = true;
  }

  assert(missing_edges_threw);

  std::cout
      << "Valhalla edge attribute parser tests passed\n";

  return 0;
}