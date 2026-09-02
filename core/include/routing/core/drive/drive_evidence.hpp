#pragma once

#include <cstdint>
#include <optional>
#include <string>
#include <vector>

#include "routing/core/drive/drive_session.hpp"

namespace routing::core::drive {

enum class EvidenceKind : std::uint8_t {
  ExplicitFeedback = 0,
  RouteDeviation,
  AlternativeSelection,
  Reroute,
};

enum class EvidencePolarity : std::uint8_t {
  Neutral = 0,
  Positive,
  Negative,
};

struct EvidenceRecord {
  std::string id;

  std::string session_id;
  std::string source_event_id;

  EvidenceKind kind =
      EvidenceKind::ExplicitFeedback;

  EvidencePolarity polarity =
      EvidencePolarity::Neutral;

  // Confidence, dass das zugrunde liegende Ereignis
  // korrekt beobachtet wurde.
  // Dies ist NICHT automatisch Preference-Confidence.
  double confidence = 0.0;

  std::string route_id;
  std::string segment_id;
  std::string alternative_route_id;

  std::optional<FeedbackReason> feedback_reason;
  std::optional<std::uint8_t> feedback_severity;

  std::string detail;
};

[[nodiscard]] std::vector<EvidenceRecord>
build_drive_evidence(
    const DriveSession& session);

[[nodiscard]] bool
may_feed_personal_learning(
    const DriveSession& session);

}  // namespace routing::core::drive
