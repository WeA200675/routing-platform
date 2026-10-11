#include "routing/core/drive/regression_candidate.hpp"

#include <algorithm>
#include <utility>

namespace routing::core::drive {

std::vector<RegressionCaseCandidate>
derive_regression_candidates(
    const DriveSession& session,
    const std::vector<EvidenceRecord>& evidence) {
  std::vector<RegressionCaseCandidate> result;

  std::uint64_t next_sequence = 1;

  const auto append =
      [&](RegressionCaseCandidate candidate) {
        candidate.id =
            session.header.session_id +
            ":regression:" +
            std::to_string(next_sequence++);

        candidate.session_id =
            session.header.session_id;

        candidate.request =
            session.request;

        candidate.versions =
            session.header.versions;

        candidate.selected_route =
            session.selected_route;

        candidate.requires_human_review = true;

        result.push_back(
            std::move(candidate));
      };

  for (const auto& item : evidence) {
    if (item.session_id !=
        session.header.session_id) {
      continue;
    }

    if (item.kind ==
            EvidenceKind::ExplicitFeedback &&
        item.polarity ==
            EvidencePolarity::Negative) {
      RegressionCaseCandidate candidate;

      candidate.kind =
          RegressionCandidateKind::SegmentComplaint;

      const std::uint8_t severity =
          item.feedback_severity.value_or(3);

      candidate.priority =
          static_cast<std::uint8_t>(
              std::min<int>(
                  100,
                  60 +
                  static_cast<int>(severity) * 8));

      candidate.evidence_ids.push_back(
          item.id);

      candidate.affected_segment_id =
          item.segment_id;

      candidate.description =
          "explicit_negative_feedback";

      append(std::move(candidate));
      continue;
    }

    if (item.kind ==
        EvidenceKind::AlternativeSelection) {
      RegressionCaseCandidate candidate;

      candidate.kind =
          RegressionCandidateKind::RoutePreference;

      candidate.priority = 75;

      candidate.evidence_ids.push_back(
          item.id);

      candidate.preferred_route_id =
          item.alternative_route_id;

      candidate.description =
          "explicit_alternative_selection";

      append(std::move(candidate));
      continue;
    }

    if (item.kind ==
            EvidenceKind::RouteDeviation &&
        item.confidence >= 0.80) {
      RegressionCaseCandidate candidate;

      candidate.kind =
          RegressionCandidateKind::DeviationCase;

      candidate.priority = 60;

      candidate.evidence_ids.push_back(
          item.id);

      candidate.affected_segment_id =
          item.segment_id;

      candidate.description =
          "high_confidence_route_deviation";

      append(std::move(candidate));
    }
  }

  return result;
}

}  // namespace routing::core::drive
