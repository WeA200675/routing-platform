#include <cassert>
#include <stdexcept>

#include "routing/core/drive/drive_session.hpp"

int main() {
  using namespace routing::core::drive;

  DriveSessionHeader header;
  header.session_id = "validation";
  header.started_at_ms = 1000;

  RouteRequestSnapshot route_request;
  route_request.origin = {47.1410, 9.5209};
  route_request.destination = {47.1660, 9.5100};
  route_request.candidate_family = "profile_optimal";
  route_request.costing_profile = "auto";

  RouteSnapshot selected;
  selected.candidate_family = "profile_optimal";
  selected.route_id = "same-route";
  selected.distance_m = 1000.0;
  selected.duration_s = 100.0;

  RouteSnapshot duplicate =
      selected;

  bool duplicate_route_threw = false;

  try {
    DriveSessionRecorder invalid(
        header,
        route_request,
        selected,
        {duplicate});

    (void)invalid;
  } catch (const std::invalid_argument&) {
    duplicate_route_threw = true;
  }

  assert(duplicate_route_threw);

  // Ein nicht initialisierter Request darf nicht still als
  // Route von (0,0) nach (0,0) akzeptiert werden.
  RouteRequestSnapshot unset_request;
  unset_request.candidate_family =
      "profile_optimal";

  bool unset_request_threw = false;

  try {
    DriveSessionRecorder invalid(
        header,
        unset_request,
        selected);

    (void)invalid;
  } catch (const std::invalid_argument&) {
    unset_request_threw = true;
  }

  assert(unset_request_threw);

  DriveSessionRecorder recorder(
      header,
      route_request,
      selected);

  DriveEvent first;
  first.timestamp_ms = 1500;

  first.type =
      DriveEventType::LocationObservation;

  (void)recorder.record(first);

  DriveEvent backwards;
  backwards.timestamp_ms = 1400;

  backwards.type =
      DriveEventType::LocationObservation;

  bool backwards_threw = false;

  try {
    (void)recorder.record(backwards);
  } catch (const std::invalid_argument&) {
    backwards_threw = true;
  }

  assert(backwards_threw);

  DriveEvent invalid_feedback;
  invalid_feedback.timestamp_ms = 1600;

  invalid_feedback.type =
      DriveEventType::FeedbackMarked;

  FeedbackMark bad_mark;
  bad_mark.severity = 6;

  invalid_feedback.feedback = bad_mark;

  bool severity_threw = false;

  try {
    (void)recorder.record(invalid_feedback);
  } catch (const std::invalid_argument&) {
    severity_threw = true;
  }

  assert(severity_threw);

  DriveEvent invalid_deviation;
  invalid_deviation.timestamp_ms = 1700;

  invalid_deviation.type =
      DriveEventType::RouteDeviationDetected;

  invalid_deviation.deviation_distance_m =
      20.0;

  // Confidence absichtlich nicht gesetzt.

  bool confidence_threw = false;

  try {
    (void)recorder.record(invalid_deviation);
  } catch (const std::invalid_argument&) {
    confidence_threw = true;
  }

  assert(confidence_threw);

  DriveSessionHeader excluded_header;
  excluded_header.session_id = "excluded";
  excluded_header.started_at_ms = 1;

  excluded_header.learning_disposition =
      LearningDisposition::Excluded;

  bool missing_reason_threw = false;

  try {
    DriveSessionRecorder invalid(
        excluded_header,
        route_request,
        selected);

    (void)invalid;
  } catch (const std::invalid_argument&) {
    missing_reason_threw = true;
  }

  assert(missing_reason_threw);

  return 0;
}
