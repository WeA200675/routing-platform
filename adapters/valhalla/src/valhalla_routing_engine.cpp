#include "routing/adapters/valhalla/valhalla_routing_engine.hpp"

#include "routing/adapters/valhalla/detail/valhalla_edge_attributes.hpp"
#include "routing/adapters/valhalla/detail/valhalla_parsing.hpp"
#include "routing/adapters/valhalla/detail/valhalla_route_request.hpp"
#include "routing/adapters/valhalla/detail/valhalla_trace_request.hpp"
#include "routing/adapters/valhalla/valhalla_street_segment_mapper.hpp"

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

routing::core::RoutePath parse_trip(
    const boost::property_tree::ptree& trip,
    const routing::core::RouteRequest& request,
    const std::size_t route_index) {
  routing::core::RoutePath path;

  path.route_id =
      "valhalla-" +
      std::to_string(route_index);

  path.family = request.family;

  path.distance_m =
      trip.get<double>(
          "summary.length") *
      1000.0;

  path.duration_s =
      trip.get<double>(
          "summary.time");

  path.engine_name = "valhalla";
  path.engine_version = "3.8.3";

  const auto& legs =
      trip.get_child("legs");

  for (const auto& leg_entry :
       legs) {
    const auto& leg =
        leg_entry.second;

    const std::string encoded_shape =
        leg.get<std::string>("shape");

    auto leg_geometry =
        detail::decode_polyline6(
            encoded_shape);

    const std::size_t shape_index_offset =
        path.geometry.empty()
            ? 0
            : path.geometry.size() - 1;

    if (!path.geometry.empty() &&
        !leg_geometry.empty()) {
      leg_geometry.erase(
          leg_geometry.begin());
    }

    path.geometry.insert(
        path.geometry.end(),
        leg_geometry.begin(),
        leg_geometry.end());

    if (const auto maneuvers =
            leg.get_child_optional(
                "maneuvers")) {
      for (const auto& maneuver_entry :
           *maneuvers) {
        const auto& source =
            maneuver_entry.second;

        routing::core::RouteManeuver maneuver;

        const auto engine_type =
            source.get<std::int32_t>(
                "type",
                0);

        maneuver.engine_type =
            engine_type;

        maneuver.type =
            detail::map_maneuver_type(
                engine_type);

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

        const auto local_begin_shape_index =
            source.get<std::size_t>(
                "begin_shape_index",
                0);

        const auto local_end_shape_index =
            source.get<std::size_t>(
                "end_shape_index",
                local_begin_shape_index);

        maneuver.begin_shape_index =
            shape_index_offset +
            local_begin_shape_index;

        maneuver.end_shape_index =
            shape_index_offset +
            local_end_shape_index;

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

  return path;
}

routing::core::RoutingResult parse_route_response(
    const std::string& response_json,
    const routing::core::RouteRequest& request) {
  std::istringstream stream(
      response_json);

  boost::property_tree::ptree root;
  boost::property_tree::read_json(
      stream,
      root);

  routing::core::RoutingResult result;
  result.success = true;

  result.routes.push_back(
      parse_trip(
          root.get_child("trip"),
          request,
          0));

  if (const auto alternates =
          root.get_child_optional(
              "alternates")) {
    std::size_t route_index = 1;

    for (const auto& alternate_entry :
         *alternates) {
      const auto trip =
          alternate_entry.second
              .get_child_optional(
                  "trip");

      if (!trip) {
        throw std::runtime_error(
            "Valhalla alternate response is missing trip.");
      }

      result.routes.push_back(
          parse_trip(
              *trip,
              request,
              route_index));

      ++route_index;
    }
  }

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

    (void)request;

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
          detail::build_route_request(request);

      // actor_t wird bewusst serialisiert benutzt.
      // Ein gemeinsamer Actor ist nicht als paralleler Request-Worker gedacht.
      std::lock_guard<std::mutex> lock(actor_mutex_);

      const std::string response =
          actor_->route(request_json);

      auto result =
          parse_route_response(
              response,
              request);

      if (!result.success ||
          result.routes.empty()) {
        return result;
      }

      try {
        for (auto& path :
             result.routes) {
          const std::string trace_request_json =
              detail::build_trace_attributes_request(
                  path.geometry,
                  detail::costing_name(request));

          const std::string trace_response =
              actor_->trace_attributes(
                  trace_request_json);

          const auto edges =
              detail::parse_trace_edge_attributes_json(
                  trace_response);

          if (edges.empty()) {
            throw std::runtime_error(
                "Valhalla trace_attributes returned no edges "
                "for route " +
                path.route_id +
                ".");
          }

          path.segments.clear();
          path.segment_ids.clear();

          path.segments.reserve(
              edges.size());

          path.segment_ids.reserve(
              edges.size());

          for (const auto& edge :
               edges) {
            auto segment =
                map_valhalla_edge_to_street_segment(
                    edge);

            path.segment_ids.push_back(
                segment.id);

            path.segments.push_back(
                std::move(segment));
          }
        }

      } catch (const std::exception& error) {
        result.success = false;
        result.error_code =
            "VALHALLA_TRACE_ATTRIBUTES_FAILED";
        result.error_message =
            error.what();
        result.routes.clear();
      } catch (...) {
        result.success = false;
        result.error_code =
            "VALHALLA_TRACE_ATTRIBUTES_FAILED";
        result.error_message =
            "Unknown exception while reading "
            "Valhalla trace attributes.";
        result.routes.clear();
      }

      return result;

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