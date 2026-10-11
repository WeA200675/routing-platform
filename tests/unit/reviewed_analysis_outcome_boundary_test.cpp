#include <cassert>
#include <iostream>
#include <stdexcept>
#include <string>

#include "routing/core/intelligence/reviewed_analysis_outcome.hpp"
#include "routing/core/intelligence/reviewed_analysis_outcome_report.hpp"

namespace {

routing::core::intelligence::ClusterProblemAnalysisResult
make_analysis(
    const routing::core::intelligence::
        ClusterProblemDomain domain =
            routing::core::intelligence::
                ClusterProblemDomain::Unknown) {
  using namespace routing::core::diagnostics;
  using namespace routing::core::intelligence;

  ClusterProblemAnalysisResult analysis;

  analysis.analysis_id =
      "cluster-analysis-v1|cluster:personal|revision=3";

  analysis.job_id =
      "cluster-problem-v1|cluster:personal";

  analysis.cluster_key =
      "cluster:personal";

  analysis.context_key =
      "personal:context:test";

  analysis.data_scope_key =
      "personal";

  analysis.diagnostic_code =
      "DATA_URBAN_POSITIVE_SIGNAL_ABSENT";

  analysis.evidence_revision =
      3;

  analysis.observed_cluster_revision =
      3;

  analysis.status =
      ClusterProblemAnalysisStatus::Completed;

  analysis.domain =
      domain;

  analysis.severity =
      DiagnosticSeverity::Info;

  analysis.next_actions = {
      ClusterProblemNextAction::CollectAdditionalContext,
  };

  return analysis;
}


routing::core::intelligence::ClusterProblemReviewRecord
make_review(
    const routing::core::intelligence::
        ClusterProblemAnalysisResult& analysis,
    const routing::core::intelligence::
        ClusterProblemReviewDecision decision,
    const std::string& review_id) {
  using namespace routing::core::diagnostics;
  using namespace routing::core::intelligence;

  ClusterProblemReviewRecord review;

  review.review_id =
      review_id;

  review.reviewer_ref =
      "tester:alpha";

  review.analysis_id =
      analysis.analysis_id;

  review.job_id =
      analysis.job_id;

  review.cluster_key =
      analysis.cluster_key;

  review.context_key =
      analysis.context_key;

  review.data_scope_key =
      analysis.data_scope_key;

  review.diagnostic_code =
      analysis.diagnostic_code;

  review.analysis_evidence_revision =
      analysis.evidence_revision;

  review.cluster_revision_at_review =
      analysis.evidence_revision;

  review.analysis_status =
      analysis.status;

  review.decision =
      decision;

  review.prior_state =
      InvestigationState::Observed;

  if (decision ==
          ClusterProblemReviewDecision::Acknowledge ||
      decision ==
          ClusterProblemReviewDecision::NeedsMoreEvidence) {
    review.resulting_state =
        InvestigationState::Investigating;
  } else if (decision ==
             ClusterProblemReviewDecision::Resolve) {
    review.resulting_state =
        InvestigationState::Resolved;
  } else if (decision ==
             ClusterProblemReviewDecision::Dismiss) {
    review.resulting_state =
        InvestigationState::Dismissed;
  } else {
    review.resulting_state =
        InvestigationState::Investigating;
  }

  review.rationale =
      "Explicit review decision.";

  return review;
}

}  // namespace


int main() {
  using namespace routing::core::intelligence;

  const auto analysis =
      make_analysis();

  ReviewedAnalysisOutcomeWorkflow workflow;


  // -------------------------------------------------------------
  // NEEDS MORE EVIDENCE -> TESTER QUESTION PROPOSAL
  // -------------------------------------------------------------

  const auto needs_more =
      make_review(
          analysis,
          ClusterProblemReviewDecision::
              NeedsMoreEvidence,
          "review:needs-more");

  ReviewedAnalysisOutcomeRequest question_request;

  question_request.outcome_id =
      "outcome:question:1";

  question_request.reviewer_ref =
      "tester:beta";

  question_request.kind =
      ReviewedAnalysisOutcomeKind::
          TesterQuestionProposal;

  question_request.semantic_key =
      "diagnostic.question.additional_context";

  question_request.rationale =
      "Ask a tester for additional context after the drive.";


  const auto question_result =
      workflow.apply(
          analysis,
          needs_more,
          question_request);

  assert(
      question_result.record.
          tester_question_proposal.has_value());

  const auto& question =
      *question_result.record.
          tester_question_proposal;

  assert(
      question.data_scope_key ==
      "personal");

  assert(
      question.prompt_key ==
      "diagnostic.question.additional_context");

  assert(
      question.post_drive_only);

  assert(
      !question.automatic_presentation_allowed);

  assert(
      !question.question_candidate_created);

  assert(
      !question.answer_application_allowed);

  assert(
      !question.production_application_allowed);

  assert(
      !question.evidence_scope_promotion_allowed);


  // A missing-evidence review cannot directly propose a hypothesis.
  auto invalid_hypothesis_request =
      question_request;

  invalid_hypothesis_request.outcome_id =
      "outcome:hypothesis:invalid";

  invalid_hypothesis_request.kind =
      ReviewedAnalysisOutcomeKind::
          HypothesisProposal;

  invalid_hypothesis_request.semantic_key =
      "hypothesis:invalid";

  bool needs_more_hypothesis_rejected =
      false;

  try {
    (void)workflow.apply(
        analysis,
        needs_more,
        invalid_hypothesis_request);
  } catch (const std::logic_error&) {
    needs_more_hypothesis_rejected =
        true;
  }

  assert(
      needs_more_hypothesis_rejected);


  // -------------------------------------------------------------
  // ACKNOWLEDGE -> EXPLICIT HYPOTHESIS PROPOSAL
  // -------------------------------------------------------------

  const auto acknowledged =
      make_review(
          analysis,
          ClusterProblemReviewDecision::Acknowledge,
          "review:ack");

  ReviewedAnalysisOutcomeRequest hypothesis_request;

  hypothesis_request.outcome_id =
      "outcome:hypothesis:1";

  hypothesis_request.reviewer_ref =
      "reviewer:beta";

  hypothesis_request.kind =
      ReviewedAnalysisOutcomeKind::
          HypothesisProposal;

  hypothesis_request.semantic_key =
      "hypothesis.data.urban_signal_source_gap";

  hypothesis_request.rationale =
      "Explicitly propose a problem hypothesis for later review.";


  const auto hypothesis_result =
      workflow.apply(
          analysis,
          acknowledged,
          hypothesis_request);

  assert(
      hypothesis_result.record.
          hypothesis_proposal.has_value());

  const auto& hypothesis =
      *hypothesis_result.record.
          hypothesis_proposal;

  assert(
      hypothesis.data_scope_key ==
      "personal");

  assert(
      hypothesis.hypothesis_key ==
      "hypothesis.data.urban_signal_source_gap");

  assert(
      hypothesis.explicit_conversion_required);

  assert(
      !hypothesis.preference_hypothesis_created);

  assert(
      !hypothesis.learning_gate_invoked);

  assert(
      !hypothesis.shadow_evaluation_created);

  assert(
      !hypothesis.production_application_allowed);

  assert(
      !hypothesis.evidence_scope_promotion_allowed);


  // -------------------------------------------------------------
  // TERMINAL / REFRESH REVIEWS CANNOT CREATE OUTCOMES
  // -------------------------------------------------------------

  const auto resolved =
      make_review(
          analysis,
          ClusterProblemReviewDecision::Resolve,
          "review:resolved");

  bool resolved_rejected =
      false;

  try {
    (void)workflow.apply(
        analysis,
        resolved,
        hypothesis_request);
  } catch (const std::logic_error&) {
    resolved_rejected =
        true;
  }

  assert(
      resolved_rejected);


  const auto dismissed =
      make_review(
          analysis,
          ClusterProblemReviewDecision::Dismiss,
          "review:dismissed");

  bool dismissed_rejected =
      false;

  try {
    (void)workflow.apply(
        analysis,
        dismissed,
        hypothesis_request);
  } catch (const std::logic_error&) {
    dismissed_rejected =
        true;
  }

  assert(
      dismissed_rejected);


  auto refresh =
      make_review(
          analysis,
          ClusterProblemReviewDecision::RefreshAnalysis,
          "review:refresh");

  refresh.refresh_job_requested =
      true;

  bool refresh_rejected =
      false;

  try {
    (void)workflow.apply(
        analysis,
        refresh,
        hypothesis_request);
  } catch (const std::logic_error&) {
    refresh_rejected =
        true;
  }

  assert(
      refresh_rejected);


  const std::string report =
      format_reviewed_analysis_outcome_report(
          hypothesis_result.record);

  assert(
      report.find(
          "HYPOTHESIS PROPOSAL") !=
      std::string::npos);

  assert(
      report.find(
          "PreferenceHypothesis created: no") !=
      std::string::npos);

  assert(
      report.find(
          "LearningGate invoked: no") !=
      std::string::npos);

  assert(
      report.find(
          "production application allowed: no") !=
      std::string::npos);


  std::cout
      << "Reviewed analysis outcome boundary tests passed\n";

  return 0;
}
