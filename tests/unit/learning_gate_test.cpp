#include <cassert>

#include "routing/core/intelligence/learning_gate.hpp"

int main() {
  using namespace routing::core;
  using namespace routing::core::drive;
  using namespace routing::core::intelligence;

  DriveSession session;

  session.header.session_id =
      "learning-drive";

  session.header.learning_disposition =
      LearningDisposition::Eligible;

  session.completed = true;

  PreferenceHypothesis hypothesis;

  hypothesis.id =
      "learning-drive:hypothesis:1";

  hypothesis.session_id =
      "learning-drive";

  hypothesis.target.attribute =
      Attribute::SpeedLimitKmh;

  hypothesis.target.condition_key =
      "speed_limit_kmh<=30";

  hypothesis.direction =
      PreferenceDirection::Avoid;

  hypothesis.strength = 1.0;
  hypothesis.confidence = 0.90;

  AiPolicy policy;

  policy.mode =
      AiAutonomyMode::Shadow;

  policy.global_learning_intensity =
      LearningIntensity::from_ui_level(10);

  AiAttributePolicy attribute_policy;

  attribute_policy.attribute =
      Attribute::SpeedLimitKmh;

  attribute_policy.learning_intensity =
      LearningIntensity::from_ui_level(8);

  attribute_policy.permissions.observe = true;
  attribute_policy.permissions.learn = true;

  attribute_policy.minimum_learning_confidence =
      0.60;

  attribute_policy.user_locked = true;

  policy.attributes.push_back(
      attribute_policy);

  const auto locked =
      evaluate_learning_gate(
          session,
          policy,
          hypothesis);

  // Kernregel:
  // Lock verhindert Anwendung, aber nicht Lernen.
  assert(
      locked.may_store_personal_learning);

  assert(
      locked.application_locked_by_user);

  assert(
      locked.reason ==
      "learning_allowed_application_locked");

  session.header.learning_disposition =
      LearningDisposition::RecordOnly;

  const auto record_only =
      evaluate_learning_gate(
          session,
          policy,
          hypothesis);

  assert(
      !record_only.may_store_personal_learning);

  assert(
      record_only.reason ==
      "session_record_only");

  session.header.learning_disposition =
      LearningDisposition::Eligible;

  hypothesis.confidence = 0.40;

  const auto uncertain =
      evaluate_learning_gate(
          session,
          policy,
          hypothesis);

  assert(
      !uncertain.may_store_personal_learning);

  assert(
      uncertain.reason ==
      "below_learning_confidence");

  hypothesis.confidence = 0.90;

  policy.attributes.front().
      learning_intensity =
      LearningIntensity::from_ui_level(0);

  const auto zero_learning =
      evaluate_learning_gate(
          session,
          policy,
          hypothesis);

  assert(
      !zero_learning.
          may_store_personal_learning);

  assert(
      zero_learning.reason ==
      "learning_intensity_zero");

  return 0;
}
