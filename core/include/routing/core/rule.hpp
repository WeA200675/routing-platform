#pragma once

#include <cstdint>
#include <string>
#include <vector>

namespace routing::core {

enum class Attribute : std::uint8_t {
  FunctionalRoadClass = 0,
  SpeedLimitKmh = 1,
  CurvatureScore = 2,
  SerpentineScore = 3,
  GradientAbsPct = 4,
  UrbanScore = 5,
  DataConfidence = 6,
  RoadNetworkClass = 7,
};

enum class CompareOp : std::uint8_t {
  Equal = 0,
  Less,
  LessOrEqual,
  Greater,
  GreaterOrEqual,
};

enum class RuleAction : std::uint8_t {
  Prefer = 0,
  StronglyPrefer,
  Avoid,
  StronglyAvoid,
  Exclude,
};

struct Rule {
  std::string id;
  std::string name;
  bool enabled = true;
  Attribute attribute = Attribute::SpeedLimitKmh;
  CompareOp op = CompareOp::Equal;
  double value = 0.0;
  RuleAction action = RuleAction::Avoid;
  double strength = 50.0;  // 0..100
  int priority = 0;
};

struct RuleSet {
  std::string id = "default";
  std::string version = "1";
  std::vector<Rule> rules;
};

}  // namespace routing::core
