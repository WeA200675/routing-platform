#include <cassert>

#include "routing/core/intelligence/shadow_evaluation.hpp"

int main() {
  using namespace routing::core;
  using namespace routing::core::drive;
  using namespace routing::core::intelligence;

  DriveSession session;

  session.header.session_id =
      "shadow-drive";

  session.header.learning_disposition =
      LearningDisposition::Eligible;

  session.completed = true;

  PreferenceHypothesis hypothesis;

  hypothesis.id =
      "shadow-drive:hypothesis:1";

  hypothesis.session_id =
      "shadow-drive";

  hypothesis.target.attribute =
      Attribute::SpeedLimitKmh;

  hypothesis.target.condition_key =
      "speed_limit_kmh<=30";

  hypothesis.direction =
      PreferenceDirection::Avoid;

  hypothesis.strength = 1.0;
  hypothesis.confidence = 0.95;

  hypothesis.evidence_ids = {
      "shadow-drive:evidence:1",
  };

  hypothesis.context_tags = {
      "trip:normal",
  };

  AiPolicy policy;

  policy.mode =
      AiAutonomyMode::Shadow;

  policy.global_learning_intensity =
      LearningIntensity::from_ui_level(10);

  AiAttributePolicy attribute;

  attribute.attribute =
      Attribute::SpeedLimitKmh;

  attribute.learning_intensity =
      LearningIntensity::from_ui_level(10);

  attribute.permissions.observe = true;
  attribute.permissions.learn = true;

  attribute.minimum_learning_confidence =
      0.60;

  // User-Lock verhindert Anwendung,
  // aber nicht Lernen oder Shadow-Analyse.
  attribute.user_locked = true;

  policy.attributes.push_back(
      attribute);

  const auto eligible =
      make_shadow_evaluation_candidate(
          session,
          policy,
          hypothesis);

  assert(
      eligible.eligible_for_shadow);

  assert(
      eligible.may_store_personal_learning);

  assert(
      eligible.application_locked_by_user);

  // Wichtigster Sicherheits-Contract:
  assert(
      !eligible.production_application_allowed);

  assert(
      eligible.reason ==
      "shadow_allowed_application_locked");

  assert(
      eligible.evidence_ids.size() == 1);

  assert(
      eligible.context_tags.size() == 1);

  // Tester-/Diagnosefahrt:
  // kein persoenliches Lernen, aber Shadow-Auswertung erlaubt.
  session.header.learning_disposition =
      LearningDisposition::RecordOnly;

  const auto record_only =
      make_shadow_evaluation_candidate(
          session,
          policy,
          hypothesis);

  assert(
      record_only.eligible_for_shadow);

  assert(
      !record_only.may_store_personal_learning);

  assert(
      !record_only.production_application_allowed);

  assert(
      record_only.reason ==
      "shadow_allowed_diagnostic_only");

  // Observe allein darf noch keine Shadow-Auswertung vorbereiten.
  session.header.learning_disposition =
      LearningDisposition::Eligible;

  policy.mode =
      AiAutonomyMode::Observe;

  const auto observe_only =
      make_shadow_evaluation_candidate(
          session,
          policy,
          hypothesis);

  assert(
      !observe_only.eligible_for_shadow);

  assert(
      !observe_only.production_application_allowed);

  assert(
      observe_only.reason ==
      "autonomy_mode_not_shadow_eligible");

  // Ohne Beobachtungsrecht ebenfalls kein Shadow.
  policy.mode =
      AiAutonomyMode::Shadow;

  policy.attributes.front().
      permissions.observe = false;

  const auto observation_blocked =
      make_shadow_evaluation_candidate(
          session,
          policy,
          hypothesis);

  assert(
      !observation_blocked.eligible_for_shadow);

  assert(
      observation_blocked.reason ==
      "observation_not_allowed");

  return 0;
}
