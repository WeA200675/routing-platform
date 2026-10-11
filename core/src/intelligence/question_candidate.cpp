#include "routing/core/intelligence/question_candidate.hpp"

#include <algorithm>
#include <cmath>
#include <cstddef>
#include <stdexcept>
#include <unordered_set>
#include <utility>

namespace routing::core::intelligence {

namespace {

bool autonomy_allows_questions(
    const AiAutonomyMode mode) {
  switch (mode) {
    case AiAutonomyMode::Disabled:
    case AiAutonomyMode::Observe:
      return false;

    case AiAutonomyMode::Ask:
    case AiAutonomyMode::Propose:
    case AiAutonomyMode::Shadow:
    case AiAutonomyMode::BoundedAutomatic:
      return true;
  }

  return false;
}

std::size_t effective_question_budget(
    const AiPolicy& policy) {
  if (!autonomy_allows_questions(policy.mode) ||
      policy.max_questions_per_drive == 0) {
    return 0;
  }

  const double intensity =
      policy.global_question_intensity.normalized();

  if (!std::isfinite(intensity) ||
      intensity < 0.0 ||
      intensity > 1.0) {
    throw std::invalid_argument(
        "Question intensity must normalize to 0..1.");
  }

  if (intensity <= 0.0) {
    return 0;
  }

  const double scaled =
      static_cast<double>(
          policy.max_questions_per_drive) *
      intensity;

  // Jede positive Intensitaet darf mindestens eine Frage
  // zulassen. Das harte Maximum bleibt erhalten.
  return std::min<std::size_t>(
      policy.max_questions_per_drive,
      std::max<std::size_t>(
          1,
          static_cast<std::size_t>(
              std::ceil(scaled))));
}

}  // namespace

std::vector<QuestionCandidate>
select_question_candidates(
    const drive::DriveSession& session,
    const std::vector<drive::EvidenceRecord>& evidence,
    const AiPolicy& policy) {
  std::vector<QuestionCandidate> result;

  // Keine Detailinterviews waehrend der Fahrt.
  if (!session.completed) {
    return result;
  }

  const std::size_t budget =
      effective_question_budget(policy);

  if (budget == 0) {
    return result;
  }

  std::unordered_set<std::string>
      explicitly_explained_segments;

  for (const auto& item : evidence) {
    if (item.session_id !=
        session.header.session_id) {
      continue;
    }

    if (item.kind ==
            drive::EvidenceKind::ExplicitFeedback &&
        !item.segment_id.empty()) {
      explicitly_explained_segments.insert(
          item.segment_id);
    }
  }

  std::unordered_set<std::string>
      seen_alternatives;

  std::unordered_set<std::string>
      seen_deviation_segments;

  constexpr double
      minimum_deviation_confidence = 0.80;

  for (const auto& item : evidence) {
    if (item.session_id !=
        session.header.session_id) {
      continue;
    }

    if (!std::isfinite(item.confidence) ||
        item.confidence < 0.0 ||
        item.confidence > 1.0) {
      throw std::invalid_argument(
          "Evidence confidence must be between 0 and 1.");
    }

    // Eine bewusste Alternativwahl ist ein sehr informationsreiches
    // Signal, aber noch KEINE automatisch interpretierte Praeferenz.
    if (item.kind ==
            drive::EvidenceKind::AlternativeSelection &&
        !item.alternative_route_id.empty() &&
        seen_alternatives.insert(
            item.alternative_route_id).second) {
      QuestionCandidate question;

      question.session_id =
          session.header.session_id;

      question.kind =
          QuestionKind::AlternativeReason;

      question.prompt_key =
          "why_alternative_selected";

      question.priority = 90;

      question.route_id =
          item.route_id;

      question.alternative_route_id =
          item.alternative_route_id;

      question.evidence_ids.push_back(
          item.id);

      question.context_tags =
          session.header.context_tags;

      result.push_back(
          std::move(question));

      continue;
    }

    // Auch eine RouteDeviation wird NICHT als negative
    // Praeferenz interpretiert.
    // Sie darf lediglich eine spaetere Frage ausloesen.
    if (item.kind ==
            drive::EvidenceKind::RouteDeviation &&
        item.confidence >=
            minimum_deviation_confidence &&
        !item.segment_id.empty() &&
        explicitly_explained_segments.count(
            item.segment_id) == 0 &&
        seen_deviation_segments.insert(
            item.segment_id).second) {
      QuestionCandidate question;

      question.session_id =
          session.header.session_id;

      question.kind =
          QuestionKind::DeviationReason;

      question.prompt_key =
          "why_route_deviation";

      question.priority = 70;

      question.route_id =
          item.route_id;

      question.segment_id =
          item.segment_id;

      question.evidence_ids.push_back(
          item.id);

      question.context_tags =
          session.header.context_tags;

      result.push_back(
          std::move(question));
    }
  }

  std::stable_sort(
      result.begin(),
      result.end(),
      [](const QuestionCandidate& left,
         const QuestionCandidate& right) {
        return left.priority >
            right.priority;
      });

  if (result.size() > budget) {
    result.resize(budget);
  }

  for (std::size_t i = 0;
       i < result.size();
       ++i) {
    result[i].id =
        session.header.session_id +
        ":question:" +
        std::to_string(i + 1);
  }

  return result;
}

}  // namespace routing::core::intelligence
