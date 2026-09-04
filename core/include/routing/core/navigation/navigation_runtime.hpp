#pragma once

#include <cstddef>
#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <string_view>
#include <vector>

#include "routing/core/routing_engine.hpp"

namespace routing::core::navigation {

inline constexpr std::uint32_t
kNavigationRuntimeSchemaVersion = 1;


enum class NavigationSessionState : std::uint8_t {
  Preview = 0,
  Navigating,
  Arrived,
};


[[nodiscard]]
constexpr std::string_view
navigation_session_state_key(
    const NavigationSessionState state) {
  switch (state) {
    case NavigationSessionState::Preview:
      return "preview";

    case NavigationSessionState::Navigating:
      return "navigating";

    case NavigationSessionState::Arrived:
      return "arrived";
  }

  return "unknown";
}


// Immutable platform-neutral route presentation.
//
// It contains only information from an already selected RoutePath.
// Creating a preview never performs routing or candidate evaluation.
struct RoutePreview {
  std::uint32_t schema_version =
      kNavigationRuntimeSchemaVersion;

  std::string route_id;

  CandidateFamily family =
      CandidateFamily::ProfileOptimal;

  double distance_m = 0.0;
  double duration_s = 0.0;

  std::vector<GeoPoint> geometry;
  std::vector<RouteManeuver> maneuvers;

  std::string engine_name;
  std::string engine_version;

  RouteSegmentDataStatus segment_data_status =
      RouteSegmentDataStatus::Unspecified;

  std::vector<RouteDiagnostic> diagnostics;
};


// Progress supplied by a future map matcher / GPS adapter.
//
// shape_segment_index identifies the geometry segment:
//
//   geometry[i] -> geometry[i + 1]
//
// segment_fraction is in [0, 1].
//
// This layer deliberately does not perform map matching itself.
struct NavigationProgressUpdate {
  std::size_t shape_segment_index = 0;
  double segment_fraction = 0.0;
};


// Self-contained dynamic navigation view.
//
// route_preview is shared immutable state so repeated snapshots do not
// copy the complete route geometry on every progress update.
struct NavigationSnapshot {
  std::uint32_t schema_version =
      kNavigationRuntimeSchemaVersion;

  std::string session_id;

  NavigationSessionState state =
      NavigationSessionState::Preview;

  std::shared_ptr<const RoutePreview>
      route_preview;

  std::size_t shape_segment_index = 0;
  double segment_fraction = 0.0;

  double progress_fraction = 0.0;

  double traveled_distance_m = 0.0;
  double remaining_distance_m = 0.0;
  double remaining_duration_s = 0.0;

  std::optional<std::size_t>
      current_maneuver_index;

  std::optional<RouteManeuver>
      current_maneuver;

  std::optional<std::size_t>
      next_maneuver_index;

  std::optional<RouteManeuver>
      next_maneuver;

  // Approximate route-summary-scaled distance to the end shape index
  // of current_maneuver.
  double distance_to_current_maneuver_end_m = 0.0;

  bool arrived = false;

  // Hard runtime boundaries.
  bool reroute_requested = false;
  bool route_recomputed = false;
  bool routing_engine_invoked = false;
  bool candidate_selection_invoked = false;
  bool cost_engine_invoked = false;
  bool production_route_mutation_allowed = false;
};


[[nodiscard]]
RoutePreview make_route_preview(
    const RoutePath& route);


// Platform-neutral navigation session over one immutable selected route.
//
// State machine:
//
//   Preview
//      |
//    start()
//      |
//   Navigating
//      |
//   final geometry segment @ fraction 1
//      |
//    Arrived
//
// No backwards progress is accepted.
//
// This class does NOT:
//   - access GPS,
//   - map-match,
//   - reroute,
//   - call IRoutingEngine,
//   - call Valhalla,
//   - evaluate candidates,
//   - invoke CostEngine,
//   - mutate the selected RoutePath.
class NavigationSession {
 public:
  NavigationSession(
      std::string session_id,
      const RoutePath& route);

  [[nodiscard]]
  const std::string&
  session_id() const noexcept {
    return session_id_;
  }

  [[nodiscard]]
  NavigationSessionState
  state() const noexcept {
    return state_;
  }

  [[nodiscard]]
  const RoutePreview&
  preview() const noexcept {
    return *preview_;
  }

  [[nodiscard]]
  NavigationSnapshot
  snapshot() const;

  [[nodiscard]]
  NavigationSnapshot
  start();

  [[nodiscard]]
  NavigationSnapshot
  update_progress(
      const NavigationProgressUpdate& update);

 private:
  [[nodiscard]]
  std::optional<std::size_t>
  current_maneuver_index(
      double shape_position) const;

  [[nodiscard]]
  NavigationSnapshot
  make_snapshot() const;

  std::string session_id_;

  std::shared_ptr<const RoutePreview>
      preview_;

  std::vector<double>
      cumulative_geometry_m_;

  double geometry_length_m_ = 0.0;

  NavigationSessionState state_ =
      NavigationSessionState::Preview;

  std::size_t current_segment_index_ = 0;
  double current_segment_fraction_ = 0.0;
};

}  // namespace routing::core::navigation
