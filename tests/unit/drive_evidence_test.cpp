#include <cassert>

#include "routing/core/drive/drive_evidence.hpp"

int main() {
  using namespace routing::core::drive;

  DriveSessionHeader header;
  header.session_id = "tester-drive";
  header.started_at_ms = 1000;

  // Testfahrt darf Regressionsevidenz erzeugen,
  // aber kein persönliches Lernprofil verändern.
  header.purpose =
      DriveSessionPurpose::Tester;

  header.learning_disposition =
      LearningDisposition::RecordOnly;

  RouteRequestSnapshot route_request;
  route_request.origin = {47.1410, 9.5209};
  route_request.destination = {47.1660, 9.5100};
  route_request.candidate_family = "profile_optimal";
  route_request.costing_profile = "auto";

  RouteSnapshot selected;
  selected.candidate_family = "profile_optimal";
  selected.route_id = "route-a";
  selected.distance_m = 1000.0;
  selected.duration_s = 100.0;

  RouteSnapshot alternative;
  alternative.candidate_family = "profile_optimal";
  alternative.route_id = "route-b";
  alternative.distance_m = 1100.0;
  alternative.duration_s = 105.0;

  DriveSessionRecorder recorder(
      header,
      route_request,
      selected,
      {alternative});

  DriveEvent deviation;
  deviation.timestamp_ms = 1100;

  deviation.type =
      DriveEventType::RouteDeviationDetected;

  deviation.route_id = "route-a";
  deviation.segment_id = "segment-7";

  deviation.deviation_distance_m = 20.0;
  deviation.detector_confidence = 0.88;

  (void)recorder.record(deviation);

  DriveEvent alternative_selected;
  alternative_selected.timestamp_ms = 1200;

  alternative_selected.type =
      DriveEventType::AlternativeSelected;

  alternative_selected.route_id = "route-a";

  alternative_selected.alternative_route_id =
      "route-b";

  (void)recorder.record(alternative_selected);

  DriveEvent feedback;
  feedback.timestamp_ms = 1300;

  feedback.type =
      DriveEventType::FeedbackMarked;

  feedback.route_id = "route-a";
  feedback.segment_id = "segment-7";

  FeedbackMark mark;
  mark.sentiment =
      FeedbackSentiment::Negative;

  mark.reason =
      FeedbackReason::Speed30Zone;

  mark.severity = 4;

  feedback.feedback = mark;

  (void)recorder.record(feedback);

  recorder.finish(1400);

  const auto evidence =
      build_drive_evidence(
          recorder.session());

  assert(evidence.size() == 3);

  assert(
      evidence[0].kind ==
      EvidenceKind::RouteDeviation);

  // Ein Abweichen ist zunächst eine neutrale Tatsache.
  assert(
      evidence[0].polarity ==
      EvidencePolarity::Neutral);

  assert(
      evidence[1].kind ==
      EvidenceKind::AlternativeSelection);

  assert(
      evidence[1].polarity ==
      EvidencePolarity::Positive);

  assert(
      evidence[2].kind ==
      EvidenceKind::ExplicitFeedback);

  assert(
      evidence[2].polarity ==
      EvidencePolarity::Negative);

  assert(
      evidence[2].confidence == 1.0);

  assert(
      !may_feed_personal_learning(
          recorder.session()));

  return 0;
}
