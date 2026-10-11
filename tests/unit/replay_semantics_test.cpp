#include <cassert>
#include <cmath>
#include <iostream>
#include <limits>
#include <stdexcept>

#include "routing/core/drive/replay_semantics.hpp"

int main() {
  using namespace routing::core;
  using namespace routing::core::drive;

  VehicleProfile vehicle;

  vehicle.id = "tester-tourer";
  vehicle.width_m = 2.03;
  vehicle.height_m = 1.91;
  vehicle.weight_kg = 2240.0;
  vehicle.trailer = true;

  RuleSet rules;

  rules.id = "tester-alpha-rules";
  rules.version = "42";

  Rule rule;

  rule.id = "avoid-30";
  rule.name = "Avoid unnecessary 30 zones";
  rule.enabled = true;
  rule.attribute = Attribute::SpeedLimitKmh;
  rule.op = CompareOp::LessOrEqual;
  rule.value = 30.0;
  rule.action = RuleAction::StronglyAvoid;
  rule.strength = 87.5;
  rule.priority = 120;

  rules.rules.push_back(rule);

  RoutingContext context;

  context.comfort_budget_seconds = 1234.5;
  context.shortcut_threshold_seconds = 456.25;
  context.max_segment_preference_bonus_fraction =
      0.27;

  const auto snapshot =
      make_replay_semantics_snapshot(
          vehicle,
          rules,
          context);

  assert(
      snapshot.schema_version ==
      kReplaySemanticsSchemaVersion);

  assert(
      snapshot.vehicle.id ==
      "tester-tourer");

  assert(
      snapshot.vehicle.width_m ==
      2.03);

  assert(snapshot.vehicle.trailer);

  assert(
      snapshot.rules.id ==
      "tester-alpha-rules");

  assert(
      snapshot.rules.version ==
      "42");

  assert(
      snapshot.rules.rules.size() ==
      1);

  assert(
      snapshot.rules.rules.front().strength ==
      87.5);

  assert(
      snapshot.context.comfort_budget_seconds ==
      1234.5);

  assert(
      snapshot.context
          .max_segment_preference_bonus_fraction ==
      0.27);

  validate_replay_semantics_snapshot(
      snapshot);

  {
    auto invalid = snapshot;

    invalid.context.comfort_budget_seconds =
        std::numeric_limits<double>::infinity();

    bool rejected = false;

    try {
      validate_replay_semantics_snapshot(
          invalid);
    } catch (const std::invalid_argument&) {
      rejected = true;
    }

    assert(rejected);
  }

  {
    auto unsupported = snapshot;
    unsupported.schema_version = 999;

    bool rejected = false;

    try {
      validate_replay_semantics_snapshot(
          unsupported);
    } catch (const std::invalid_argument&) {
      rejected = true;
    }

    assert(rejected);
  }

  std::cout
      << "Replay semantics tests passed\n";

  return 0;
}
