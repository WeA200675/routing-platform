#include "routing/core/intelligence/learning_gate.hpp"

#include <cmath>
#include <stdexcept>

namespace routing::core::intelligence {

LearningGateDecision evaluate_learning_gate(
    const drive::DriveSession& session,
    const AiPolicy& policy,
    const PreferenceHypothesis& hypothesis) {
  LearningGateDecision decision;

  if (!session.completed) {
    decision.reason =
        "session_not_completed";

    return decision;
  }

  if (hypothesis.session_id !=
      session.header.session_id) {
    decision.reason =
        "session_mismatch";

    return decision;
  }

  switch (session.header.learning_disposition) {
    case drive::LearningDisposition::Eligible:
      break;

    case drive::LearningDisposition::RecordOnly:
      decision.reason =
          "session_record_only";

      return decision;

    case drive::LearningDisposition::Excluded:
      decision.reason =
          "session_learning_excluded";

      return decision;
  }

  if (policy.mode ==
      AiAutonomyMode::Disabled) {
    decision.reason =
        "ai_disabled";

    return decision;
  }

  const AiAttributePolicy* attribute_policy =
      nullptr;

  for (const auto& item : policy.attributes) {
    if (item.attribute ==
        hypothesis.target.attribute) {
      attribute_policy = &item;
      break;
    }
  }

  if (attribute_policy == nullptr) {
    decision.reason =
        "no_attribute_policy";

    return decision;
  }

  decision.application_locked_by_user =
      attribute_policy->user_locked;

  if (!attribute_policy->permissions.observe) {
    decision.reason =
        "observation_not_allowed";

    return decision;
  }

  if (!attribute_policy->permissions.learn) {
    decision.reason =
        "learning_not_allowed";

    return decision;
  }

  decision.effective_learning_factor =
      effective_learning_factor(
          policy,
          *attribute_policy);

  if (decision.effective_learning_factor <= 0.0) {
    decision.reason =
        "learning_intensity_zero";

    return decision;
  }

  if (!std::isfinite(
          attribute_policy->
              minimum_learning_confidence) ||
      attribute_policy->
              minimum_learning_confidence < 0.0 ||
      attribute_policy->
              minimum_learning_confidence > 1.0) {
    throw std::invalid_argument(
        "Minimum learning confidence must be between 0 and 1.");
  }

  if (!std::isfinite(hypothesis.confidence) ||
      hypothesis.confidence < 0.0 ||
      hypothesis.confidence > 1.0) {
    throw std::invalid_argument(
        "Hypothesis confidence must be between 0 and 1.");
  }

  if (hypothesis.confidence <
      attribute_policy->
          minimum_learning_confidence) {
    decision.reason =
        "below_learning_confidence";

    return decision;
  }

  // Absichtlich NICHT durch user_locked blockiert.
  // Der Nutzer kann einen Wert fest verriegeln und trotzdem
  // erlauben, dass das System seine Praeferenz versteht.
  decision.may_store_personal_learning =
      true;

  decision.reason =
      decision.application_locked_by_user
      ? "learning_allowed_application_locked"
      : "learning_allowed";

  return decision;
}

}  // namespace routing::core::intelligence
