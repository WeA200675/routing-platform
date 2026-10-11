#include "routing/core/drive/drive_evidence.hpp"

#include <stdexcept>
#include <utility>

namespace routing::core::drive {

namespace {

EvidencePolarity polarity_from_feedback(
    const FeedbackSentiment sentiment) {
  switch (sentiment) {
    case FeedbackSentiment::Neutral:
      return EvidencePolarity::Neutral;

    case FeedbackSentiment::Positive:
      return EvidencePolarity::Positive;

    case FeedbackSentiment::Negative:
      return EvidencePolarity::Negative;
  }

  throw std::logic_error(
      "Unknown feedback sentiment.");
}

}  // namespace

std::vector<EvidenceRecord>
build_drive_evidence(
    const DriveSession& session) {
  std::vector<EvidenceRecord> result;

  std::uint64_t next_evidence_sequence = 1;

  const auto append =
      [&](EvidenceRecord evidence) {
        evidence.id =
            session.header.session_id +
            ":evidence:" +
            std::to_string(
                next_evidence_sequence++);

        evidence.session_id =
            session.header.session_id;

        result.push_back(
            std::move(evidence));
      };

  for (const auto& event : session.events) {
    switch (event.type) {
      case DriveEventType::FeedbackMarked: {
        if (!event.feedback.has_value()) {
          throw std::logic_error(
              "Recorded feedback event has no payload.");
        }

        EvidenceRecord evidence;
        evidence.source_event_id = event.id;

        evidence.kind =
            EvidenceKind::ExplicitFeedback;

        evidence.polarity =
            polarity_from_feedback(
                event.feedback->sentiment);

        // Explizites Nutzerfeedback wurde tatsächlich
        // abgegeben. Was es semantisch bedeutet, wird
        // erst später interpretiert.
        evidence.confidence = 1.0;

        evidence.route_id = event.route_id;
        evidence.segment_id = event.segment_id;

        evidence.feedback_reason =
            event.feedback->reason;

        evidence.feedback_severity =
            event.feedback->severity;

        evidence.detail =
            event.feedback->note;

        append(std::move(evidence));
        break;
      }

      case DriveEventType::RouteDeviationDetected: {
        if (!event.detector_confidence.has_value()) {
          throw std::logic_error(
              "Recorded deviation has no detector confidence.");
        }

        EvidenceRecord evidence;
        evidence.source_event_id = event.id;

        evidence.kind =
            EvidenceKind::RouteDeviation;

        // Abweichen bedeutet NICHT automatisch,
        // dass die vorgeschlagene Route unbeliebt war.
        evidence.polarity =
            EvidencePolarity::Neutral;

        evidence.confidence =
            *event.detector_confidence;

        evidence.route_id = event.route_id;
        evidence.segment_id = event.segment_id;

        evidence.detail =
            "route_deviation_detected";

        append(std::move(evidence));
        break;
      }

      case DriveEventType::AlternativeSelected: {
        EvidenceRecord evidence;
        evidence.source_event_id = event.id;

        evidence.kind =
            EvidenceKind::AlternativeSelection;

        evidence.polarity =
            EvidencePolarity::Positive;

        evidence.confidence = 1.0;

        evidence.route_id = event.route_id;

        evidence.alternative_route_id =
            event.alternative_route_id;

        evidence.detail =
            "alternative_selected";

        append(std::move(evidence));
        break;
      }

      case DriveEventType::RerouteApplied: {
        EvidenceRecord evidence;
        evidence.source_event_id = event.id;

        evidence.kind =
            EvidenceKind::Reroute;

        // Ein Reroute kann Verkehr, Sperrung,
        // Fahrfehler oder Präferenzgründe haben.
        evidence.polarity =
            EvidencePolarity::Neutral;

        evidence.confidence = 1.0;
        evidence.route_id = event.route_id;

        evidence.detail =
            "reroute_applied";

        append(std::move(evidence));
        break;
      }

      case DriveEventType::LocationObservation:
      case DriveEventType::RouteDeviationEnded:
      case DriveEventType::RerouteRequested:
        break;
    }
  }

  return result;
}

bool may_feed_personal_learning(
    const DriveSession& session) {
  return
      session.completed &&
      session.header.learning_disposition ==
          LearningDisposition::Eligible;
}

}  // namespace routing::core::drive
