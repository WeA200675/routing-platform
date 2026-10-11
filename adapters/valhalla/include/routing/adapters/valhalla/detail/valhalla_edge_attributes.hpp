#pragma once

#include <cstddef>
#include <cstdint>
#include <optional>
#include <string>
#include <vector>

namespace routing::adapters::valhalla::detail {

struct ValhallaEdgeAttributes {
  std::optional<std::uint64_t> id;
  std::optional<std::uint64_t> way_id;

  std::vector<std::string> names;

  std::optional<double> length_m;

  std::optional<std::string> road_class;
  std::optional<std::string> use;

  std::optional<double> speed_kmh;

  // Valhalla may return a numeric speed limit or the string "unlimited".
  std::optional<double> speed_limit_kmh;
  bool speed_limit_unlimited = false;

  std::optional<std::string> surface;

  // Preserve Valhalla's raw curvature value for now.
  std::optional<std::uint64_t> curvature;

  std::optional<bool> is_urban;
  std::optional<std::uint32_t> lane_count;

  std::optional<double> weighted_grade;
  std::optional<double> max_upward_grade;
  std::optional<double> max_downward_grade;

  std::optional<std::size_t> begin_shape_index;
  std::optional<std::size_t> end_shape_index;
};

[[nodiscard]] std::vector<ValhallaEdgeAttributes>
parse_trace_edge_attributes_json(const std::string& response_json);

}  // namespace routing::adapters::valhalla::detail