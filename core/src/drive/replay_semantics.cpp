#include "routing/core/drive/replay_semantics.hpp"

#include <cmath>
#include <stdexcept>
#include <string>

namespace routing::core::drive {

namespace {

void require_finite(
    const double value,
    const char* field) {
  if (!std::isfinite(value)) {
    throw std::invalid_argument(
        std::string(
            "Replay semantics field must be finite: ") +
        field);
  }
}

}  // namespace

void validate_replay_semantics_snapshot(
    const ReplaySemanticsSnapshot& snapshot) {
  if (snapshot.schema_version !=
      kReplaySemanticsSchemaVersion) {
    throw std::invalid_argument(
        "Unsupported replay semantics schema version.");
  }

  require_finite(
      snapshot.vehicle.width_m,
      "vehicle.width_m");

  require_finite(
      snapshot.vehicle.height_m,
      "vehicle.height_m");

  require_finite(
      snapshot.vehicle.weight_kg,
      "vehicle.weight_kg");

  for (const auto& rule :
       snapshot.rules.rules) {
    require_finite(
        rule.value,
        "rule.value");

    require_finite(
        rule.strength,
        "rule.strength");
  }

  require_finite(
      snapshot.context.comfort_budget_seconds,
      "context.comfort_budget_seconds");

  require_finite(
      snapshot.context.shortcut_threshold_seconds,
      "context.shortcut_threshold_seconds");

  require_finite(
      snapshot.context
          .max_segment_preference_bonus_fraction,
      "context.max_segment_preference_bonus_fraction");
}

ReplaySemanticsSnapshot
make_replay_semantics_snapshot(
    const VehicleProfile& vehicle,
    const RuleSet& rules,
    const RoutingContext& context) {
  ReplaySemanticsSnapshot snapshot;

  snapshot.vehicle = vehicle;
  snapshot.rules = rules;
  snapshot.context = context;

  validate_replay_semantics_snapshot(
      snapshot);

  return snapshot;
}

}  // namespace routing::core::drive
