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
  std::vector<GeoPoint> geometry;
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
  [[nodiscard]] virtual RoutingResult route(const RouteRequest& request) const = 0;
};

}  // namespace routing::core
