#pragma once

#include <string>

#include "routing/core/drive/drive_session.hpp"
#include "routing/core/intelligence/ai_policy.hpp"
#include "routing/core/intelligence/preference_hypothesis.hpp"

namespace routing::core::intelligence {

struct LearningGateDecision {
  bool may_store_personal_learning = false;

  // Ein User-Lock verhindert Anwendung.
  // Er verhindert nicht automatisch Beobachtung/Lernen.
  bool application_locked_by_user = false;

  double effective_learning_factor = 0.0;

  std::string reason;
};

[[nodiscard]]
LearningGateDecision evaluate_learning_gate(
    const drive::DriveSession& session,
    const AiPolicy& policy,
    const PreferenceHypothesis& hypothesis);

}  // namespace routing::core::intelligence
