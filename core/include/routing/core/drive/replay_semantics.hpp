#pragma once

#include <cstdint>

#include "routing/core/model.hpp"
#include "routing/core/rule.hpp"

namespace routing::core::drive {

// Version of the executable routing-semantics snapshot itself.
// This is deliberately independent from DriveSession schema versioning.
inline constexpr std::uint32_t
kReplaySemanticsSchemaVersion = 1;

struct ReplaySemanticsSnapshot {
  std::uint32_t schema_version =
      kReplaySemanticsSchemaVersion;

  // Exact value objects used for route evaluation/orchestration.
  VehicleProfile vehicle;
  RuleSet rules;
  RoutingContext context;
};

// Snapshot validation checks representation/replay safety.
// It deliberately does NOT invent tighter routing-domain constraints
// than the live engine currently enforces.
void validate_replay_semantics_snapshot(
    const ReplaySemanticsSnapshot& snapshot);

[[nodiscard]]
ReplaySemanticsSnapshot
make_replay_semantics_snapshot(
    const VehicleProfile& vehicle,
    const RuleSet& rules,
    const RoutingContext& context);

}  // namespace routing::core::drive
