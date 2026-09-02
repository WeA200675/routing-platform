#pragma once

#include <cstdint>
#include <string>
#include <vector>

#include "routing/core/rule.hpp"

namespace routing::core::intelligence {

// UI: 0..10
// Intern: 0..1000, damit später Experten-Feintuning möglich ist,
// ohne das Datenmodell ändern zu müssen.
struct LearningIntensity {
  std::uint16_t permille = 500;

  [[nodiscard]] static LearningIntensity from_ui_level(
      std::uint8_t level);

  [[nodiscard]] std::uint8_t ui_level() const;
  [[nodiscard]] double normalized() const;
};

enum class AiAutonomyMode : std::uint8_t {
  Disabled = 0,
  Observe,
  Ask,
  Propose,
  Shadow,
  BoundedAutomatic,
};

struct AiPermissions {
  bool observe = true;
  bool learn = true;
  bool ask = true;
  bool propose = true;
  bool apply = false;
};

struct AiAttributePolicy {
  Attribute attribute =
      Attribute::FunctionalRoadClass;

  LearningIntensity learning_intensity{
      LearningIntensity::from_ui_level(5)};

  LearningIntensity question_intensity{
      LearningIntensity::from_ui_level(5)};

  AiPermissions permissions;

  // Harte User-Entscheidung:
  // Die KI darf dieses Attribut nicht verändern.
  bool user_locked = false;

  // Maximaler absoluter KI-Beitrag für dieses Attribut.
  double max_abs_adjustment = 0.0;

  // 0.0 .. 1.0
  double minimum_confidence = 0.90;
};

struct AiPolicy {
  LearningIntensity global_learning_intensity{
      LearningIntensity::from_ui_level(5)};

  LearningIntensity global_question_intensity{
      LearningIntensity::from_ui_level(5)};

  AiAutonomyMode mode =
      AiAutonomyMode::Shadow;

  std::uint32_t max_questions_per_drive = 2;

  std::vector<AiAttributePolicy> attributes;
};

struct AiParameterAdjustment {
  Attribute attribute =
      Attribute::FunctionalRoadClass;

  double requested_delta = 0.0;
  double confidence = 0.0;

  std::string reason;
  std::vector<std::string> evidence_ids;
};

struct AiAdjustmentDecision {
  // Nach User-Grenze begrenzter Vorschlag.
  double bounded_delta = 0.0;

  // Tatsächlich auf aktives Routing anwendbarer Wert.
  // In Shadow/Proposal immer 0.
  double effective_delta = 0.0;

  bool eligible = false;
  bool shadow_only = false;

  std::string reason;
};

[[nodiscard]] const AiAttributePolicy*
find_attribute_policy(
    const AiPolicy& policy,
    Attribute attribute);

[[nodiscard]] double effective_learning_factor(
    const AiPolicy& policy,
    const AiAttributePolicy& attribute_policy);

[[nodiscard]] AiAdjustmentDecision
evaluate_ai_adjustment(
    const AiPolicy& policy,
    const AiAttributePolicy& attribute_policy,
    const AiParameterAdjustment& adjustment);

}  // namespace routing::core::intelligence
