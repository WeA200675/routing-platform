#pragma once

#include <cstdint>
#include <optional>
#include <string>

namespace routing::core {

enum class RoadClass : std::uint8_t {
  Unknown = 0,
  Motorway,
  FederalRoad,
  StateRoad,
  CountyRoad,
  Primary,
  Secondary,
  Residential,
  Service,
  Track,
};

struct StreetSegment {
  std::string id;
  double length_m = 0.0;
  RoadClass road_class = RoadClass::Unknown;

  std::optional<double> speed_limit_kmh;
  std::optional<double> practical_speed_kmh;
  std::optional<double> curvature_score;     // 0..1
  std::optional<double> serpentine_score;    // 0..1
  std::optional<double> gradient_abs_pct;    // absolute percent
  std::optional<double> urban_score;         // 0..1
  std::optional<double> data_confidence;     // 0..1

  bool access_allowed = true;
  bool hard_user_excluded = false;
};

struct VehicleProfile {
  std::string id = "default-car";
  double width_m = 1.85;
  double height_m = 1.60;
  double weight_kg = 1800.0;
  bool trailer = false;
};

struct RoutingContext {
  double comfort_budget_seconds = 15.0 * 60.0;
  double shortcut_threshold_seconds = 10.0 * 60.0;
  double max_segment_preference_bonus_fraction = 0.35;  // time can never be fully erased
};

}  // namespace routing::core
