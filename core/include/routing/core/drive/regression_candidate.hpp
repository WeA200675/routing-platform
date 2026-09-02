#pragma once

#include <cstdint>
#include <string>
#include <vector>

#include "routing/core/drive/drive_evidence.hpp"

namespace routing::core::drive {

enum class RegressionCandidateKind : std::uint8_t {
  SegmentComplaint = 0,
  RoutePreference,
  DeviationCase,
};

struct RegressionCaseCandidate {
  std::string id;
  std::string session_id;

  RegressionCandidateKind kind =
      RegressionCandidateKind::SegmentComplaint;

  // 0..100
  std::uint8_t priority = 50;

  // Ein Kandidat wird niemals automatisch zu
  // einer globalen Routingregel.
  bool requires_human_review = true;

  std::vector<std::string> evidence_ids;

  RouteRequestSnapshot request;
  VersionSnapshot versions;
  RouteSnapshot selected_route;

  std::string affected_segment_id;
  std::string preferred_route_id;

  std::string description;
};

[[nodiscard]] std::vector<RegressionCaseCandidate>
derive_regression_candidates(
    const DriveSession& session,
    const std::vector<EvidenceRecord>& evidence);

}  // namespace routing::core::drive
