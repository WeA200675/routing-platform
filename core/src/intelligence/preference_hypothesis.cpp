#include "routing/core/intelligence/preference_hypothesis.hpp"

#include <cmath>
#include <optional>
#include <stdexcept>
#include <utility>

namespace routing::core::intelligence {

namespace {

std::optional<PreferenceTarget>
target_from_feedback_reason(
    const drive::FeedbackReason reason) {
  PreferenceTarget target;

  switch (reason) {
    case drive::FeedbackReason::ResidentialShortcut:
      target.attribute =
          Attribute::FunctionalRoadClass;

      target.condition_key =
          "functional_road_class=residential";

      return target;

    case drive::FeedbackReason::Speed30Zone:
      target.attribute =
          Attribute::SpeedLimitKmh;

      target.condition_key =
          "speed_limit_kmh<=30";

      return target;

    case drive::FeedbackReason::CurvyRoad:
      target.attribute =
          Attribute::CurvatureScore;

      target.condition_key =
          "curvature=high";

      return target;

    case drive::FeedbackReason::SteepRoad:
      target.attribute =
          Attribute::GradientAbsPct;

      target.condition_key =
          "gradient=steep";

      return target;

    // Diese Rueckmeldungen bleiben Evidence.
    // Solange unser Routingmodell dafuer kein praezises Attribut
    // besitzt, erfinden wir keine kuenstliche Zuordnung.
    case drive::FeedbackReason::Unspecified:
    case drive::FeedbackReason::RouteWasGood:
    case drive::FeedbackReason::PreferredAlternative:
    case drive::FeedbackReason::TooManyTurns:
    case drive::FeedbackReason::NarrowRoad:
    case drive::FeedbackReason::PoorRoadQuality:
    case drive::FeedbackReason::Traffic:
    case drive::FeedbackReason::UnsafeFeeling:
    case drive::FeedbackReason::Other:
      return std::nullopt;
  }

  throw std::logic_error(
      "Unknown feedback reason.");
}

std::optional<PreferenceDirection>
direction_from_polarity(
    const drive::EvidencePolarity polarity) {
  switch (polarity) {
    case drive::EvidencePolarity::Positive:
      return PreferenceDirection::Prefer;

    case drive::EvidencePolarity::Negative:
      return PreferenceDirection::Avoid;

    case drive::EvidencePolarity::Neutral:
      return std::nullopt;
  }

  throw std::logic_error(
      "Unknown evidence polarity.");
}

}  // namespace

std::vector<PreferenceHypothesis>
build_preference_hypotheses(
    const drive::DriveSession& session,
    const std::vector<drive::EvidenceRecord>& evidence) {
  std::vector<PreferenceHypothesis> result;

  std::uint64_t sequence = 1;

  for (const auto& item : evidence) {
    if (item.session_id !=
        session.header.session_id) {
      continue;
    }

    // Sehr wichtiger Contract:
    // Abweichung/Reroute allein ist KEINE negative Praeferenz.
    if (item.kind !=
        drive::EvidenceKind::ExplicitFeedback) {
      continue;
    }

    if (!item.feedback_reason.has_value()) {
      throw std::invalid_argument(
          "Explicit feedback evidence requires feedback reason.");
    }

    const auto direction =
        direction_from_polarity(
            item.polarity);

    if (!direction.has_value()) {
      continue;
    }

    const auto target =
        target_from_feedback_reason(
            *item.feedback_reason);

    if (!target.has_value()) {
      continue;
    }

    if (!std::isfinite(item.confidence) ||
        item.confidence < 0.0 ||
        item.confidence > 1.0) {
      throw std::invalid_argument(
          "Evidence confidence must be between 0 and 1.");
    }

    PreferenceHypothesis hypothesis;

    hypothesis.id =
        session.header.session_id +
        ":hypothesis:" +
        std::to_string(sequence++);

    hypothesis.session_id =
        session.header.session_id;

    hypothesis.target =
        *target;

    hypothesis.direction =
        *direction;

    // Explizites Feedback ist derzeit ein volles Signal.
    // Eine feinere Severity-Gewichtung kann spaeter erfolgen,
    // ohne das semantische Ziel zu verlieren.
    hypothesis.strength = 1.0;

    hypothesis.confidence =
        item.confidence;

    hypothesis.origin =
        HypothesisOrigin::ExplicitFeedback;

    hypothesis.evidence_ids.push_back(
        item.id);

    hypothesis.context_tags =
        session.header.context_tags;

    hypothesis.rationale =
        "explicit_feedback";

    result.push_back(
        std::move(hypothesis));
  }

  return result;
}

}  // namespace routing::core::intelligence
