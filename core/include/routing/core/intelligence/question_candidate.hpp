#pragma once

#include <cstdint>
#include <string>
#include <vector>

#include "routing/core/drive/drive_evidence.hpp"
#include "routing/core/intelligence/ai_policy.hpp"

namespace routing::core::intelligence {

enum class QuestionKind : std::uint8_t {
  AlternativeReason = 0,
  DeviationReason,
};

struct QuestionCandidate {
  std::string id;
  std::string session_id;

  QuestionKind kind =
      QuestionKind::AlternativeReason;

  // Stabiler semantischer Key.
  // Eine lokale UI oder ein optionales LLM darf daraus spaeter
  // nutzerfreundlichen Text formulieren.
  std::string prompt_key;

  // 0..100. Hoeher = informativer.
  std::uint8_t priority = 0;

  // Harte Sicherheits-/UX-Regel:
  // Detailfragen werden erst nach der Fahrt gestellt.
  bool post_drive_only = true;

  std::string route_id;
  std::string alternative_route_id;
  std::string segment_id;

  std::vector<std::string> evidence_ids;
  std::vector<std::string> context_tags;
};

[[nodiscard]]
std::vector<QuestionCandidate>
select_question_candidates(
    const drive::DriveSession& session,
    const std::vector<drive::EvidenceRecord>& evidence,
    const AiPolicy& policy);

}  // namespace routing::core::intelligence
