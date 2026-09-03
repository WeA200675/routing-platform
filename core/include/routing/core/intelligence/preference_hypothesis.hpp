#pragma once

#include <cstdint>
#include <string>
#include <vector>

#include "routing/core/drive/drive_evidence.hpp"
#include "routing/core/rule.hpp"

namespace routing::core::intelligence {

enum class PreferenceDirection : std::uint8_t {
  Prefer = 0,
  Avoid,
};

enum class HypothesisOrigin : std::uint8_t {
  ExplicitFeedback = 0,
};

struct PreferenceTarget {
  Attribute attribute =
      Attribute::FunctionalRoadClass;

  // Stabiler semantischer Schluessel.
  // Keine persistierte Enum-Zahl und kein bedeutungsloser
  // universeller Skalar.
  std::string condition_key;
};

struct PreferenceHypothesis {
  std::string id;
  std::string session_id;

  PreferenceTarget target;

  PreferenceDirection direction =
      PreferenceDirection::Avoid;

  // Staerke der signalisierten Praeferenz: 0..1.
  double strength = 0.0;

  // Sicherheit unserer Interpretation: 0..1.
  double confidence = 0.0;

  HypothesisOrigin origin =
      HypothesisOrigin::ExplicitFeedback;

  std::vector<std::string> evidence_ids;
  std::vector<std::string> context_tags;

  std::string rationale;
};

[[nodiscard]]
std::vector<PreferenceHypothesis>
build_preference_hypotheses(
    const drive::DriveSession& session,
    const std::vector<drive::EvidenceRecord>& evidence);

}  // namespace routing::core::intelligence
