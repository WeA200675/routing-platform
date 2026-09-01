#include "routing/adapters/valhalla/valhalla_routing_engine.hpp"

#include <iomanip>
#include <memory>
#include <mutex>
#include <sstream>
#include <stdexcept>
#include <string>
#include <utility>

#ifdef ROUTING_PLATFORM_WITH_VALHALLA
#include <boost/property_tree/json_parser.hpp>
#include <boost/property_tree/ptree.hpp>
#include <valhalla/tyr/actor.h>
#endif

namespace routing::adapters::valhalla {

namespace {

#ifdef ROUTING_PLATFORM_WITH_VALHALLA

void validate_point(const routing::core::GeoPoint& point) {
  if (point.latitude < -90.0 || point.latitude > 90.0) {
    throw std::invalid_argument("Latitude must be between -90 and 90 degrees.");
  }

  if (point.longitude < -180.0 || point.longitude > 180.0) {
    throw std::invalid_argument("Longitude must be between -180 and 180 degrees.");
  }
}

std::string costing_name(const routing::core::RouteRequest& request) {
  const std::string costing = request.costing_profile.value_or("auto");

  if (costing.empty() ||
      costing.find_first_not_of(
          "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_-") !=
          std::string::npos) {
    throw std::invalid_argument("Invalid Valhalla costing profile.");
  }

  return costing;
}

void append_location(std::ostringstream& json,
                     const routing::core::GeoPoint& point) {
  validate_point(point);

  json << "{\"lat\":" << std::setprecision(15) << point.latitude
       << ",\"lon\":" << std::setprecision(15) << point.longitude << "}";
}

std::string build_route_request(const routing::core::RouteRequest& request) {
  std::ostringstream json;

  json << "{\"locations\":[";

  append_location(json, request.origin);

  for (const auto& via : request.via_points) {
    json << ",";
    append_location(json, via);
  }

  json << ",";
  append_location(json, request.destination);

  json << "],\"costing\":\"" << costing_name(request)
       << "\",\"units\":\"kilometers\"}";

  return json.str();
}

routing::core::ManeuverType map_maneuver_type(
    const std::int32_t type) {
  using routing::core::ManeuverType;

  switch (type) {
    case 1:
    case 2:
    case 3:
      return ManeuverType::Start;

    case 4:
    case 5:
    case 6:
      return ManeuverType::Arrive;

    case 7:
    case 8:
    case 17:
    case 22:
      return ManeuverType::Continue;

    case 9:
    case 10:
    case 11:
    case 18:
    case 23:
      return ManeuverType::TurnRight;

    case 12:
    case 13:
      return ManeuverType::UTurn;

    case 14:
    case 15:
    case 16:
    case 19:
    case 24:
      return ManeuverType::TurnLeft;

    case 20:
    case 21:
      return ManeuverType::Exit;

    case 25:
      return ManeuverType::Merge;

    case 26:
      return ManeuverType::RoundaboutEnter;

    case 27:
      return ManeuverType::RoundaboutExit;

    default:
      return ManeuverType::Unknown;
  }
}

std::vector<routing::core::GeoPoint> decode_polyline6(
    const std::string& encoded) {
  constexpr double kScale = 1'000'000.0;

  std::vector<routing::core::GeoPoint> points;

  std::size_t index = 0;
  std::int64_t latitude = 0;
  std::int64_t longitude = 0;

  auto decode_value =
      [&encoded, &index](std::int64_t previous) -> std::int64_t {
    std::uint64_t result = 0;
    unsigned int shift = 0;

    while (true) {
      if (index >= encoded.size()) {
        throw std::runtime_error(
            "Invalid truncated Valhalla polyline.");
      }

      const auto byte =
          static_cast<unsigned int>(
              static_cast<unsigned char>(encoded[index++])) -
          63U;

      result |=
          static_cast<std::uint64_t>(byte & 0x1FU) << shift;

      if (byte < 0x20U) {
        break;
      }

      shift += 5U;

      if (shift >= 64U) {
        throw std::runtime_error(
            "Invalid Valhalla polyline coordinate.");
      }
    }

    const std::int64_t delta =
        (result & 1U) != 0U
            ? ~static_cast<std::int64_t>(result >> 1U)
            : static_cast<std::int64_t>(result >> 1U);

    return previous + delta;
  };

  while (index < encoded.size()) {
    latitude = decode_value(latitude);
    longitude = decode_value(longitude);

    routing::core::GeoPoint point;
    point.latitude =
        static_cast<double>(latitude) / kScale;
    point.longitude =
        static_cast<double>(longitude) / kScale;

    points.push_back(point);
  }

  return points;
}

routing::core::RoutingResult parse_route_response(
    const std::string& response_json,
    const routing::core::RouteRequest& request) {
  std::istringstream stream(response_json);

  boost::property_tree::ptree root;
  boost::property_tree::read_json(stream, root);

  routing::core::RoutePath path;
  path.route_id = "valhalla-0";
  path.family = request.family;

  path.distance_m =
      root.get<double>("trip.summary.length") * 1000.0;

  path.duration_s =
      root.get<double>("trip.summary.time");

  path.engine_name = "valhalla";
  path.engine_version = "3.8.3";

  const auto& legs =
      root.get_child("trip.legs");

  for (const auto& leg_entry : legs) {
    const auto& leg = leg_entry.second;

    const std::string encoded_shape =
        leg.get<std::string>("shape");

    auto leg_geometry =
        decode_polyline6(encoded_shape);

    if (!path.geometry.empty() &&
        !leg_geometry.empty()) {
      leg_geometry.erase(leg_geometry.begin());
    }

    path.geometry.insert(
        path.geometry.end(),
        leg_geometry.begin(),
        leg_geometry.end());

    if (const auto maneuvers =
            leg.get_child_optional("maneuvers")) {
      for (const auto& maneuver_entry : *maneuvers) {
        const auto& source =
            maneuver_entry.second;

        routing::core::RouteManeuver maneuver;

        const auto engine_type =
            source.get<std::int32_t>("type", 0);

        maneuver.engine_type = engine_type;
        maneuver.type =
            map_maneuver_type(engine_type);

        maneuver.instruction =
            source.get<std::string>(
                "instruction",
                "");

        maneuver.distance_m =
            source.get<double>(
                "length",
                0.0) *
            1000.0;

        maneuver.duration_s =
            source.get<double>(
                "time",
                0.0);

        maneuver.begin_shape_index =
            source.get<std::size_t>(
                "begin_shape_index",
                0);

        maneuver.end_shape_index =
            source.get<std::size_t>(
                "end_shape_index",
                maneuver.begin_shape_index);

        if (const auto bearing_before =
                source.get_optional<std::uint16_t>(
                    "bearing_before")) {
          maneuver.bearing_before_deg =
              *bearing_before;
        }

        if (const auto bearing_after =
                source.get_optional<std::uint16_t>(
                    "bearing_after")) {
          maneuver.bearing_after_deg =
              *bearing_after;
        }

        if (const auto street_names =
                source.get_child_optional(
                    "street_names")) {
          for (const auto& street_entry :
               *street_names) {
            maneuver.street_names.push_back(
                street_entry.second
                    .get_value<std::string>());
          }
        }

        path.maneuvers.push_back(
            std::move(maneuver));
      }
    }
  }

  routing::core::RoutingResult result;
  result.success = true;
  result.routes.push_back(std::move(path));

  return result;
}

#endif

}  // namespace

class ValhallaRoutingEngine::Impl {
 public:
  explicit Impl(ValhallaConfig config)
      : config_(std::move(config)) {
#ifdef ROUTING_PLATFORM_WITH_VALHALLA
    try {
      std::istringstream config_stream(config_.config_json);
      boost::property_tree::read_json(config_stream, config_tree_);

      // auto_cleanup=true:
      // Valhalla räumt die Worker nach jeder Anfrage auf.
      actor_ = std::make_unique<::valhalla::tyr::actor_t>(
          config_tree_, true);

      ready_ = true;
    } catch (const std::exception& error) {
      ready_ = false;
      init_error_ = error.what();
    }
#endif
  }

  [[nodiscard]] bool ready() const {
    return ready_;
  }

  [[nodiscard]] routing::core::RoutingResult route(
      const routing::core::RouteRequest& request) const {
#ifndef ROUTING_PLATFORM_WITH_VALHALLA

    routing::core::RoutingResult result;
    result.success = false;
    result.error_code = "VALHALLA_NOT_LINKED";
    result.error_message =
        "routing-platform was built without libvalhalla.";
    return result;

#else

    if (!ready_ || !actor_) {
      routing::core::RoutingResult result;
      result.success = false;
      result.error_code = "VALHALLA_INIT_FAILED";
      result.error_message =
          init_error_.empty()
              ? "Valhalla adapter is not ready."
              : init_error_;
      return result;
    }

    try {
      const std::string request_json =
          build_route_request(request);

      // actor_t wird bewusst serialisiert benutzt.
      // Ein gemeinsamer Actor ist nicht als paralleler Request-Worker gedacht.
      std::lock_guard<std::mutex> lock(actor_mutex_);

      const std::string response =
          actor_->route(request_json);

      return parse_route_response(response, request);

    } catch (const std::exception& error) {
      routing::core::RoutingResult result;
      result.success = false;
      result.error_code = "VALHALLA_ROUTE_FAILED";
      result.error_message = error.what();
      return result;

    } catch (...) {
      routing::core::RoutingResult result;
      result.success = false;
      result.error_code = "VALHALLA_ROUTE_FAILED";
      result.error_message =
          "Unknown exception while routing with Valhalla.";
      return result;
    }

#endif
  }

 private:
  ValhallaConfig config_;

  bool ready_ = false;
  std::string init_error_;

#ifdef ROUTING_PLATFORM_WITH_VALHALLA
  boost::property_tree::ptree config_tree_;
  mutable std::unique_ptr<::valhalla::tyr::actor_t> actor_;
  mutable std::mutex actor_mutex_;
#endif
};

ValhallaRoutingEngine::ValhallaRoutingEngine(
    ValhallaConfig config)
    : impl_(std::make_unique<Impl>(std::move(config))) {}

ValhallaRoutingEngine::~ValhallaRoutingEngine() = default;

ValhallaRoutingEngine::ValhallaRoutingEngine(
    ValhallaRoutingEngine&&) noexcept = default;

ValhallaRoutingEngine&
ValhallaRoutingEngine::operator=(
    ValhallaRoutingEngine&&) noexcept = default;

std::string ValhallaRoutingEngine::name() const {
  return "valhalla";
}

std::string ValhallaRoutingEngine::version() const {
#ifdef ROUTING_PLATFORM_WITH_VALHALLA
  return "3.8.3";
#else
  return "not-linked";
#endif
}

bool ValhallaRoutingEngine::ready() const {
  return impl_->ready();
}

routing::core::RoutingResult
ValhallaRoutingEngine::route(
    const routing::core::RouteRequest& request) const {
  return impl_->route(request);
}

}  // namespace routing::adapters::valhalla