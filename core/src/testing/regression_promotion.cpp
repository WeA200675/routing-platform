#include "routing/core/testing/regression_promotion.hpp"
#include "routing/core/drive/routing_snapshot.hpp"

#include <algorithm>
#include <sstream>
#include <stdexcept>
#include <string>
#include <utility>

namespace routing::core::testing {

namespace {

struct FeedbackInterpretation {
  RegressionPromotionReadiness readiness =
      RegressionPromotionReadiness::NeedsReview;

  std::string issue_key;
  std::string title;
  std::string summary;

  std::optional<RegressionMetricSuggestion>
      metric;
};

RoutingScenario make_scenario_seed(
    const drive::DriveSession& session,
    const std::string& scenario_id,
    const std::string& title) {
  RoutingScenario scenario;

  scenario.id = scenario_id;
  scenario.title = title;

  scenario.request.origin = {
      session.request.origin.latitude,
      session.request.origin.longitude,
  };

  scenario.request.destination = {
      session.request.destination.latitude,
      session.request.destination.longitude,
  };

  for (const auto& via :
       session.request.via_points) {
    scenario.request.via_points.push_back({
        via.latitude,
        via.longitude,
    });
  }

  // Restore the persisted stable family identifier when this build
  // understands it. Unknown future identifiers remain explicitly
  // incomplete in the proposal rather than being claimed as exact replay.
  if (const auto family =
          drive::candidate_family_from_id(
              session.request.candidate_family)) {
    scenario.request.family = *family;
  } else {
    scenario.request.family =
        CandidateFamily::ProfileOptimal;
  }

  scenario.request.alternatives =
      session.request.alternatives_requested;

  scenario.request.costing_profile =
      session.request.costing_profile;

  if (session.replay_semantics.has_value()) {
    scenario.vehicle =
        session.replay_semantics->vehicle;

    scenario.rules =
        session.replay_semantics->rules;

    scenario.context =
        session.replay_semantics->context;
  }

  return scenario;
}

RegressionMetricSuggestion lower_metric(
    const RouteMetric metric,
    std::string reason_key) {
  RegressionMetricSuggestion suggestion;

  suggestion.metric = metric;

  suggestion.direction =
      MetricImprovementDirection::LowerIsBetter;

  suggestion.reason_key =
      std::move(reason_key);

  return suggestion;
}

FeedbackInterpretation interpret_feedback(
    const drive::EvidenceRecord& evidence) {
  using drive::FeedbackReason;

  FeedbackInterpretation interpretation;

  const auto reason =
      evidence.feedback_reason.value_or(
          FeedbackReason::Unspecified);

  switch (reason) {
    case FeedbackReason::ResidentialShortcut:
      interpretation.readiness =
          RegressionPromotionReadiness::MetricSuggested;

      interpretation.issue_key =
          "routing.feedback.residential-shortcut";

      interpretation.title =
          "Avoid reported residential shortcut";

      interpretation.summary =
          "Explicit negative feedback reported a residential shortcut.";

      interpretation.metric =
          lower_metric(
              RouteMetric::MinorRoadShare,
              "promotion.feedback.residential-shortcut");

      return interpretation;

    case FeedbackReason::Speed30Zone:
      interpretation.readiness =
          RegressionPromotionReadiness::MetricSuggested;

      interpretation.issue_key =
          "routing.feedback.speed-30-zone";

      interpretation.title =
          "Reduce reported 30 km/h routing";

      interpretation.summary =
          "Explicit negative feedback reported an undesirable 30 km/h zone.";

      interpretation.metric =
          lower_metric(
              RouteMetric::Speed30OrLowerShare,
              "promotion.feedback.speed-30-zone");

      return interpretation;

    case FeedbackReason::CurvyRoad:
      interpretation.readiness =
          RegressionPromotionReadiness::MetricSuggested;

      interpretation.issue_key =
          "routing.feedback.curvy-road";

      interpretation.title =
          "Reduce reported curvy-road routing";

      interpretation.summary =
          "Explicit negative feedback reported an undesirable curvy road.";

      interpretation.metric =
          lower_metric(
              RouteMetric::StronglyCurvyShare,
              "promotion.feedback.curvy-road");

      return interpretation;

    case FeedbackReason::SteepRoad:
      interpretation.readiness =
          RegressionPromotionReadiness::MetricSuggested;

      interpretation.issue_key =
          "routing.feedback.steep-road";

      interpretation.title =
          "Reduce reported steep-road routing";

      interpretation.summary =
          "Explicit negative feedback reported an undesirable steep road.";

      interpretation.metric =
          lower_metric(
              RouteMetric::SteepGradientShare,
              "promotion.feedback.steep-road");

      return interpretation;

    case FeedbackReason::PreferredAlternative:
      interpretation.readiness =
          evidence.alternative_route_id.empty()
              ? RegressionPromotionReadiness::NeedsReview
              : RegressionPromotionReadiness::AlternativeSuggested;

      interpretation.issue_key =
          "routing.feedback.preferred-alternative";

      interpretation.title =
          "Review preferred route alternative";

      interpretation.summary =
          "Explicit feedback refers to a preferred alternative. "
          "This is evidence of this choice, not a global preference.";

      return interpretation;

    case FeedbackReason::TooManyTurns:
      interpretation.issue_key =
          "routing.feedback.too-many-turns";

      interpretation.title =
          "Review excessive-turn routing";

      interpretation.summary =
          "Current route metrics do not yet provide a turn-density "
          "regression metric.";

      return interpretation;

    case FeedbackReason::NarrowRoad:
      interpretation.issue_key =
          "routing.feedback.narrow-road";

      interpretation.title =
          "Review narrow-road routing";

      interpretation.summary =
          "Current route metrics do not yet expose road-width quality.";

      return interpretation;

    case FeedbackReason::PoorRoadQuality:
      interpretation.issue_key =
          "routing.feedback.poor-road-quality";

      interpretation.title =
          "Review poor-road-quality routing";

      interpretation.summary =
          "Current route metrics do not yet expose a complete "
          "road-quality regression metric.";

      return interpretation;

    case FeedbackReason::Traffic:
      interpretation.issue_key =
          "routing.feedback.traffic";

      interpretation.title =
          "Review traffic-related routing";

      interpretation.summary =
          "Traffic feedback requires the applicable traffic-data snapshot "
          "before it can become a reproducible expectation.";

      return interpretation;

    case FeedbackReason::UnsafeFeeling:
      interpretation.issue_key =
          "routing.feedback.unsafe-feeling";

      interpretation.title =
          "Review reported unsafe-feeling route";

      interpretation.summary =
          "Safety-related subjective feedback requires explicit human "
          "review and must not be reduced to an inferred preference.";

      return interpretation;

    case FeedbackReason::Other:
    case FeedbackReason::Unspecified:
      interpretation.issue_key =
          "routing.feedback.review";

      interpretation.title =
          "Review explicit route feedback";

      interpretation.summary =
          "Explicit negative feedback requires reviewer interpretation.";

      return interpretation;

    case FeedbackReason::RouteWasGood:
      interpretation.issue_key =
          "routing.feedback.inconsistent-negative-good";

      interpretation.title =
          "Review inconsistent feedback";

      interpretation.summary =
          "Negative polarity combined with RouteWasGood requires review.";

      return interpretation;
  }

  return interpretation;
}

std::string evidence_identity(
    const drive::EvidenceRecord& evidence) {
  if (!evidence.id.empty()) {
    return evidence.id;
  }

  if (!evidence.source_event_id.empty()) {
    return evidence.source_event_id;
  }

  throw std::invalid_argument(
      "Drive evidence requires an id or source event id.");
}

RegressionPromotionProposal base_proposal(
    const drive::DriveSession& session,
    const drive::EvidenceRecord& evidence,
    const std::string& title) {
  RegressionPromotionProposal proposal;

  const auto identity =
      evidence_identity(
          evidence);

  proposal.proposal_id =
      "promotion:" + identity;

  proposal.session_id =
      session.header.session_id;

  proposal.source_event_id =
      evidence.source_event_id;

  proposal.evidence_kind =
      evidence.kind;

  proposal.evidence_ids.push_back(
      identity);

  proposal.observed_route_id =
      evidence.route_id;

  proposal.preferred_alternative_route_id =
      evidence.alternative_route_id;

  proposal.source_versions =
      session.header.versions;

  proposal.context_tags =
      session.header.context_tags;

  proposal.scenario_seed =
      make_scenario_seed(
          session,
          proposal.proposal_id,
          title);

  proposal.runtime_semantics_complete =
      true;

  if (!session.replay_semantics.has_value()) {
    // Legacy/imported sessions remain usable as evidence, but the
    // default objects inside RoutingScenario must never be presented
    // as though they were the historical runtime semantics.
    proposal.runtime_semantics_complete =
        false;

    proposal.missing_runtime_inputs = {
        "vehicle_profile_snapshot",
        "rule_set_snapshot",
        "routing_context_snapshot",
    };
  } else {
    try {
      drive::validate_replay_semantics_snapshot(
          *session.replay_semantics);
    } catch (const std::exception&) {
      proposal.runtime_semantics_complete =
          false;

      proposal.missing_runtime_inputs.push_back(
          "invalid_replay_semantics_snapshot");
    }

    // Version references are provenance. When they contradict the
    // value snapshot, exact replay must not be claimed silently.
    if (!session.header.versions.profile_id.empty() &&
        session.header.versions.profile_id !=
            session.replay_semantics->vehicle.id) {
      proposal.runtime_semantics_complete =
          false;

      proposal.missing_runtime_inputs.push_back(
          "vehicle_profile_id_mismatch");
    }

    if (!session.header.versions.rules_version.empty() &&
        session.header.versions.rules_version !=
            session.replay_semantics->rules.version) {
      proposal.runtime_semantics_complete =
          false;

      proposal.missing_runtime_inputs.push_back(
          "rule_set_version_mismatch");
    }
  }

  if (!drive::candidate_family_from_id(
           session.request.candidate_family)
           .has_value()) {
    proposal.runtime_semantics_complete =
        false;

    proposal.missing_runtime_inputs.push_back(
        "candidate_family_semantics");
  }

  proposal.human_approval_required =
      true;

  return proposal;
}

bool has_matching_metric_expectation(
    const RegressionPromotionProposal& proposal,
    const RoutingScenario& scenario) {
  if (!proposal.metric_suggestion.has_value()) {
    return true;
  }

  const auto& suggestion =
      *proposal.metric_suggestion;

  for (const auto& expectation :
       scenario.expectations
           .selected_route_metrics) {
    if (expectation.metric !=
        suggestion.metric) {
      continue;
    }

    if (suggestion.direction ==
            MetricImprovementDirection::LowerIsBetter &&
        expectation.maximum_value.has_value()) {
      return true;
    }

    if (suggestion.direction ==
            MetricImprovementDirection::HigherIsBetter &&
        expectation.minimum_value.has_value()) {
      return true;
    }
  }

  return false;
}

std::string promotion_provenance_note(
    const RegressionPromotionProposal& proposal) {
  std::ostringstream note;

  note
      << "Promoted from "
      << proposal.proposal_id
      << ". Evidence: ";

  for (std::size_t index = 0;
       index < proposal.evidence_ids.size();
       ++index) {
    if (index != 0) {
      note << ",";
    }

    note
        << proposal.evidence_ids[index];
  }

  if (!proposal.context_tags.empty()) {
    note << ". Context tags: ";

    for (std::size_t index = 0;
         index < proposal.context_tags.size();
         ++index) {
      if (index != 0) {
        note << ",";
      }

      note
          << proposal.context_tags[index];
    }
  }

  return note.str();
}

}  // namespace

std::string_view regression_promotion_readiness_key(
    const RegressionPromotionReadiness readiness) {
  switch (readiness) {
    case RegressionPromotionReadiness::MetricSuggested:
      return "metric-suggested";

    case RegressionPromotionReadiness::AlternativeSuggested:
      return "alternative-suggested";

    case RegressionPromotionReadiness::DiagnosticOnly:
      return "diagnostic-only";

    case RegressionPromotionReadiness::NeedsReview:
      return "needs-review";
  }

  throw std::invalid_argument(
      "Unknown regression promotion readiness.");
}

std::string_view metric_improvement_direction_key(
    const MetricImprovementDirection direction) {
  switch (direction) {
    case MetricImprovementDirection::LowerIsBetter:
      return "lower-is-better";

    case MetricImprovementDirection::HigherIsBetter:
      return "higher-is-better";
  }

  throw std::invalid_argument(
      "Unknown metric improvement direction.");
}

std::vector<RegressionPromotionProposal>
build_regression_promotion_proposals(
    const drive::DriveSession& session) {
  if (session.header.session_id.empty()) {
    throw std::invalid_argument(
        "Regression promotion requires a DriveSession session_id.");
  }

  const auto evidence =
      drive::build_drive_evidence(
          session);

  std::vector<RegressionPromotionProposal>
      proposals;

  for (const auto& item :
       evidence) {
    switch (item.kind) {
      case drive::EvidenceKind::ExplicitFeedback: {
        // Positive feedback is useful learning evidence but is not a
        // "bad route" regression proposal.
        if (item.polarity !=
            drive::EvidencePolarity::Negative) {
          continue;
        }

        const auto interpretation =
            interpret_feedback(
                item);

        auto proposal =
            base_proposal(
                session,
                item,
                interpretation.title);

        proposal.readiness =
            interpretation.readiness;

        proposal.issue_key =
            interpretation.issue_key;

        proposal.title =
            interpretation.title;

        proposal.summary =
            interpretation.summary;

        proposal.metric_suggestion =
            interpretation.metric;

        proposals.push_back(
            std::move(proposal));

        break;
      }

      case drive::EvidenceKind::AlternativeSelection: {
        auto proposal =
            base_proposal(
                session,
                item,
                "Review selected route alternative");

        proposal.readiness =
            RegressionPromotionReadiness::
                AlternativeSuggested;

        proposal.issue_key =
            "routing.drive.alternative-selected";

        proposal.title =
            "Review selected route alternative";

        proposal.summary =
            "A route alternative was selected. "
            "This records a concrete choice but does not infer "
            "a global routing preference.";

        proposals.push_back(
            std::move(proposal));

        break;
      }

      case drive::EvidenceKind::RouteDeviation: {
        auto proposal =
            base_proposal(
                session,
                item,
                "Review route deviation");

        proposal.readiness =
            RegressionPromotionReadiness::
                DiagnosticOnly;

        proposal.issue_key =
            "routing.drive.route-deviation";

        proposal.title =
            "Review route deviation";

        proposal.summary =
            "A route deviation was observed. "
            "Deviation alone is not interpreted as a preference.";

        proposals.push_back(
            std::move(proposal));

        break;
      }

      case drive::EvidenceKind::Reroute: {
        auto proposal =
            base_proposal(
                session,
                item,
                "Review reroute event");

        proposal.readiness =
            RegressionPromotionReadiness::
                DiagnosticOnly;

        proposal.issue_key =
            "routing.drive.reroute";

        proposal.title =
            "Review reroute event";

        proposal.summary =
            "A reroute occurred. Reroute alone is not interpreted "
            "as a routing preference.";

        proposals.push_back(
            std::move(proposal));

        break;
      }
    }
  }

  return proposals;
}

std::optional<RoutingRegressionCase>
approve_regression_promotion(
    const RegressionPromotionProposal& proposal,
    const RegressionPromotionApproval& approval) {
  if (!approval.approved) {
    return std::nullopt;
  }

  if (!proposal.human_approval_required) {
    throw std::logic_error(
        "Promotion contract unexpectedly allows non-human approval.");
  }

  if (!approval.confirmed_runtime_semantics) {
    throw std::invalid_argument(
        "Promotion approval requires confirmed runtime semantics.");
  }

  if (!approval
           .confirmed_expectation_captures_issue) {
    throw std::invalid_argument(
        "Promotion approval requires confirmation that the "
        "scenario expectation captures the reported issue.");
  }

  if (approval.case_id.empty()) {
    throw std::invalid_argument(
        "Promotion approval requires a stable case_id.");
  }

  if (approval.title.empty()) {
    throw std::invalid_argument(
        "Promotion approval requires a title.");
  }

  if (approval.disposition ==
          RegressionDisposition::Gating &&
      !approval.allow_gating) {
    throw std::invalid_argument(
        "Gating promotion requires explicit allow_gating approval.");
  }

  if (approval.reviewed_scenario.id !=
      approval.case_id) {
    throw std::invalid_argument(
        "Reviewed scenario.id must match approved case_id.");
  }

  if (!has_matching_metric_expectation(
          proposal,
          approval.reviewed_scenario)) {
    throw std::invalid_argument(
        "Reviewed scenario must contain a concrete expectation "
        "for the suggested route metric and direction.");
  }

  RoutingRegressionCase regression_case;

  regression_case.schema_version = 1;
  regression_case.case_id =
      approval.case_id;

  regression_case.case_version = 1;
  regression_case.title =
      approval.title;

  regression_case.issue_key =
      approval.issue_key.empty()
          ? proposal.issue_key
          : approval.issue_key;

  regression_case.disposition =
      approval.disposition;

  regression_case.provenance.source =
      RegressionCaseSource::DriveSession;

  regression_case.provenance.source_ref =
      proposal.session_id;

  regression_case.provenance.note =
      promotion_provenance_note(
          proposal);

  regression_case.scenario =
      approval.reviewed_scenario;

  validate_regression_case(
      regression_case);

  return regression_case;
}

}  // namespace routing::core::testing
