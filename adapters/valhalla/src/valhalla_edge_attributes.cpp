#include "routing/adapters/valhalla/detail/valhalla_edge_attributes.hpp"

#include <boost/property_tree/json_parser.hpp>
#include <boost/property_tree/ptree.hpp>

#include <optional>
#include <sstream>
#include <stdexcept>
#include <string>
#include <utility>

namespace routing::adapters::valhalla::detail {

namespace {

template <typename T>
std::optional<T> get_std_optional(
    const boost::property_tree::ptree& source,
    const std::string& key) {
  const auto value = source.get_optional<T>(key);

  if (!value.has_value()) {
    return std::nullopt;
  }

  return *value;
}

void parse_speed_limit(
    const boost::property_tree::ptree& source,
    ValhallaEdgeAttributes& edge) {
  const auto raw =
      source.get_optional<std::string>("speed_limit");

  if (!raw.has_value()) {
    return;
  }

  if (*raw == "unlimited") {
    edge.speed_limit_unlimited = true;
    return;
  }

  try {
    edge.speed_limit_kmh = std::stod(*raw);
  } catch (const std::exception&) {
    throw std::runtime_error(
        "Invalid Valhalla edge.speed_limit value: " + *raw);
  }
}

}  // namespace

std::vector<ValhallaEdgeAttributes>
parse_trace_edge_attributes_json(
    const std::string& response_json) {
  std::istringstream stream(response_json);

  boost::property_tree::ptree root;
  boost::property_tree::read_json(stream, root);

  const auto edges = root.get_child_optional("edges");

  if (!edges.has_value()) {
    throw std::runtime_error(
        "Valhalla trace_attributes response has no edges array.");
  }

  std::vector<ValhallaEdgeAttributes> result;
  result.reserve(edges->size());

  for (const auto& edge_entry : *edges) {
    const auto& source = edge_entry.second;

    ValhallaEdgeAttributes edge;

    edge.id =
        get_std_optional<std::uint64_t>(
            source,
            "id");

    edge.way_id =
        get_std_optional<std::uint64_t>(
            source,
            "way_id");

    if (const auto names =
            source.get_child_optional("names")) {
      for (const auto& name_entry : *names) {
        edge.names.push_back(
            name_entry.second.get_value<std::string>());
      }
    }

    if (const auto length_km =
            source.get_optional<double>("length")) {
      edge.length_m = *length_km * 1000.0;
    }

    edge.road_class =
        get_std_optional<std::string>(
            source,
            "road_class");

    edge.use =
        get_std_optional<std::string>(
            source,
            "use");

    edge.speed_kmh =
        get_std_optional<double>(
            source,
            "speed");

    parse_speed_limit(source, edge);

    edge.surface =
        get_std_optional<std::string>(
            source,
            "surface");

    edge.curvature =
        get_std_optional<std::uint64_t>(
            source,
            "curvature");

    edge.is_urban =
        get_std_optional<bool>(
            source,
            "is_urban");

    edge.lane_count =
        get_std_optional<std::uint32_t>(
            source,
            "lane_count");

    edge.weighted_grade =
        get_std_optional<double>(
            source,
            "weighted_grade");

    edge.max_upward_grade =
        get_std_optional<double>(
            source,
            "max_upward_grade");

    edge.max_downward_grade =
        get_std_optional<double>(
            source,
            "max_downward_grade");

    edge.begin_shape_index =
        get_std_optional<std::size_t>(
            source,
            "begin_shape_index");

    edge.end_shape_index =
        get_std_optional<std::size_t>(
            source,
            "end_shape_index");

    result.push_back(std::move(edge));
  }

  return result;
}

}  // namespace routing::adapters::valhalla::detail