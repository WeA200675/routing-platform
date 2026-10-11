#include <cassert>

#include "routing/core/drive/regression_candidate.hpp"

int main() {
  using namespace routing::core::drive;

  DriveSessionHeader header;
  header.session_id = "regression-drive";
  header.started_at_ms = 100;

  header.versions.routing_engine =
      "valhalla";

  header.versions.routing_engine_version =
      "3.8.3";

  header.versions.map_data_version =
      "liechtenstein-fixture-v1";

  header.versions.profile_id =
      "main-roads";

  header.versions.profile_version =
      "7";

  RouteRequestSnapshot route_request;
  route_request.origin = {47.1410, 9.5209};
  route_request.destination = {47.1660, 9.5100};
  route_request.candidate_family = "profile_optimal";
  route_request.costing_profile = "auto";

  RouteSnapshot selected;
  selected.candidate_family = "profile_optimal";
  selected.route_id = "route-current";
  selected.distance_m = 3000.0;
  selected.duration_s = 240.0;

  DriveSessionRecorder recorder(
      header,
      route_request,
      selected);

  DriveEvent deviation;
  deviation.timestamp_ms = 200;

  deviation.type =
      DriveEventType::RouteDeviationDetected;

  deviation.route_id = "route-current";
  deviation.segment_id = "segment-bad";

  deviation.deviation_distance_m = 42.0;
  deviation.detector_confidence = 0.91;

  (void)recorder.record(deviation);

  DriveEvent feedback;
  feedback.timestamp_ms = 300;

  feedback.type =
      DriveEventType::FeedbackMarked;

  feedback.route_id = "route-current";
  feedback.segment_id = "segment-bad";

  FeedbackMark mark;
  mark.sentiment =
      FeedbackSentiment::Negative;

  mark.reason =
      FeedbackReason::CurvyRoad;

  mark.severity = 5;

  feedback.feedback = mark;

  (void)recorder.record(feedback);

  DriveEvent choice;
  choice.timestamp_ms = 400;

  choice.type =
      DriveEventType::AlternativeSelected;

  choice.route_id = "route-current";

  choice.alternative_route_id =
      "route-preferred";

  (void)recorder.record(choice);

  recorder.finish(500);

  const auto evidence =
      build_drive_evidence(
          recorder.session());

  const auto candidates =
      derive_regression_candidates(
          recorder.session(),
          evidence);

  assert(candidates.size() == 3);

  for (const auto& candidate : candidates) {
    // Nichts wird still automatisch zur Routingregel.
    assert(candidate.requires_human_review);

    assert(
        candidate.session_id ==
        "regression-drive");

    assert(
        candidate.versions.routing_engine ==
        "valhalla");

    assert(
        candidate.request.candidate_family ==
        "profile_optimal");

    assert(
        candidate.request.costing_profile.has_value());

    assert(
        *candidate.request.costing_profile ==
        "auto");

    assert(
        candidate.selected_route.route_id ==
        "route-current");

    assert(
        candidate.evidence_ids.size() == 1);
  }

  assert(
      candidates[0].kind ==
      RegressionCandidateKind::DeviationCase);

  assert(
      candidates[1].kind ==
      RegressionCandidateKind::SegmentComplaint);

  assert(
      candidates[1].affected_segment_id ==
      "segment-bad");

  assert(candidates[1].priority == 100);

  assert(
      candidates[2].kind ==
      RegressionCandidateKind::RoutePreference);

  assert(
      candidates[2].preferred_route_id ==
      "route-preferred");

  return 0;
}
