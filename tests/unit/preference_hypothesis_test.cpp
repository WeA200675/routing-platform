#include <cassert>

#include "routing/core/drive/drive_evidence.hpp"
#include "routing/core/intelligence/preference_hypothesis.hpp"

int main() {
  using namespace routing::core;
  using namespace routing::core::drive;
  using namespace routing::core::intelligence;

  DriveSessionHeader header;

  header.session_id =
      "hypothesis-drive";

  header.started_at_ms = 1000;

  header.context_tags = {
      "vehicle:solo",
  };

  RouteRequestSnapshot request;
  request.origin = {47.1410, 9.5209};
  request.destination = {47.1660, 9.5100};

  request.candidate_family =
      "profile_optimal";

  request.costing_profile =
      "auto";

  RouteSnapshot route;

  route.route_id =
      "route-a";

  route.candidate_family =
      "profile_optimal";

  route.distance_m = 1000.0;
  route.duration_s = 100.0;

  DriveSessionRecorder recorder(
      header,
      request,
      route);

  // Implizite Abweichung: darf alleine keine
  // negative Praeferenz erzeugen.
  DriveEvent deviation;

  deviation.timestamp_ms = 1100;

  deviation.type =
      DriveEventType::RouteDeviationDetected;

  deviation.route_id =
      "route-a";

  deviation.segment_id =
      "segment-30";

  deviation.deviation_distance_m = 20.0;
  deviation.detector_confidence = 0.95;

  (void)recorder.record(
      deviation);

  // Explizite Aussage des Nutzers.
  DriveEvent feedback;

  feedback.timestamp_ms = 1200;

  feedback.type =
      DriveEventType::FeedbackMarked;

  feedback.route_id =
      "route-a";

  feedback.segment_id =
      "segment-30";

  FeedbackMark mark;

  mark.sentiment =
      FeedbackSentiment::Negative;

  mark.reason =
      FeedbackReason::Speed30Zone;

  mark.severity = 5;

  feedback.feedback = mark;

  (void)recorder.record(
      feedback);

  recorder.finish(1300);

  const auto evidence =
      build_drive_evidence(
          recorder.session());

  const auto hypotheses =
      build_preference_hypotheses(
          recorder.session(),
          evidence);

  assert(hypotheses.size() == 1);

  const auto& hypothesis =
      hypotheses.front();

  assert(
      hypothesis.id ==
      "hypothesis-drive:hypothesis:1");

  assert(
      hypothesis.target.attribute ==
      Attribute::SpeedLimitKmh);

  assert(
      hypothesis.target.condition_key ==
      "speed_limit_kmh<=30");

  assert(
      hypothesis.direction ==
      PreferenceDirection::Avoid);

  assert(hypothesis.strength == 1.0);
  assert(hypothesis.confidence == 1.0);

  assert(
      hypothesis.evidence_ids.size() ==
      1);

  assert(
      hypothesis.context_tags.size() ==
      1);

  assert(
      hypothesis.context_tags.front() ==
      "vehicle:solo");

  return 0;
}
