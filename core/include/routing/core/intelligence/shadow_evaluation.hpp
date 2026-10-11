#pragma once

#include <string>
#include <vector>

#include "routing/core/drive/drive_session.hpp"
#include "routing/core/intelligence/ai_policy.hpp"
#include "routing/core/intelligence/learning_gate.hpp"
#include "routing/core/intelligence/preference_hypothesis.hpp"

namespace routing::core::intelligence {

struct ShadowEvaluationCandidate {
  std::string id;
  std::string session_id;

  PreferenceTarget target;

  PreferenceDirection direction =
      PreferenceDirection::Avoid;

  double strength = 0.0;
  double confidence = 0.0;

  // Ob diese Beobachtung in persoenliches Lernen gespeichert
  // werden duerfte.
  bool may_store_personal_learning = false;

  // Ob die Hypothese offline gegen Routen/Kandidaten
  // ausgewertet werden darf.
  bool eligible_for_shadow = false;

  bool application_locked_by_user = false;

  // Harte Garantie dieses Datentyps:
  // Shadow-Auswertung greift niemals ins aktive Routing ein.
  bool production_application_allowed = false;

  double learning_factor = 0.0;

  std::vector<std::string> evidence_ids;
  std::vector<std::string> context_tags;

  std::string reason;
};

[[nodiscard]]
ShadowEvaluationCandidate
make_shadow_evaluation_candidate(
    const drive::DriveSession& session,
    const AiPolicy& policy,
    const PreferenceHypothesis& hypothesis);

}  // namespace routing::core::intelligence
