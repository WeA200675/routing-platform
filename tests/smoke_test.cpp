#include <cassert>
#include <cmath>
#include <iostream>

#include "routing/core/cost_engine.hpp"
#include "routing/core/version.hpp"

using namespace routing::core;

int main() {
  CostEngine engine;
  VehicleProfile vehicle;
  RoutingContext context;

  StreetSegment federal{
      .id = "federal-road",
      .length_m = 18000.0,
      .functional_road_class = FunctionalRoadClass::Primary,
      .road_network_class = RoadNetworkClass::FederalRoad,
      .speed_limit_kmh = 80.0,
      .practical_speed_kmh = 78.0,
      .curvature_score = 0.10,
      .serpentine_score = 0.0,
      .gradient_abs_pct = 2.0,
      .urban_score = 0.05,
      .data_confidence = 0.95,
  };

  StreetSegment shortcut{
      .id = "mountain-shortcut",
      .length_m = 14000.0,
      .functional_road_class = FunctionalRoadClass::Residential,
      .road_network_class = RoadNetworkClass::MunicipalRoad,
      .speed_limit_kmh = 30.0,
      .practical_speed_kmh = 30.0,
      .curvature_score = 0.85,
      .serpentine_score = 0.90,
      .gradient_abs_pct = 9.0,
      .urban_score = 0.65,
      .data_confidence = 0.90,
  };

  RuleSet rules;
  rules.id = "long-distance";
  rules.rules = {
      {.id = "prefer-federal", .name = "Bundesstraße bevorzugen", .attribute = Attribute::RoadNetworkClass,
       .op = CompareOp::Equal,
       .value = static_cast<double>(RoadNetworkClass::FederalRoad),
       .action = RuleAction::StronglyPrefer, .strength = 70.0},
      {.id = "avoid-30", .name = "Tempo 30 vermeiden", .attribute = Attribute::SpeedLimitKmh,
       .op = CompareOp::LessOrEqual, .value = 30.0,
       .action = RuleAction::StronglyAvoid, .strength = 90.0},
      {.id = "avoid-serpentines", .name = "Serpentinen vermeiden", .attribute = Attribute::SerpentineScore,
       .op = CompareOp::GreaterOrEqual, .value = 0.65,
       .action = RuleAction::StronglyAvoid, .strength = 95.0},
      {.id = "avoid-gradient", .name = "Starke Steigung vermeiden", .attribute = Attribute::GradientAbsPct,
       .op = CompareOp::GreaterOrEqual, .value = 7.0,
       .action = RuleAction::Avoid, .strength = 60.0},
  };

  const auto federal_cost = engine.evaluate(federal, vehicle, rules, context);
  const auto shortcut_cost = engine.evaluate(shortcut, vehicle, rules, context);

  assert(federal_cost.allowed);
  assert(shortcut_cost.allowed);
  assert(std::isfinite(federal_cost.total_seconds_equivalent));
  assert(federal_cost.total_seconds_equivalent > 0.0);
  assert(federal_cost.total_seconds_equivalent >= federal_cost.expected_travel_seconds * 0.60);
  assert(std::isfinite(shortcut_cost.total_seconds_equivalent));
  assert(federal_cost.total_seconds_equivalent < shortcut_cost.total_seconds_equivalent);

  StreetSegment excluded = shortcut;
  rules.rules.push_back({.id = "exclude-serpentines", .name = "Serpentinen ausschließen",
                         .attribute = Attribute::SerpentineScore,
                         .op = CompareOp::GreaterOrEqual, .value = 0.80,
                         .action = RuleAction::Exclude, .strength = 100.0});
  const auto excluded_cost = engine.evaluate(excluded, vehicle, rules, context);
  assert(!excluded_cost.allowed);

  std::cout << "routing-platform " << kPlatformVersion << " smoke test PASS\n";
  std::cout << "Federal road cost: " << federal_cost.total_seconds_equivalent << " s-eq\n";
  std::cout << "Mountain shortcut cost: " << shortcut_cost.total_seconds_equivalent << " s-eq\n";
  return 0;
}
