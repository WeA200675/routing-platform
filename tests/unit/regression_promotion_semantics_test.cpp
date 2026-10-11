#include <cassert>
#include <iostream>

#include "routing/core/testing/regression_promotion.hpp"

namespace {

routing::core::drive::DriveSession
base_session() {
  using namespace routing::core::drive;

  DriveSession session;

  session.header.session_id =
      "drive:test:semantics";

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

  session.request.costing_profile =
      "auto";

  session.selected_route.route_id =
      "route-a";

  session.selected_route.candidate_family =
      "profile-optimal";

  session.selected_route.distance_m =
      10000.0;

  session.selected_route.duration_s =
      700.0;

  routing::core::drive::RouteSnapshot alternative;

  alternative.route_id =
      "route-b";

  alternative.candidate_family =
      "major-roads";

  alternative.distance_m =
      11000.0;

  alternative.duration_s =
      720.0;

  session.alternatives.push_back(
      alternative);

  return session;
}

}  // namespace

int main() {
  using namespace routing::core::drive;
  using namespace routing::core::testing;

  auto session =
      base_session();

  DriveEvent positive;

  positive.id =
      "drive:test:semantics:event:1";

  positive.sequence = 1;
  positive.timestamp_ms = 1000;

  positive.type =
      DriveEventType::FeedbackMarked;

  positive.route_id =
      "route-a";

  FeedbackMark positive_mark;

  positive_mark.sentiment =
      FeedbackSentiment::Positive;

  positive_mark.reason =
      FeedbackReason::RouteWasGood;

  positive.feedback =
      positive_mark;

  session.events.push_back(
      positive);

  DriveEvent deviation;

  deviation.id =
      "drive:test:semantics:event:2";

  deviation.sequence = 2;
  deviation.timestamp_ms = 2000;

  deviation.type =
      DriveEventType::RouteDeviationDetected;

  deviation.route_id =
      "route-a";

  deviation.segment_id =
      "segment-x";

  deviation.deviation_distance_m =
      80.0;

  deviation.detector_confidence =
      0.95;

  session.events.push_back(
      deviation);

  DriveEvent alternative;

  alternative.id =
      "drive:test:semantics:event:3";

  alternative.sequence = 3;
  alternative.timestamp_ms = 3000;

  alternative.type =
      DriveEventType::AlternativeSelected;

  alternative.route_id =
      "route-a";

  alternative.alternative_route_id =
      "route-b";

  session.events.push_back(
      alternative);

  const auto proposals =
      build_regression_promotion_proposals(
          session);

  bool found_deviation = false;
  bool found_alternative = false;
  bool found_positive_feedback = false;

  for (const auto& proposal :
       proposals) {
    if (proposal.source_event_id ==
        positive.id) {
      found_positive_feedback = true;
    }

    if (proposal.evidence_kind ==
        EvidenceKind::RouteDeviation) {
      found_deviation = true;

      assert(
          proposal.readiness ==
          RegressionPromotionReadiness::
              DiagnosticOnly);

      assert(
          !proposal.metric_suggestion
               .has_value());
    }

    if (proposal.evidence_kind ==
        EvidenceKind::AlternativeSelection) {
      found_alternative = true;

      assert(
          proposal.readiness ==
          RegressionPromotionReadiness::
              AlternativeSuggested);

      assert(
          proposal.preferred_alternative_route_id ==
          "route-b");

      // A selected alternative is not silently converted into a
      // global routing preference or fabricated metric.
      assert(
          !proposal.metric_suggestion
               .has_value());
    }
  }

  assert(found_deviation);
  assert(found_alternative);

  // Positive feedback is learning evidence, but not a "bad-route"
  // regression-promotion proposal.
  assert(!found_positive_feedback);

  std::cout
      << "Regression promotion semantics tests passed\n";

  return 0;
}
