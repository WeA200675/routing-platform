#pragma once

#include <string>
#include <vector>

#include "routing/core/model.hpp"
#include "routing/core/rule.hpp"

namespace routing::core {

struct CostContribution {
  std::string rule_id;
  std::string reason;
  double seconds_equivalent = 0.0;
};

struct SegmentCost {
  bool allowed = true;
  double expected_travel_seconds = 0.0;
  double preference_seconds = 0.0;
  double uncertainty_seconds = 0.0;
  double total_seconds_equivalent = 0.0;
  std::vector<CostContribution> contributions;
};

class CostEngine {
 public:
  [[nodiscard]] SegmentCost evaluate(const StreetSegment& segment,
                                     const VehicleProfile& vehicle,
                                     const RuleSet& rules,
                                     const RoutingContext& context) const;

 private:
  [[nodiscard]] static double expected_speed_kmh(const StreetSegment& segment);
  [[nodiscard]] static bool matches(const StreetSegment& segment, const Rule& rule);
  [[nodiscard]] static double action_sign(RuleAction action);
  [[nodiscard]] static double action_multiplier(RuleAction action);
};

}  // namespace routing::core
