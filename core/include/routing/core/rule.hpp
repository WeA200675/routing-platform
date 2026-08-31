#pragma once

#include <cstdint>
#include <string>
#include <vector>

namespace routing::core {

enum class Attribute : std::uint8_t {
  RoadClass = 0,
  SpeedLimitKmh,
  CurvatureScore,
  SerpentineScore,
  GradientAbsPct,
  UrbanScore,
  DataConfidence,
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
