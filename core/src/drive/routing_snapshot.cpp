#include "routing/core/drive/routing_snapshot.hpp"

#include <cmath>
#include <stdexcept>

namespace routing::core::drive {

namespace {

void validate_point(
    const GeoPoint& point) {
  if (!std::isfinite(point.latitude) ||
      !std::isfinite(point.longitude) ||
      point.latitude < -90.0 ||
      point.latitude > 90.0 ||
      point.longitude < -180.0 ||
      point.longitude > 180.0) {
    throw std::invalid_argument(
        "Routing snapshot contains invalid coordinates.");
  }
}

}  // namespace

std::string candidate_family_id(
    const CandidateFamily family) {
  switch (family) {
    case CandidateFamily::Fastest:
      return "fastest";
    case CandidateFamily::Shortest:
      return "shortest";
    case CandidateFamily::ProfileOptimal:
      return "profile_optimal";
    case CandidateFamily::MajorRoads:
      return "major_roads";
    case CandidateFamily::Comfort:
      return "comfort";
    case CandidateFamily::LowUrban:
      return "low_urban";
    case CandidateFamily::LowCurvature:
      return "low_curvature";
    case CandidateFamily::LowGradient:
      return "low_gradient";
    case CandidateFamily::LowTraffic:
      return "low_traffic";
    case CandidateFamily::Energy:
      return "energy";
    case CandidateFamily::Scenic:
      return "scenic";
    case CandidateFamily::Stable:
      return "stable";
  }

  throw std::logic_error(
      "Unknown routing candidate family.");
}

RouteRequestSnapshot make_route_request_snapshot(
    const RouteRequest& request) {
  validate_point(request.origin);
  validate_point(request.destination);

  RouteRequestSnapshot snapshot;

  snapshot.origin = {
      request.origin.latitude,
      request.origin.longitude,
  };

  snapshot.destination = {
      request.destination.latitude,
      request.destination.longitude,
  };

  for (const auto& via : request.via_points) {
    validate_point(via);

    snapshot.via_points.push_back({
        via.latitude,
        via.longitude,
    });
  }

  snapshot.candidate_family =
      candidate_family_id(request.family);

  snapshot.alternatives_requested =
      request.alternatives;

  if (request.costing_profile.has_value()) {
    if (request.costing_profile->empty()) {
      throw std::invalid_argument(
          "Routing request costing profile must not be empty.");
    }

    snapshot.costing_profile =
        request.costing_profile;
  }

  return snapshot;
}

RouteSnapshot make_route_snapshot(
    const RoutePath& route) {
  if (route.route_id.empty()) {
    throw std::invalid_argument(
        "RoutePath requires route_id before snapshotting.");
  }

  if (!std::isfinite(route.distance_m) ||
      route.distance_m < 0.0) {
    throw std::invalid_argument(
        "RoutePath distance must be finite and non-negative.");
  }

  if (!std::isfinite(route.duration_s) ||
      route.duration_s < 0.0) {
    throw std::invalid_argument(
        "RoutePath duration must be finite and non-negative.");
  }

  if (route.segment_ids.size() !=
      route.segments.size()) {
    throw std::invalid_argument(
        "RoutePath segment_ids and segments must have equal size.");
  }

  for (std::size_t i = 0;
       i < route.segment_ids.size();
       ++i) {
    if (route.segment_ids[i].empty()) {
      throw std::invalid_argument(
          "RoutePath contains empty segment id.");
    }

    if (route.segment_ids[i] !=
        route.segments[i].id) {
      throw std::invalid_argument(
          "RoutePath segment id order does not match segments.");
    }
  }

  RouteSnapshot snapshot;

  snapshot.route_id = route.route_id;

  snapshot.candidate_family =
      candidate_family_id(route.family);

  snapshot.distance_m = route.distance_m;
  snapshot.duration_s = route.duration_s;

  snapshot.segment_ids =
      route.segment_ids;

  return snapshot;
}

}  // namespace routing::core::drive
