#include <cassert>

#include "routing/core/drive/drive_evidence.hpp"
#include "routing/core/intelligence/question_candidate.hpp"

int main() {
  using namespace routing::core::drive;
  using namespace routing::core::intelligence;

  DriveSessionHeader header;

  header.session_id =
      "question-drive";

  header.started_at_ms = 1000;

  header.context_tags = {
      "trip:normal",
  };

  RouteRequestSnapshot request;

  request.origin =
      {47.1410, 9.5209};

  request.destination =
      {47.1660, 9.5100};

  request.candidate_family =
      "profile_optimal";

  request.costing_profile =
      "auto";

  RouteSnapshot selected;

  selected.route_id =
      "route-a";

  selected.candidate_family =
      "profile_optimal";

  selected.distance_m = 1000.0;
  selected.duration_s = 100.0;

  RouteSnapshot alternative;

  alternative.route_id =
      "route-b";

  alternative.candidate_family =
      "major_roads";

  alternative.distance_m = 1100.0;
  alternative.duration_s = 105.0;

  DriveSessionRecorder recorder(
      header,
      request,
      selected,
      {alternative});

  DriveEvent deviation;

  deviation.timestamp_ms = 1100;

  deviation.type =
      DriveEventType::RouteDeviationDetected;

  deviation.route_id =
      "route-a";

  deviation.segment_id =
      "segment-x";

  deviation.deviation_distance_m = 30.0;
  deviation.detector_confidence = 0.92;

  (void)recorder.record(
      deviation);

  DriveEvent choice;

  choice.timestamp_ms = 1200;

  choice.type =
      DriveEventType::AlternativeSelected;

  choice.route_id =
      "route-a";

  choice.alternative_route_id =
      "route-b";

  (void)recorder.record(
      choice);

  recorder.finish(1300);

  const auto evidence =
      build_drive_evidence(
          recorder.session());

  AiPolicy policy;

  policy.mode =
      AiAutonomyMode::Ask;

  policy.global_question_intensity =
      LearningIntensity::from_ui_level(10);

  policy.max_questions_per_drive = 1;

  const auto questions =
      select_question_candidates(
          recorder.session(),
          evidence,
          policy);

  assert(questions.size() == 1);

  // Bewusste Alternativwahl gewinnt bei knappem Budget
  // gegen eine isolierte Abweichung.
  assert(
      questions.front().kind ==
      QuestionKind::AlternativeReason);

  assert(
      questions.front().prompt_key ==
      "why_alternative_selected");

  assert(
      questions.front().alternative_route_id ==
      "route-b");

  assert(
      questions.front().post_drive_only);

  assert(
      questions.front().evidence_ids.size() ==
      1);

  // Observe darf beobachten, aber nicht fragen.
  policy.mode =
      AiAutonomyMode::Observe;

  const auto observe_questions =
      select_question_candidates(
          recorder.session(),
          evidence,
          policy);

  assert(observe_questions.empty());

  // Lernen/Fragen komplett auf 0.
  policy.mode =
      AiAutonomyMode::Ask;

  policy.global_question_intensity =
      LearningIntensity::from_ui_level(0);

  const auto zero_questions =
      select_question_candidates(
          recorder.session(),
          evidence,
          policy);

  assert(zero_questions.empty());

  return 0;
}
