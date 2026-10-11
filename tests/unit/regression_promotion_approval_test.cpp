#include <cassert>
#include <iostream>
#include <stdexcept>

#include "routing/core/testing/regression_promotion.hpp"

namespace {

routing::core::testing::RegressionPromotionProposal
make_proposal() {
  using namespace routing::core;
  using namespace routing::core::drive;
  using namespace routing::core::testing;

  DriveSession session;

  session.header.session_id =
      "drive:test:approval";

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
      "bad-route";

  session.selected_route.candidate_family =
      "profile-optimal";

  session.selected_route.distance_m =
      12000.0;

  session.selected_route.duration_s =
      800.0;

  DriveEvent feedback;

  feedback.id =
      "drive:test:approval:event:1";

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

  feedback.feedback =
      mark;

  session.events.push_back(
      feedback);

  const auto proposals =
      build_regression_promotion_proposals(
          session);

  assert(proposals.size() == 1);

  return proposals.front();
}

routing::core::testing::RegressionPromotionApproval
make_approval(
    const routing::core::testing::
        RegressionPromotionProposal& proposal) {
  using namespace routing::core::testing;

  RegressionPromotionApproval approval;

  approval.approved = true;

  approval.confirmed_runtime_semantics =
      true;

  approval
      .confirmed_expectation_captures_issue =
          true;

  approval.disposition =
      RegressionDisposition::ObserveOnly;

  approval.case_id =
      "tester:alpha:speed30:001";

  approval.title =
      "Tester alpha reported unnecessary 30 km/h routing";

  approval.reviewed_scenario =
      proposal.scenario_seed;

  approval.reviewed_scenario.id =
      approval.case_id;

  approval.reviewed_scenario.title =
      approval.title;

  ScenarioMetricExpectation expectation;

  expectation.metric =
      RouteMetric::Speed30OrLowerShare;

  // The threshold is deliberately supplied by the reviewer.
  // It was not invented by promotion logic.
  expectation.maximum_value =
      0.05;

  expectation.minimum_known_coverage =
      0.80;

  approval.reviewed_scenario
      .expectations
      .selected_route_metrics
      .push_back(
          expectation);

  return approval;
}

}  // namespace

int main() {
  using namespace routing::core::testing;

  const auto proposal =
      make_proposal();

  {
    RegressionPromotionApproval approval;

    const auto result =
        approve_regression_promotion(
            proposal,
            approval);

    assert(!result.has_value());
  }

  auto approval =
      make_approval(
          proposal);

  {
    auto incomplete =
        approval;

    incomplete.confirmed_runtime_semantics =
        false;

    bool rejected = false;

    try {
      (void)approve_regression_promotion(
          proposal,
          incomplete);
    } catch (const std::invalid_argument&) {
      rejected = true;
    }

    assert(rejected);
  }

  {
    auto wrong_metric =
        approval;

    wrong_metric.reviewed_scenario
        .expectations
        .selected_route_metrics
        .clear();

    ScenarioMetricExpectation expectation;

    expectation.metric =
        RouteMetric::MajorRoadShare;

    expectation.minimum_value =
        0.50;

    wrong_metric.reviewed_scenario
        .expectations
        .selected_route_metrics
        .push_back(
            expectation);

    bool rejected = false;

    try {
      (void)approve_regression_promotion(
          proposal,
          wrong_metric);
    } catch (const std::invalid_argument&) {
      rejected = true;
    }

    assert(rejected);
  }

  {
    auto gating =
        approval;

    gating.disposition =
        RegressionDisposition::Gating;

    gating.allow_gating =
        false;

    bool rejected = false;

    try {
      (void)approve_regression_promotion(
          proposal,
          gating);
    } catch (const std::invalid_argument&) {
      rejected = true;
    }

    assert(rejected);
  }

  const auto observe_case =
      approve_regression_promotion(
          proposal,
          approval);

  assert(observe_case.has_value());

  assert(
      observe_case->case_id ==
      approval.case_id);

  assert(
      observe_case->disposition ==
      RegressionDisposition::ObserveOnly);

  assert(
      observe_case->provenance.source ==
      RegressionCaseSource::DriveSession);

  assert(
      observe_case->provenance.source_ref ==
      "drive:test:approval");

  assert(
      observe_case->scenario
          .expectations
          .selected_route_metrics
          .size() ==
      1);

  validate_regression_case(
      *observe_case);

  {
    auto gating =
        approval;

    gating.disposition =
        RegressionDisposition::Gating;

    gating.allow_gating =
        true;

    const auto gating_case =
        approve_regression_promotion(
            proposal,
            gating);

    assert(gating_case.has_value());

    assert(
        gating_case->disposition ==
        RegressionDisposition::Gating);
  }

  std::cout
      << "Regression promotion approval tests passed\n";

  return 0;
}
