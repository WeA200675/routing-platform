#include "routing/core/navigation/navigation_runtime.hpp"

#include <algorithm>
#include <cmath>
#include <memory>
#include <stdexcept>
#include <utility>
#include <vector>

namespace routing::core::navigation {

namespace {

constexpr double kEarthRadiusM =
    6371008.8;

constexpr double kDegreesToRadians =
    0.017453292519943295769236907684886;


[[nodiscard]]
bool finite_positive(
    const double value) {
  return
      std::isfinite(value) &&
      value > 0.0;
}


[[nodiscard]]
bool finite_nonnegative(
    const double value) {
  return
      std::isfinite(value) &&
      value >= 0.0;
}


void validate_point(
    const GeoPoint& point) {
  if (!std::isfinite(point.latitude) ||
      !std::isfinite(point.longitude)) {
    throw std::invalid_argument(
        "Navigation route geometry contains non-finite coordinate.");
  }

  if (point.latitude < -90.0 ||
      point.latitude > 90.0) {
    throw std::invalid_argument(
        "Navigation route latitude is outside [-90, 90].");
  }

  if (point.longitude < -180.0 ||
      point.longitude > 180.0) {
    throw std::invalid_argument(
        "Navigation route longitude is outside [-180, 180].");
  }
}


[[nodiscard]]
double geometry_distance_m(
    const GeoPoint& from,
    const GeoPoint& to) {
  const double lat1 =
      from.latitude *
      kDegreesToRadians;

  const double lat2 =
      to.latitude *
      kDegreesToRadians;

  const double delta_lat =
      (to.latitude -
       from.latitude) *
      kDegreesToRadians;

  const double delta_lon =
      (to.longitude -
       from.longitude) *
      kDegreesToRadians;

  const double sin_half_lat =
      std::sin(
          delta_lat * 0.5);

  const double sin_half_lon =
      std::sin(
          delta_lon * 0.5);

  double a =
      sin_half_lat *
          sin_half_lat +
      std::cos(lat1) *
          std::cos(lat2) *
          sin_half_lon *
          sin_half_lon;

  a =
      std::clamp(
          a,
          0.0,
          1.0);

  return
      2.0 *
      kEarthRadiusM *
      std::asin(
          std::sqrt(a));
}


[[nodiscard]]
std::vector<double>
make_cumulative_geometry(
    const std::vector<GeoPoint>& geometry) {
  std::vector<double> cumulative(
      geometry.size(),
      0.0);

  for (std::size_t i = 1;
       i < geometry.size();
       ++i) {
    cumulative[i] =
        cumulative[i - 1] +
        geometry_distance_m(
            geometry[i - 1],
            geometry[i]);
  }

  return cumulative;
}


void validate_route_path(
    const RoutePath& route) {
  if (route.route_id.empty()) {
    throw std::invalid_argument(
        "Navigation route requires route_id.");
  }

  if (!finite_positive(
          route.distance_m)) {
    throw std::invalid_argument(
        "Navigation route requires positive finite distance.");
  }

  if (!finite_positive(
          route.duration_s)) {
    throw std::invalid_argument(
        "Navigation route requires positive finite duration.");
  }

  if (route.geometry.size() < 2) {
    throw std::invalid_argument(
        "Navigation route requires at least two geometry points.");
  }


  for (const auto& point :
       route.geometry) {
    validate_point(
        point);
  }


  const auto cumulative =
      make_cumulative_geometry(
          route.geometry);

  if (cumulative.empty() ||
      !finite_positive(
          cumulative.back())) {
    throw std::invalid_argument(
        "Navigation route geometry must have positive length.");
  }


  bool first_maneuver =
      true;

  std::size_t previous_begin =
      0;

  std::size_t previous_end =
      0;


  for (const auto& maneuver :
       route.maneuvers) {
    if (!finite_nonnegative(
            maneuver.distance_m) ||
        !finite_nonnegative(
            maneuver.duration_s)) {
      throw std::invalid_argument(
          "Navigation maneuver contains invalid distance or duration.");
    }

    if (maneuver.begin_shape_index >
        maneuver.end_shape_index) {
      throw std::invalid_argument(
          "Navigation maneuver begin index exceeds end index.");
    }

    if (maneuver.end_shape_index >=
        route.geometry.size()) {
      throw std::invalid_argument(
          "Navigation maneuver shape index exceeds route geometry.");
    }

    if (!first_maneuver &&
        (maneuver.begin_shape_index <
             previous_begin ||
         maneuver.end_shape_index <
             previous_end)) {
      throw std::invalid_argument(
          "Navigation maneuvers are not ordered by route geometry.");
    }

    if (maneuver.bearing_before_deg.has_value() &&
        *maneuver.bearing_before_deg > 359U) {
      throw std::invalid_argument(
          "Navigation maneuver bearing_before exceeds 359 degrees.");
    }

    if (maneuver.bearing_after_deg.has_value() &&
        *maneuver.bearing_after_deg > 359U) {
      throw std::invalid_argument(
          "Navigation maneuver bearing_after exceeds 359 degrees.");
    }

    previous_begin =
        maneuver.begin_shape_index;

    previous_end =
        maneuver.end_shape_index;

    first_maneuver =
        false;
  }
}


}  // namespace


RoutePreview make_route_preview(
    const RoutePath& route) {
  validate_route_path(
      route);

  RoutePreview preview;

  preview.route_id =
      route.route_id;

  preview.family =
      route.family;

  preview.distance_m =
      route.distance_m;

  preview.duration_s =
      route.duration_s;

  preview.geometry =
      route.geometry;

  preview.maneuvers =
      route.maneuvers;

  preview.engine_name =
      route.engine_name;

  preview.engine_version =
      route.engine_version;

  preview.segment_data_status =
      route.segment_data_status;

  preview.diagnostics =
      route.diagnostics;

  return preview;
}


NavigationSession::NavigationSession(
    std::string session_id,
    const RoutePath& route)
    : session_id_(
          std::move(session_id)) {
  if (session_id_.empty()) {
    throw std::invalid_argument(
        "Navigation session requires session_id.");
  }


  auto preview =
      std::make_shared<RoutePreview>(
          make_route_preview(
              route));

  cumulative_geometry_m_ =
      make_cumulative_geometry(
          preview->geometry);

  geometry_length_m_ =
      cumulative_geometry_m_.back();

  preview_ =
      std::move(preview);
}


std::optional<std::size_t>
NavigationSession::current_maneuver_index(
    const double shape_position) const {
  if (preview_->maneuvers.empty()) {
    return std::nullopt;
  }


  if (state_ ==
      NavigationSessionState::Arrived) {
    return
        preview_->maneuvers.size() -
        1;
  }


  for (std::size_t i = 0;
       i < preview_->maneuvers.size();
       ++i) {
    const double end =
        static_cast<double>(
            preview_->
                maneuvers[i].
                end_shape_index);

    if (shape_position <=
        end) {
      return i;
    }
  }


  return
      preview_->maneuvers.size() -
      1;
}


NavigationSnapshot
NavigationSession::make_snapshot() const {
  NavigationSnapshot result;

  result.session_id =
      session_id_;

  result.state =
      state_;

  result.route_preview =
      preview_;

  result.shape_segment_index =
      current_segment_index_;

  result.segment_fraction =
      current_segment_fraction_;


  const std::size_t next_point_index =
      current_segment_index_ +
      1;

  const double segment_geometry_m =
      cumulative_geometry_m_[
          next_point_index] -
      cumulative_geometry_m_[
          current_segment_index_];

  double geometry_traveled_m =
      cumulative_geometry_m_[
          current_segment_index_] +
      segment_geometry_m *
          current_segment_fraction_;


  if (state_ ==
      NavigationSessionState::Arrived) {
    geometry_traveled_m =
        geometry_length_m_;
  }


  result.progress_fraction =
      std::clamp(
          geometry_traveled_m /
              geometry_length_m_,
          0.0,
          1.0);


  result.traveled_distance_m =
      preview_->distance_m *
      result.progress_fraction;

  result.remaining_distance_m =
      std::max(
          0.0,
          preview_->distance_m -
              result.traveled_distance_m);

  result.remaining_duration_s =
      std::max(
          0.0,
          preview_->duration_s *
              (1.0 -
               result.progress_fraction));


  const double shape_position =
      static_cast<double>(
          current_segment_index_) +
      current_segment_fraction_;


  result.current_maneuver_index =
      current_maneuver_index(
          shape_position);


  if (result.current_maneuver_index.has_value()) {
    const std::size_t index =
        *result.current_maneuver_index;

    result.current_maneuver =
        preview_->
            maneuvers[index];


    if (index + 1 <
        preview_->maneuvers.size()) {
      result.next_maneuver_index =
          index + 1;

      result.next_maneuver =
          preview_->
              maneuvers[index + 1];
    }


    const std::size_t maneuver_end_index =
        preview_->
            maneuvers[index].
            end_shape_index;

    const double geometry_to_maneuver_end_m =
        std::max(
            0.0,
            cumulative_geometry_m_[
                maneuver_end_index] -
                geometry_traveled_m);

    const double route_scale =
        preview_->distance_m /
        geometry_length_m_;

    result.distance_to_current_maneuver_end_m =
        geometry_to_maneuver_end_m *
        route_scale;
  }


  result.arrived =
      state_ ==
      NavigationSessionState::Arrived;

  return result;
}


NavigationSnapshot
NavigationSession::snapshot() const {
  return make_snapshot();
}


NavigationSnapshot
NavigationSession::start() {
  if (state_ ==
      NavigationSessionState::Arrived) {
    throw std::logic_error(
        "Arrived navigation session cannot be started again.");
  }


  if (state_ ==
      NavigationSessionState::Preview) {
    state_ =
        NavigationSessionState::Navigating;
  }


  return make_snapshot();
}


NavigationSnapshot
NavigationSession::update_progress(
    const NavigationProgressUpdate& update) {
  if (state_ !=
      NavigationSessionState::Navigating) {
    throw std::logic_error(
        "Navigation progress requires Navigating state.");
  }


  if (!std::isfinite(
          update.segment_fraction) ||
      update.segment_fraction < 0.0 ||
      update.segment_fraction > 1.0) {
    throw std::invalid_argument(
        "Navigation segment_fraction must be finite and in [0, 1].");
  }


  const std::size_t segment_count =
      preview_->geometry.size() -
      1;


  if (update.shape_segment_index >=
      segment_count) {
    throw std::invalid_argument(
        "Navigation shape_segment_index exceeds route segments.");
  }


  if (update.shape_segment_index <
      current_segment_index_) {
    throw std::logic_error(
        "Navigation progress may not move backwards.");
  }


  if (update.shape_segment_index ==
          current_segment_index_ &&
      update.segment_fraction <
          current_segment_fraction_) {
    throw std::logic_error(
        "Navigation segment progress may not move backwards.");
  }


  current_segment_index_ =
      update.shape_segment_index;

  current_segment_fraction_ =
      update.segment_fraction;


  const std::size_t final_segment_index =
      segment_count -
      1;


  if (current_segment_index_ ==
          final_segment_index &&
      current_segment_fraction_ >=
          1.0) {
    current_segment_fraction_ =
        1.0;

    state_ =
        NavigationSessionState::Arrived;
  }


  return make_snapshot();
}

}  // namespace routing::core::navigation
