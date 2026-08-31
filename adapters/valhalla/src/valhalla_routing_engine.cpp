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

routing::core::RoutingResult parse_route_response(
    const std::string& response_json,
    const routing::core::RouteRequest& request) {
  std::istringstream stream(response_json);

  boost::property_tree::ptree root;
  boost::property_tree::read_json(stream, root);

  routing::core::RoutePath path;
  path.route_id = "valhalla-0";
  path.family = request.family;

  // Valhalla liefert bei units=kilometers die Länge in Kilometern.
  path.distance_m =
      root.get<double>("trip.summary.length") * 1000.0;

  path.duration_s =
      root.get<double>("trip.summary.time");

  path.engine_name = "valhalla";
  path.engine_version = "3.8.3";

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