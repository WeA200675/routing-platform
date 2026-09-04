#include <cassert>
#include <iostream>
#include <stdexcept>
#include <string>

#include "routing/core/intelligence/proposal_approval.hpp"
#include "routing/core/intelligence/proposal_approval_report.hpp"

namespace {

routing::core::intelligence::ReviewedAnalysisOutcomeRecord
make_question_outcome(
    const std::string& outcome_id) {
  using namespace routing::core::intelligence;

  ReviewedAnalysisOutcomeRecord outcome;

  outcome.outcome_id =
      outcome_id;

  outcome.source_review_id =
      "review:question:" +
      outcome_id;

  outcome.source_analysis_id =
      "analysis:question:" +
      outcome_id;

  outcome.review_reviewer_ref =
      "tester:alpha";

  outcome.outcome_reviewer_ref =
      "tester:beta";

  outcome.cluster_key =
      "cluster:question:" +
      outcome_id;

  outcome.context_key =
      "personal:question-context";

  outcome.data_scope_key =
      "personal";

  outcome.diagnostic_code =
      "DATA_URBAN_POSITIVE_SIGNAL_ABSENT";

  outcome.evidence_revision =
      3;

  outcome.kind =
      ReviewedAnalysisOutcomeKind::
          TesterQuestionProposal;

  outcome.semantic_key =
      "diagnostic.question.additional_context";

  outcome.rationale =
      "Gather additional tester context.";


  TesterQuestionProposal proposal;

  proposal.id =
      "tester-question-proposal-v1|" +
      outcome_id;

  proposal.source_review_id =
      outcome.source_review_id;

  proposal.source_analysis_id =
      outcome.source_analysis_id;

  proposal.cluster_key =
      outcome.cluster_key;

  proposal.context_key =
      outcome.context_key;

  proposal.data_scope_key =
      outcome.data_scope_key;

  proposal.diagnostic_code =
      outcome.diagnostic_code;

  proposal.evidence_revision =
      outcome.evidence_revision;

  proposal.prompt_key =
      outcome.semantic_key;

  proposal.reviewer_ref =
      outcome.outcome_reviewer_ref;

  proposal.rationale =
      outcome.rationale;

  outcome.tester_question_proposal =
      proposal;

  return outcome;
}


routing::core::intelligence::ReviewedAnalysisOutcomeRecord
make_hypothesis_outcome(
    const std::string& outcome_id) {
  using namespace routing::core::intelligence;

  ReviewedAnalysisOutcomeRecord outcome;

  outcome.outcome_id =
      outcome_id;

  outcome.source_review_id =
      "review:hypothesis:" +
      outcome_id;

  outcome.source_analysis_id =
      "analysis:hypothesis:" +
      outcome_id;

  outcome.review_reviewer_ref =
      "reviewer:alpha";

  outcome.outcome_reviewer_ref =
      "reviewer:beta";

  outcome.cluster_key =
      "cluster:hypothesis:" +
      outcome_id;

  outcome.context_key =
      "personal:hypothesis-context";

  outcome.data_scope_key =
      "personal";

  outcome.diagnostic_code =
      "DATA_URBAN_POSITIVE_SIGNAL_ABSENT";

  outcome.evidence_revision =
      3;

  outcome.kind =
      ReviewedAnalysisOutcomeKind::
          HypothesisProposal;

  outcome.semantic_key =
      "hypothesis.data.urban_signal_source_gap";

  outcome.rationale =
      "Problem hypothesis for explicit semantic review.";


  HypothesisProposal proposal;

  proposal.id =
      "hypothesis-proposal-v1|" +
      outcome_id;

  proposal.source_review_id =
      outcome.source_review_id;

  proposal.source_analysis_id =
      outcome.source_analysis_id;

  proposal.cluster_key =
      outcome.cluster_key;

  proposal.context_key =
      outcome.context_key;

  proposal.data_scope_key =
      outcome.data_scope_key;

  proposal.diagnostic_code =
      outcome.diagnostic_code;

  proposal.evidence_revision =
      outcome.evidence_revision;

  proposal.hypothesis_key =
      outcome.semantic_key;

  proposal.reviewer_ref =
      outcome.outcome_reviewer_ref;

  proposal.rationale =
      outcome.rationale;

  outcome.hypothesis_proposal =
      proposal;

  return outcome;
}

}  // namespace


int main() {
  using namespace routing::core::intelligence;

  ProposalApprovalWorkflow workflow;


  // -------------------------------------------------------------
  // APPROVED TESTER QUESTION -> DELIVERY CANDIDATE ONLY
  // -------------------------------------------------------------

  const auto question_outcome =
      make_question_outcome(
          "outcome:question:1");

  ProposalApprovalRequest question_approval;

  question_approval.approval_id =
      "approval:question:1";

  question_approval.approver_ref =
      "approver:alpha";

  question_approval.decision =
      ProposalApprovalDecision::Approve;

  question_approval.rationale =
      "Approve for later explicit post-drive presentation.";


  const auto approved_question =
      workflow.apply(
          question_outcome,
          question_approval);

  assert(
      approved_question.record.
          question_delivery_candidate.has_value());

  const QuestionDeliveryCandidate& delivery =
      *approved_question.record.
          question_delivery_candidate;

  assert(
      delivery.data_scope_key ==
      "personal");

  assert(
      delivery.post_drive_only);

  assert(
      delivery.explicit_presentation_required);

  assert(
      !delivery.automatic_presentation_allowed);

  assert(
      !delivery.question_candidate_created);

  assert(
      !delivery.answer_application_allowed);

  assert(
      !delivery.production_application_allowed);

  assert(
      !delivery.evidence_scope_promotion_allowed);


  // -------------------------------------------------------------
  // APPROVED HYPOTHESIS -> CONVERSION CANDIDATE ONLY
  // -------------------------------------------------------------

  const auto hypothesis_outcome =
      make_hypothesis_outcome(
          "outcome:hypothesis:1");

  ProposalApprovalRequest hypothesis_approval;

  hypothesis_approval.approval_id =
      "approval:hypothesis:1";

  hypothesis_approval.approver_ref =
      "approver:beta";

  hypothesis_approval.decision =
      ProposalApprovalDecision::Approve;

  hypothesis_approval.rationale =
      "Approve semantic mapping review, not preference learning.";


  const auto approved_hypothesis =
      workflow.apply(
          hypothesis_outcome,
          hypothesis_approval);

  assert(
      approved_hypothesis.record.
          hypothesis_conversion_candidate.has_value());

  const HypothesisConversionCandidate& conversion =
      *approved_hypothesis.record.
          hypothesis_conversion_candidate;

  assert(
      conversion.data_scope_key ==
      "personal");

  assert(
      conversion.hypothesis_key ==
      "hypothesis.data.urban_signal_source_gap");

  assert(
      conversion.explicit_semantic_mapping_required);

  assert(
      !conversion.preference_target_created);

  assert(
      !conversion.preference_hypothesis_created);

  assert(
      !conversion.learning_gate_invoked);

  assert(
      !conversion.shadow_evaluation_created);

  assert(
      !conversion.production_application_allowed);

  assert(
      !conversion.evidence_scope_promotion_allowed);


  // -------------------------------------------------------------
  // REJECTION CREATES NO DOWNSTREAM ARTIFACT
  // -------------------------------------------------------------

  const auto rejected_outcome =
      make_hypothesis_outcome(
          "outcome:hypothesis:reject");

  ProposalApprovalRequest reject;

  reject.approval_id =
      "approval:hypothesis:reject";

  reject.approver_ref =
      "approver:gamma";

  reject.decision =
      ProposalApprovalDecision::Reject;

  reject.rationale =
      "Evidence does not justify semantic conversion review.";


  const auto rejected =
      workflow.apply(
          rejected_outcome,
          reject);

  assert(
      rejected.record.decision ==
      ProposalApprovalDecision::Reject);

  assert(
      !rejected.record.data_review_task.has_value());

  assert(
      !rejected.record.question_delivery_candidate.has_value());

  assert(
      !rejected.record.hypothesis_conversion_candidate.has_value());


  // -------------------------------------------------------------
  // UNSAFE SOURCE PROPOSAL CANNOT BE APPROVED
  // -------------------------------------------------------------

  auto unsafe_question =
      make_question_outcome(
          "outcome:question:unsafe");

  unsafe_question.tester_question_proposal->
      automatic_presentation_allowed =
          true;

  ProposalApprovalRequest unsafe_request;

  unsafe_request.approval_id =
      "approval:question:unsafe";

  unsafe_request.approver_ref =
      "approver:delta";

  unsafe_request.decision =
      ProposalApprovalDecision::Approve;

  unsafe_request.rationale =
      "Unsafe source object must be rejected.";


  bool unsafe_rejected =
      false;

  try {
    (void)workflow.apply(
        unsafe_question,
        unsafe_request);
  } catch (const std::logic_error&) {
    unsafe_rejected =
        true;
  }

  assert(
      unsafe_rejected);


  const std::string report =
      format_proposal_approval_report(
          approved_hypothesis.record);

  assert(
      report.find(
          "HYPOTHESIS CONVERSION CANDIDATE") !=
      std::string::npos);

  assert(
      report.find(
          "PreferenceTarget created: no") !=
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
      << "Proposal approval boundary tests passed\n";

  return 0;
}
