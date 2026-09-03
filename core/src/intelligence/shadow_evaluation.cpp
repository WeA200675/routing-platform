#include "routing/core/intelligence/shadow_evaluation.hpp"

#include <cmath>

namespace routing::core::intelligence {

namespace {

bool autonomy_allows_shadow(
    const AiAutonomyMode mode) {
  switch (mode) {
    case AiAutonomyMode::Disabled:
    case AiAutonomyMode::Observe:
    case AiAutonomyMode::Ask:
      return false;

    case AiAutonomyMode::Propose:
    case AiAutonomyMode::Shadow:
    case AiAutonomyMode::BoundedAutomatic:
      return true;
  }

  return false;
}

}  // namespace

ShadowEvaluationCandidate
make_shadow_evaluation_candidate(
    const drive::DriveSession& session,
    const AiPolicy& policy,
    const PreferenceHypothesis& hypothesis) {
  ShadowEvaluationCandidate candidate;

  candidate.id =
      hypothesis.id +
      ":shadow";

  candidate.session_id =
      hypothesis.session_id;

  candidate.target =
      hypothesis.target;

  candidate.direction =
      hypothesis.direction;

  candidate.strength =
      hypothesis.strength;

  candidate.confidence =
      hypothesis.confidence;

  candidate.evidence_ids =
      hypothesis.evidence_ids;

  candidate.context_tags =
      hypothesis.context_tags;

  // Diese Garantie darf spaeter nicht versehentlich durch
  // BoundedAutomatic aufgeweicht werden.
  candidate.production_application_allowed =
      false;

  if (!session.completed) {
    candidate.reason =
        "session_not_completed";

    return candidate;
  }

  if (hypothesis.session_id !=
      session.header.session_id) {
    candidate.reason =
        "session_mismatch";

    return candidate;
  }

  if (!std::isfinite(hypothesis.confidence) ||
      hypothesis.confidence < 0.0 ||
      hypothesis.confidence > 1.0) {
    candidate.reason =
        "invalid_hypothesis_confidence";

    return candidate;
  }

  const AiAttributePolicy* attribute_policy =
      find_attribute_policy(
          policy,
          hypothesis.target.attribute);

  if (attribute_policy == nullptr) {
    candidate.reason =
        "no_attribute_policy";

    return candidate;
  }

  candidate.application_locked_by_user =
      attribute_policy->user_locked;

  if (!attribute_policy->permissions.observe) {
    candidate.reason =
        "observation_not_allowed";

    return candidate;
  }

  if (!autonomy_allows_shadow(
          policy.mode)) {
    candidate.reason =
        "autonomy_mode_not_shadow_eligible";

    return candidate;
  }

  // Shadow ist Analyse, keine Mutation.
  // Deshalb darf auch eine RecordOnly-Testfahrt hier ausgewertet
  // werden. Ob persoenliches Lernen erlaubt ist, bleibt separat.
  candidate.eligible_for_shadow = true;

  const auto learning_gate =
      evaluate_learning_gate(
          session,
          policy,
          hypothesis);

  candidate.may_store_personal_learning =
      learning_gate.may_store_personal_learning;

  candidate.learning_factor =
      learning_gate.effective_learning_factor;

  if (candidate.may_store_personal_learning) {
    candidate.reason =
        candidate.application_locked_by_user
        ? "shadow_allowed_application_locked"
        : "shadow_allowed";
  } else {
    candidate.reason =
        "shadow_allowed_diagnostic_only";
  }

  return candidate;
}

}  // namespace routing::core::intelligence
