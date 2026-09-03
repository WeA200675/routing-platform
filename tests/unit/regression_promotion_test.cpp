#include <cassert>
#include <iostream>
#include <string>

#include "routing/core/testing/regression_promotion_report.hpp"

namespace {

routing::core::drive::DriveSession
make_base_session() {
  using namespace routing::core::drive;

  DriveSession session;

  session.header.session_id =
      "drive:test:speed30";

  session.header.context_tags = {
      "tester:alpha",
      "trip:regression",
  };

  session.request.origin = {
      47.1410,
      9.5209,
  };

  session.request.destination = {
      47.2410,
      9.5310,
  };

  session.request.candidate_family =
      "profile-optimal";

  session.request.alternatives_requested =
      3;

  session.request.costing_profile =
      "auto";

  session.selected_route.route_id =
      "bad-route";

  session.selected_route.candidate_family =
      "profile-optimal";

  session.selected_route.distance_m =
      12000.0;

  session.selected_route.duration_s =
      800.0;

  session.selected_route.segment_ids = {
      "segment-a",
      "segment-b",
  };

  return session;
}

}  // namespace

int main() {
  using namespace routing::core::drive;
  using namespace routing::core::testing;

  auto session =
      make_base_session();

  DriveEvent feedback;

  feedback.id =
      "drive:test:speed30:event:1";

  feedback.sequence = 1;

  feedback.timestamp_ms = 1000;

  feedback.type =
      DriveEventType::FeedbackMarked;

  feedback.route_id =
      "bad-route";

  FeedbackMark mark;

  mark.sentiment =
      FeedbackSentiment::Negative;

  mark.reason =
      FeedbackReason::Speed30Zone;

  mark.severity = 5;

  mark.note =
      "Unnecessary 30 km/h zone.";

  feedback.feedback =
      mark;

  session.events.push_back(
      feedback);

  const auto proposals =
      build_regression_promotion_proposals(
          session);

  assert(proposals.size() == 1);

  const auto& proposal =
      proposals.front();

  assert(
      proposal.session_id ==
      "drive:test:speed30");

  assert(
      proposal.readiness ==
      RegressionPromotionReadiness::
          MetricSuggested);

  assert(
      proposal.issue_key ==
      "routing.feedback.speed-30-zone");

  assert(
      proposal.metric_suggestion
          .has_value());

  assert(
      proposal.metric_suggestion
          ->metric ==
      RouteMetric::Speed30OrLowerShare);

  assert(
      proposal.metric_suggestion
          ->direction ==
      MetricImprovementDirection::
          LowerIsBetter);

  assert(
      !proposal.runtime_semantics_complete);

  assert(
      proposal.human_approval_required);

  assert(
      proposal.missing_runtime_inputs
          .size() ==
      3);

  assert(
      proposal.scenario_seed
          .request.origin.latitude ==
      47.1410);

  assert(
      proposal.scenario_seed
          .request.destination.longitude ==
      9.5310);

  assert(
      proposal.scenario_seed
          .request.alternatives ==
      3);

  assert(
      proposal.scenario_seed
          .request.costing_profile
          .has_value());

  const auto report =
      format_regression_promotion_report(
          proposals);

  assert(
      report.find(
          "metric-suggested") !=
      std::string::npos);

  assert(
      report.find(
          "speed_30_or_lower_share") !=
      std::string::npos);

  assert(
      report.find(
          "threshold: HUMAN REVIEW REQUIRED") !=
      std::string::npos);

  assert(
      report.find(
          "runtime semantics: INCOMPLETE") !=
      std::string::npos);

  std::cout
      << "Regression promotion tests passed\n";

  return 0;
}
