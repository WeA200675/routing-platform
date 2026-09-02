#include "routing/core/drive/drive_session.hpp"

#include <cmath>
#include <stdexcept>
#include <unordered_set>
#include <utility>

namespace routing::core::drive {

namespace {

void validate_coordinate(
    const GeoPointSnapshot& point) {
  if (!std::isfinite(point.latitude) ||
      !std::isfinite(point.longitude) ||
      point.latitude < -90.0 ||
      point.latitude > 90.0 ||
      point.longitude < -180.0 ||
      point.longitude > 180.0) {
    throw std::invalid_argument(
        "Drive route request contains invalid coordinates.");
  }
}

void validate_request(
    const RouteRequestSnapshot& request) {
  validate_coordinate(request.origin);
  validate_coordinate(request.destination);

  for (const auto& via : request.via_points) {
    validate_coordinate(via);
  }

  if (request.candidate_family.empty()) {
    throw std::invalid_argument(
        "Drive route request requires candidate family.");
  }

  if (request.costing_profile.has_value() &&
      request.costing_profile->empty()) {
    throw std::invalid_argument(
        "Drive costing profile must not be empty.");
  }
}

void validate_route(
    const RouteSnapshot& route) {
  if (route.route_id.empty()) {
    throw std::invalid_argument(
        "Route snapshot requires a route id.");
  }

  if (route.candidate_family.empty()) {
    throw std::invalid_argument(
        "Route snapshot requires candidate family.");
  }

  if (!std::isfinite(route.distance_m) ||
      route.distance_m < 0.0) {
    throw std::invalid_argument(
        "Route distance must be finite and non-negative.");
  }

  if (!std::isfinite(route.duration_s) ||
      route.duration_s < 0.0) {
    throw std::invalid_argument(
        "Route duration must be finite and non-negative.");
  }
}

void validate_header(
    const DriveSessionHeader& header) {
  if (header.schema_version == 0) {
    throw std::invalid_argument(
        "Drive session schema version must not be zero.");
  }

  if (header.session_id.empty()) {
    throw std::invalid_argument(
        "Drive session requires a session id.");
  }

  if (header.started_at_ms < 0) {
    throw std::invalid_argument(
        "Drive session start timestamp must not be negative.");
  }

  if (header.learning_disposition ==
          LearningDisposition::Excluded &&
      header.learning_exclusion_reason.empty()) {
    throw std::invalid_argument(
        "Excluded drive session requires a reason.");
  }
}

void validate_feedback(
    const FeedbackMark& feedback) {
  if (feedback.severity < 1 ||
      feedback.severity > 5) {
    throw std::invalid_argument(
        "Feedback severity must be between 1 and 5.");
  }
}

void validate_event_payload(
    const DriveEvent& event) {
  if (event.detector_confidence.has_value()) {
    const double confidence =
        *event.detector_confidence;

    if (!std::isfinite(confidence) ||
        confidence < 0.0 ||
        confidence > 1.0) {
      throw std::invalid_argument(
          "Detector confidence must be between 0 and 1.");
    }
  }

  if (event.deviation_distance_m.has_value()) {
    const double distance =
        *event.deviation_distance_m;

    if (!std::isfinite(distance) ||
        distance < 0.0) {
      throw std::invalid_argument(
          "Deviation distance must be finite and non-negative.");
    }
  }

  if (event.type ==
      DriveEventType::FeedbackMarked) {
    if (!event.feedback.has_value()) {
      throw std::invalid_argument(
          "Feedback event requires feedback payload.");
    }

    validate_feedback(*event.feedback);
  } else if (event.feedback.has_value()) {
    throw std::invalid_argument(
        "Feedback payload is only valid for feedback events.");
  }

  if (event.type ==
      DriveEventType::RouteDeviationDetected) {
    if (!event.deviation_distance_m.has_value() ||
        !event.detector_confidence.has_value()) {
      throw std::invalid_argument(
          "Route deviation requires distance and detector confidence.");
    }
  }

  if (event.type ==
          DriveEventType::AlternativeSelected &&
      event.alternative_route_id.empty()) {
    throw std::invalid_argument(
        "Alternative selection requires alternative route id.");
  }
}

}  // namespace

DriveSessionRecorder::DriveSessionRecorder(
    DriveSessionHeader header,
    RouteRequestSnapshot request,
    RouteSnapshot selected_route,
    std::vector<RouteSnapshot> alternatives) {
  validate_header(header);
  validate_request(request);
  validate_route(selected_route);

  std::unordered_set<std::string> route_ids;
  route_ids.insert(selected_route.route_id);

  for (const auto& alternative : alternatives) {
    validate_route(alternative);

    if (!route_ids.insert(
            alternative.route_id).second) {
      throw std::invalid_argument(
          "Drive session route ids must be unique.");
    }
  }

  session_.header = std::move(header);
  session_.request = std::move(request);

  session_.selected_route =
      std::move(selected_route);

  session_.alternatives =
      std::move(alternatives);
}

std::string DriveSessionRecorder::record(
    DriveEvent event) {
  if (session_.completed) {
    throw std::logic_error(
        "Cannot record event after drive session completion.");
  }

  if (event.timestamp_ms <
      session_.header.started_at_ms) {
    throw std::invalid_argument(
        "Drive event precedes session start.");
  }

  if (!session_.events.empty() &&
      event.timestamp_ms <
          session_.events.back().timestamp_ms) {
    throw std::invalid_argument(
        "Drive events must be recorded in timestamp order.");
  }

  validate_event_payload(event);

  event.sequence = next_sequence_++;

  event.id =
      session_.header.session_id +
      ":event:" +
      std::to_string(event.sequence);

  session_.events.push_back(
      std::move(event));

  return session_.events.back().id;
}

void DriveSessionRecorder::finish(
    const std::int64_t ended_at_ms) {
  if (session_.completed) {
    throw std::logic_error(
        "Drive session is already completed.");
  }

  std::int64_t minimum_timestamp =
      session_.header.started_at_ms;

  if (!session_.events.empty()) {
    minimum_timestamp =
        session_.events.back().timestamp_ms;
  }

  if (ended_at_ms < minimum_timestamp) {
    throw std::invalid_argument(
        "Drive session end precedes recorded data.");
  }

  session_.ended_at_ms = ended_at_ms;
  session_.completed = true;
}

const DriveSession&
DriveSessionRecorder::session() const {
  return session_;
}

}  // namespace routing::core::drive
