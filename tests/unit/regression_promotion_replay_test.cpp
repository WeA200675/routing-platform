#include <algorithm>
#include <cassert>
#include <iostream>
#include <string>

#include "routing/core/drive/replay_semantics.hpp"
#include "routing/core/testing/regression_promotion.hpp"

namespace {

bool contains(
    const std::vector<std::string>& values,
    const std::string& expected) {
  return std::find(
             values.begin(),
             values.end(),
             expected) !=
         values.end();
}

routing::core::drive::DriveSession
make_session() {
  using namespace routing::core;
  using namespace routing::core::drive;

  DriveSession session;

  session.header.schema_version =
      kDriveSessionReplaySemanticsVersion;

  session.header.session_id =
      "drive:replay:promotion";

  session.header.versions.profile_id =
      "tester-tourer";

  session.header.versions.profile_version =
      "2026-09";

  session.header.versions.rules_version =
      "42";

  session.header.versions.map_data_version =
      "liechtenstein-fixture";

  session.request.origin = {
      47.1410,
      9.5209,
  };

  session.request.destination = {
      47.2410,
      9.5310,
  };

  session.request.candidate_family =
      "major_roads";

  session.request.alternatives_requested =
      3;

  session.request.costing_profile =
      "auto";

  session.selected_route.route_id =
      "observed-route";

  session.selected_route.candidate_family =
      "major_roads";

  session.selected_route.distance_m =
      12500.0;

  session.selected_route.duration_s =
      830.0;

  VehicleProfile vehicle;

  vehicle.id = "tester-tourer";
  vehicle.width_m = 2.05;
  vehicle.height_m = 1.88;
  vehicle.weight_kg = 2180.0;
  vehicle.trailer = true;

  RuleSet rules;

  rules.id = "tester-alpha";
  rules.version = "42";

  Rule rule;

  rule.id = "avoid-thirty";
  rule.name = "Avoid 30";
  rule.attribute =
      Attribute::SpeedLimitKmh;
  rule.op =
      CompareOp::LessOrEqual;
  rule.value = 30.0;
  rule.action =
      RuleAction::StronglyAvoid;
  rule.strength = 91.0;

  rules.rules.push_back(rule);

  RoutingContext context;

  context.comfort_budget_seconds =
      1100.0;

  context.shortcut_threshold_seconds =
      700.0;

  context.max_segment_preference_bonus_fraction =
      0.22;

  session.replay_semantics =
      make_replay_semantics_snapshot(
          vehicle,
          rules,
          context);

  DriveEvent feedback;

  feedback.id =
      "drive:replay:promotion:event:1";

  feedback.sequence = 1;
  feedback.timestamp_ms = 1000;

  feedback.type =
      DriveEventType::FeedbackMarked;

  feedback.route_id =
      "observed-route";

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

  return session;
}

}  // namespace

int main() {
  using namespace routing::core;
  using namespace routing::core::testing;

  const auto session =
      make_session();

  const auto proposals =
      build_regression_promotion_proposals(
          session);

  assert(proposals.size() == 1);

  const auto& proposal =
      proposals.front();

  assert(
      proposal.runtime_semantics_complete);

  assert(
      proposal.missing_runtime_inputs.empty());

  assert(
      proposal.scenario_seed.request.family ==
      CandidateFamily::MajorRoads);

  assert(
      proposal.scenario_seed.vehicle.id ==
      "tester-tourer");

  assert(
      proposal.scenario_seed.vehicle.trailer);

  assert(
      proposal.scenario_seed.rules.id ==
      "tester-alpha");

  assert(
      proposal.scenario_seed.rules.version ==
      "42");

  assert(
      proposal.scenario_seed.rules.rules.size() ==
      1);

  assert(
      proposal.scenario_seed.rules.rules.front()
          .strength ==
      91.0);

  assert(
      proposal.scenario_seed.context
          .comfort_budget_seconds ==
      1100.0);

  // Legacy session: evidence survives, but exact runtime replay
  // is not claimed.
  {
    auto legacy =
        session;

    legacy.header.schema_version =
        routing::core::drive::
            kDriveSessionLegacySchemaVersion;

    legacy.replay_semantics.reset();

    const auto legacy_proposals =
        build_regression_promotion_proposals(
            legacy);

    assert(
        legacy_proposals.size() ==
        1);

    assert(
        !legacy_proposals.front()
             .runtime_semantics_complete);

    assert(
        legacy_proposals.front()
            .missing_runtime_inputs
            .size() ==
        3);

    assert(
        contains(
            legacy_proposals.front()
                .missing_runtime_inputs,
            "vehicle_profile_snapshot"));
  }

  // Contradictory provenance must make the proposal incomplete
  // instead of silently trusting either side.
  {
    auto mismatch =
        session;

    mismatch.header.versions.profile_id =
        "different-profile";

    const auto mismatch_proposals =
        build_regression_promotion_proposals(
            mismatch);

    assert(
        mismatch_proposals.size() ==
        1);

    assert(
        !mismatch_proposals.front()
             .runtime_semantics_complete);

    assert(
        contains(
            mismatch_proposals.front()
                .missing_runtime_inputs,
            "vehicle_profile_id_mismatch"));
  }

  // Unknown future family identifiers must not be silently
  // represented as ProfileOptimal exact replay.
  {
    auto future =
        session;

    future.request.candidate_family =
        "future_family_v99";

    const auto future_proposals =
        build_regression_promotion_proposals(
            future);

    assert(
        future_proposals.size() ==
        1);

    assert(
        !future_proposals.front()
             .runtime_semantics_complete);

    assert(
        contains(
            future_proposals.front()
                .missing_runtime_inputs,
            "candidate_family_semantics"));
  }

  std::cout
      << "Regression promotion replay tests passed\n";

  return 0;
}
