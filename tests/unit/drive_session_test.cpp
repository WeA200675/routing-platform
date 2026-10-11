#include <cassert>
#include <stdexcept>

#include "routing/core/drive/drive_session.hpp"

int main() {
  using namespace routing::core::drive;

  DriveSessionHeader header;
  header.session_id = "session-001";
  header.started_at_ms = 1000;

  header.versions.routing_engine = "valhalla";
  header.versions.routing_engine_version = "3.8.3";
  header.versions.map_data_version = "fixture-liechtenstein";

  header.versions.profile_id = "comfort";
  header.versions.profile_version = "1";

  RouteRequestSnapshot route_request;
  route_request.origin = {47.1410, 9.5209};
  route_request.destination = {47.1660, 9.5100};
  route_request.candidate_family = "profile_optimal";
  route_request.costing_profile = "auto";

  RouteSnapshot selected;
  selected.candidate_family = "profile_optimal";
  selected.route_id = "route-a";
  selected.distance_m = 3174.0;
  selected.duration_s = 242.149;

  selected.segment_ids.push_back(
      "valhalla:100");

  RouteSnapshot alternative;
  alternative.candidate_family = "profile_optimal";
  alternative.route_id = "route-b";
  alternative.distance_m = 3300.0;
  alternative.duration_s = 260.0;

  DriveSessionRecorder recorder(
      header,
      route_request,
      selected,
      {alternative});

  DriveEvent deviation;
  deviation.timestamp_ms = 2000;

  deviation.type =
      DriveEventType::RouteDeviationDetected;

  deviation.route_id = "route-a";
  deviation.segment_id = "valhalla:100";

  deviation.deviation_distance_m = 35.0;
  deviation.detector_confidence = 0.95;

  const auto deviation_id =
      recorder.record(deviation);

  assert(
      deviation_id ==
      "session-001:event:1");

  DriveEvent feedback;
  feedback.timestamp_ms = 2500;

  feedback.type =
      DriveEventType::FeedbackMarked;

  feedback.route_id = "route-a";
  feedback.segment_id = "valhalla:100";

  FeedbackMark mark;
  mark.sentiment =
      FeedbackSentiment::Negative;

  mark.reason =
      FeedbackReason::ResidentialShortcut;

  mark.severity = 5;
  mark.note = "unnecessary shortcut";

  feedback.feedback = mark;

  const auto feedback_id =
      recorder.record(feedback);

  assert(
      feedback_id ==
      "session-001:event:2");

  recorder.finish(3000);

  const auto& session =
      recorder.session();

  assert(session.completed);
  assert(session.ended_at_ms.has_value());
  assert(*session.ended_at_ms == 3000);

  assert(session.events.size() == 2);
  assert(session.events[0].sequence == 1);
  assert(session.events[1].sequence == 2);

  bool after_finish_threw = false;

  try {
    (void)recorder.record(feedback);
  } catch (const std::logic_error&) {
    after_finish_threw = true;
  }

  assert(after_finish_threw);

  return 0;
}
