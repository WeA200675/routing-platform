#include "routing/core/cost_engine.hpp"

#include <algorithm>
#include <cmath>
#include <limits>
#include <string>

namespace routing::core {
namespace {

double clamp01(double value) { return std::clamp(value, 0.0, 1.0); }

double segment_attribute_value(const StreetSegment& segment, Attribute attribute) {
  switch (attribute) {
    case Attribute::RoadClass:
      return static_cast<double>(segment.road_class);
    case Attribute::SpeedLimitKmh:
      return segment.speed_limit_kmh.value_or(std::numeric_limits<double>::quiet_NaN());
    case Attribute::CurvatureScore:
      return segment.curvature_score.value_or(std::numeric_limits<double>::quiet_NaN());
    case Attribute::SerpentineScore:
      return segment.serpentine_score.value_or(std::numeric_limits<double>::quiet_NaN());
    case Attribute::GradientAbsPct:
      return segment.gradient_abs_pct.value_or(std::numeric_limits<double>::quiet_NaN());
    case Attribute::UrbanScore:
      return segment.urban_score.value_or(std::numeric_limits<double>::quiet_NaN());
    case Attribute::DataConfidence:
      return segment.data_confidence.value_or(std::numeric_limits<double>::quiet_NaN());
  }
  return std::numeric_limits<double>::quiet_NaN();
}

bool compare(double lhs, CompareOp op, double rhs) {
  if (std::isnan(lhs)) {
    return false;
  }
  switch (op) {
    case CompareOp::Equal:
      return std::abs(lhs - rhs) < 1e-9;
    case CompareOp::Less:
      return lhs < rhs;
    case CompareOp::LessOrEqual:
      return lhs <= rhs;
    case CompareOp::Greater:
      return lhs > rhs;
    case CompareOp::GreaterOrEqual:
      return lhs >= rhs;
  }
  return false;
}

std::string reason_for(const Rule& rule) {
  return rule.name.empty() ? rule.id : rule.name;
}

}  // namespace

double CostEngine::expected_speed_kmh(const StreetSegment& segment) {
  const double legal = segment.speed_limit_kmh.value_or(50.0);
  const double practical = segment.practical_speed_kmh.value_or(legal);
  double speed = std::min(legal, practical);

  if (segment.curvature_score.has_value()) {
    speed *= 1.0 - 0.35 * clamp01(*segment.curvature_score);
  }
  if (segment.serpentine_score.has_value()) {
    speed *= 1.0 - 0.30 * clamp01(*segment.serpentine_score);
  }
  if (segment.gradient_abs_pct.has_value()) {
    const double gradient_factor = std::clamp(*segment.gradient_abs_pct / 20.0, 0.0, 1.0);
    speed *= 1.0 - 0.20 * gradient_factor;
  }

  return std::clamp(speed, 5.0, 160.0);
}

bool CostEngine::matches(const StreetSegment& segment, const Rule& rule) {
  return compare(segment_attribute_value(segment, rule.attribute), rule.op, rule.value);
}

double CostEngine::action_sign(RuleAction action) {
  switch (action) {
    case RuleAction::Prefer:
    case RuleAction::StronglyPrefer:
      return -1.0;
    case RuleAction::Avoid:
    case RuleAction::StronglyAvoid:
    case RuleAction::Exclude:
      return 1.0;
  }
  return 1.0;
}

double CostEngine::action_multiplier(RuleAction action) {
  switch (action) {
    case RuleAction::Prefer:
    case RuleAction::Avoid:
      return 1.0;
    case RuleAction::StronglyPrefer:
    case RuleAction::StronglyAvoid:
      return 2.0;
    case RuleAction::Exclude:
      return 0.0;
  }
  return 1.0;
}

SegmentCost CostEngine::evaluate(const StreetSegment& segment,
                                 const VehicleProfile& /*vehicle*/,
                                 const RuleSet& rules,
                                 const RoutingContext& context) const {
  SegmentCost result;

  if (!segment.access_allowed || segment.hard_user_excluded) {
    result.allowed = false;
    result.total_seconds_equivalent = std::numeric_limits<double>::infinity();
    return result;
  }

  const double speed_kmh = expected_speed_kmh(segment);
  const double speed_mps = speed_kmh / 3.6;
  result.expected_travel_seconds = segment.length_m / speed_mps;

  for (const Rule& rule : rules.rules) {
    if (!rule.enabled || !matches(segment, rule)) {
      continue;
    }
    if (rule.action == RuleAction::Exclude) {
      result.allowed = false;
      result.total_seconds_equivalent = std::numeric_limits<double>::infinity();
      result.contributions.push_back({rule.id, reason_for(rule), 0.0});
      return result;
    }

    const double normalized_strength = std::clamp(rule.strength, 0.0, 100.0) / 100.0;
    const double exposure_km = std::max(0.0, segment.length_m) / 1000.0;
    const double base_seconds_per_km = 90.0;
    const double seconds = action_sign(rule.action) * action_multiplier(rule.action) *
                           normalized_strength * exposure_km * base_seconds_per_km;
    result.preference_seconds += seconds;
    result.contributions.push_back({rule.id, reason_for(rule), seconds});
  }

  const double confidence = clamp01(segment.data_confidence.value_or(0.50));
  result.uncertainty_seconds = (1.0 - confidence) * (segment.length_m / 1000.0) * 20.0;

  // A preference bonus may improve a segment but must never erase its travel
  // time. The route-level comfort budget is an outer constraint; this local cap
  // additionally protects Dijkstra/A* edge costs from collapsing toward zero.
  const double bonus_fraction = std::clamp(context.max_segment_preference_bonus_fraction, 0.0, 0.90);
  const double local_bonus_cap = result.expected_travel_seconds * bonus_fraction;
  const double budget_cap = std::max(0.0, context.comfort_budget_seconds);
  const double max_bonus = std::min(local_bonus_cap, budget_cap);
  result.preference_seconds = std::max(result.preference_seconds, -max_bonus);

  result.total_seconds_equivalent =
      result.expected_travel_seconds + result.preference_seconds + result.uncertainty_seconds;
  return result;
}

}  // namespace routing::core
