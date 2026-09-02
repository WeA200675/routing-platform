#pragma once

#include <cstddef>
#include <cstdint>
#include <optional>
#include <string>
#include <vector>

#include "routing/core/model.hpp"

namespace routing::core {

struct GeoPoint {
  double latitude = 0.0;
  double longitude = 0.0;
};

enum class CandidateFamily : std::uint8_t {
  Fastest = 0,
  Shortest,
  ProfileOptimal,
  MajorRoads,
  Comfort,
  LowUrban,
  LowCurvature,
  LowGradient,
  LowTraffic,
  Energy,
  Scenic,
  Stable,
};

enum class ManeuverType : std::uint8_t {
  Unknown = 0,
  Start,
  Continue,
  TurnLeft,
  TurnRight,
  UTurn,
  Merge,
  Exit,
  RoundaboutEnter,
  RoundaboutExit,
  Arrive,
};

struct RouteManeuver {
  ManeuverType type = ManeuverType::Unknown;

  std::string instruction;

  std::vector<std::string> street_names;

  double distance_m = 0.0;
  double duration_s = 0.0;

  std::size_t begin_shape_index = 0;
  std::size_t end_shape_index = 0;

  std::optional<std::uint16_t> bearing_before_deg;
  std::optional<std::uint16_t> bearing_after_deg;

  // Original engine-specific maneuver type.
  // This preserves information that our neutral model may not yet expose.
  std::optional<std::int32_t> engine_type;
};

struct RouteRequest {
  GeoPoint origin;
  GeoPoint destination;
  std::vector<GeoPoint> via_points;
  CandidateFamily family = CandidateFamily::ProfileOptimal;
  std::size_t alternatives = 0;
  std::optional<std::string> costing_profile;
};

struct RoutePath {
  std::string route_id;

  CandidateFamily family = CandidateFamily::ProfileOptimal;

  double distance_m = 0.0;
  double duration_s = 0.0;

  // Full decoded route geometry in travel order.
  std::vector<GeoPoint> geometry;

  // Turn-by-turn guidance independent of the routing backend.
  std::vector<RouteManeuver> maneuvers;

  // Street Model segments in travel order. These represent the
  // route occurrences, so the same underlying edge may occur more than once.
  std::vector<StreetSegment> segments;

  // Convenience IDs in exactly the same order as segments.
  std::vector<std::string> segment_ids;

  std::string engine_name;
  std::string engine_version;
};

struct RoutingResult {
  bool success = false;
  std::string error_code;
  std::string error_message;
  std::vector<RoutePath> routes;
};

class IRoutingEngine {
 public:
  virtual ~IRoutingEngine() = default;

  [[nodiscard]] virtual std::string name() const = 0;
  [[nodiscard]] virtual std::string version() const = 0;
  [[nodiscard]] virtual bool ready() const = 0;

  [[nodiscard]] virtual RoutingResult route(
      const RouteRequest& request) const = 0;
};

}  // namespace routing::core