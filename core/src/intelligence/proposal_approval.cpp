#include "routing/core/intelligence/proposal_approval.hpp"

#include <stdexcept>
#include <string>
#include <utility>

namespace routing::core::intelligence {

namespace {

void validate_request(
    const ProposalApprovalRequest& request) {
  if (request.approval_id.empty()) {
    throw std::invalid_argument(
        "Proposal approval requires approval_id.");
  }

  if (request.approver_ref.empty()) {
    throw std::invalid_argument(
        "Proposal approval requires approver_ref.");
  }

  if (request.rationale.empty()) {
    throw std::invalid_argument(
        "Proposal approval requires rationale.");
  }
}


void validate_common_outcome_identity(
    const ReviewedAnalysisOutcomeRecord& outcome) {
  if (outcome.schema_version !=
      kReviewedAnalysisOutcomeSchemaVersion) {
    throw std::invalid_argument(
        "Unsupported reviewed analysis outcome schema.");
  }

  if (outcome.outcome_id.empty() ||
      outcome.source_review_id.empty() ||
      outcome.source_analysis_id.empty() ||
      outcome.outcome_reviewer_ref.empty() ||
      outcome.cluster_key.empty() ||
      outcome.context_key.empty() ||
      outcome.data_scope_key.empty() ||
      outcome.diagnostic_code.empty()) {
    throw std::invalid_argument(
        "Proposal approval requires complete outcome identity.");
  }

  if (outcome.evidence_revision == 0) {
    throw std::invalid_argument(
        "Proposal approval requires evidence revision.");
  }
}


void validate_data_review_candidate(
    const ReviewedAnalysisOutcomeRecord& outcome,
    const DataReviewCandidate& candidate) {
  if (candidate.schema_version !=
      kReviewedAnalysisOutcomeSchemaVersion) {
    throw std::invalid_argument(
        "Unsupported DataReviewCandidate schema.");
  }

  if (candidate.id.empty() ||
      candidate.review_target_key.empty()) {
    throw std::invalid_argument(
        "DataReviewCandidate identity is incomplete.");
  }

  if (candidate.source_review_id !=
          outcome.source_review_id ||
      candidate.source_analysis_id !=
          outcome.source_analysis_id ||
      candidate.cluster_key !=
          outcome.cluster_key ||
      candidate.context_key !=
          outcome.context_key ||
      candidate.data_scope_key !=
          outcome.data_scope_key ||
      candidate.diagnostic_code !=
          outcome.diagnostic_code ||
      candidate.evidence_revision !=
          outcome.evidence_revision) {
    throw std::invalid_argument(
        "DataReviewCandidate/outcome identity mismatch.");
  }

  if (candidate.domain !=
      ClusterProblemDomain::DataQuality) {
    throw std::logic_error(
        "DataReviewCandidate approval requires DataQuality domain.");
  }

  if (!outcome.semantic_key.empty()) {
    throw std::invalid_argument(
        "DataReviewCandidate outcome must not contain semantic_key.");
  }

  if (candidate.map_change_allowed ||
      candidate.routing_change_allowed ||
      candidate.automatic_publish_allowed ||
      candidate.evidence_scope_promotion_allowed) {
    throw std::logic_error(
        "Unsafe DataReviewCandidate permissions cannot be approved.");
  }
}


void validate_tester_question_proposal(
    const ReviewedAnalysisOutcomeRecord& outcome,
    const TesterQuestionProposal& proposal) {
  if (proposal.schema_version !=
      kReviewedAnalysisOutcomeSchemaVersion) {
    throw std::invalid_argument(
        "Unsupported TesterQuestionProposal schema.");
  }

  if (proposal.id.empty() ||
      proposal.prompt_key.empty()) {
    throw std::invalid_argument(
        "TesterQuestionProposal identity is incomplete.");
  }

  if (proposal.source_review_id !=
          outcome.source_review_id ||
      proposal.source_analysis_id !=
          outcome.source_analysis_id ||
      proposal.cluster_key !=
          outcome.cluster_key ||
      proposal.context_key !=
          outcome.context_key ||
      proposal.data_scope_key !=
          outcome.data_scope_key ||
      proposal.diagnostic_code !=
          outcome.diagnostic_code ||
      proposal.evidence_revision !=
          outcome.evidence_revision) {
    throw std::invalid_argument(
        "TesterQuestionProposal/outcome identity mismatch.");
  }

  if (outcome.semantic_key !=
      proposal.prompt_key) {
    throw std::invalid_argument(
        "TesterQuestionProposal semantic key mismatch.");
  }

  if (!proposal.post_drive_only) {
    throw std::logic_error(
        "TesterQuestionProposal must remain post-drive only.");
  }

  if (proposal.automatic_presentation_allowed ||
      proposal.question_candidate_created ||
      proposal.answer_application_allowed ||
      proposal.production_application_allowed ||
      proposal.evidence_scope_promotion_allowed) {
    throw std::logic_error(
        "Unsafe TesterQuestionProposal permissions cannot be approved.");
  }
}


void validate_hypothesis_proposal(
    const ReviewedAnalysisOutcomeRecord& outcome,
    const HypothesisProposal& proposal) {
  if (proposal.schema_version !=
      kReviewedAnalysisOutcomeSchemaVersion) {
    throw std::invalid_argument(
        "Unsupported HypothesisProposal schema.");
  }

  if (proposal.id.empty() ||
      proposal.hypothesis_key.empty()) {
    throw std::invalid_argument(
        "HypothesisProposal identity is incomplete.");
  }

  if (proposal.source_review_id !=
          outcome.source_review_id ||
      proposal.source_analysis_id !=
          outcome.source_analysis_id ||
      proposal.cluster_key !=
          outcome.cluster_key ||
      proposal.context_key !=
          outcome.context_key ||
      proposal.data_scope_key !=
          outcome.data_scope_key ||
      proposal.diagnostic_code !=
          outcome.diagnostic_code ||
      proposal.evidence_revision !=
          outcome.evidence_revision) {
    throw std::invalid_argument(
        "HypothesisProposal/outcome identity mismatch.");
  }

  if (outcome.semantic_key !=
      proposal.hypothesis_key) {
    throw std::invalid_argument(
        "HypothesisProposal semantic key mismatch.");
  }

  if (!proposal.explicit_conversion_required) {
    throw std::logic_error(
        "HypothesisProposal must require explicit conversion.");
  }

  if (proposal.preference_hypothesis_created ||
      proposal.learning_gate_invoked ||
      proposal.shadow_evaluation_created ||
      proposal.production_application_allowed ||
      proposal.evidence_scope_promotion_allowed) {
    throw std::logic_error(
        "Unsafe HypothesisProposal permissions cannot be approved.");
  }
}


void validate_outcome(
    const ReviewedAnalysisOutcomeRecord& outcome) {
  validate_common_outcome_identity(
      outcome);

  const unsigned int populated =
      (outcome.data_review_candidate.has_value()
           ? 1U
           : 0U) +
      (outcome.tester_question_proposal.has_value()
           ? 1U
           : 0U) +
      (outcome.hypothesis_proposal.has_value()
           ? 1U
           : 0U);

  if (populated != 1U) {
    throw std::invalid_argument(
        "Reviewed outcome must contain exactly one proposal artifact.");
  }

  switch (outcome.kind) {
    case ReviewedAnalysisOutcomeKind::
        DataReviewCandidate:
      if (!outcome.data_review_candidate.has_value() ||
          outcome.tester_question_proposal.has_value() ||
          outcome.hypothesis_proposal.has_value()) {
        throw std::invalid_argument(
            "Reviewed outcome kind/artifact mismatch.");
      }

      validate_data_review_candidate(
          outcome,
          *outcome.data_review_candidate);
      break;


    case ReviewedAnalysisOutcomeKind::
        TesterQuestionProposal:
      if (outcome.data_review_candidate.has_value() ||
          !outcome.tester_question_proposal.has_value() ||
          outcome.hypothesis_proposal.has_value()) {
        throw std::invalid_argument(
            "Reviewed outcome kind/artifact mismatch.");
      }

      validate_tester_question_proposal(
          outcome,
          *outcome.tester_question_proposal);
      break;


    case ReviewedAnalysisOutcomeKind::
        HypothesisProposal:
      if (outcome.data_review_candidate.has_value() ||
          outcome.tester_question_proposal.has_value() ||
          !outcome.hypothesis_proposal.has_value()) {
        throw std::invalid_argument(
            "Reviewed outcome kind/artifact mismatch.");
      }

      validate_hypothesis_proposal(
          outcome,
          *outcome.hypothesis_proposal);
      break;
  }
}


bool same_request_identity(
    const ProposalApprovalRecord& existing,
    const ReviewedAnalysisOutcomeRecord& outcome,
    const ProposalApprovalRequest& request) {
  return
      existing.source_outcome_id ==
          outcome.outcome_id &&
      existing.approver_ref ==
          request.approver_ref &&
      existing.decision ==
          request.decision &&
      existing.rationale ==
          request.rationale;
}


ProposalApprovalRecord
make_base_record(
    const ReviewedAnalysisOutcomeRecord& outcome,
    const ProposalApprovalRequest& request) {
  ProposalApprovalRecord record;

  record.approval_id =
      request.approval_id;

  record.source_outcome_id =
      outcome.outcome_id;

  record.source_review_id =
      outcome.source_review_id;

  record.source_analysis_id =
      outcome.source_analysis_id;

  record.outcome_kind =
      outcome.kind;

  record.outcome_reviewer_ref =
      outcome.outcome_reviewer_ref;

  record.approver_ref =
      request.approver_ref;

  record.cluster_key =
      outcome.cluster_key;

  record.context_key =
      outcome.context_key;

  record.data_scope_key =
      outcome.data_scope_key;

  record.diagnostic_code =
      outcome.diagnostic_code;

  record.evidence_revision =
      outcome.evidence_revision;

  record.decision =
      request.decision;

  record.rationale =
      request.rationale;

  return record;
}


DataReviewTask
make_data_review_task(
    const ReviewedAnalysisOutcomeRecord& outcome,
    const ProposalApprovalRequest& request) {
  const auto& source =
      *outcome.data_review_candidate;

  DataReviewTask task;

  task.id =
      std::string(
          "data-review-task-v1|") +
      outcome.outcome_id;

  task.source_outcome_id =
      outcome.outcome_id;

  task.source_review_id =
      outcome.source_review_id;

  task.source_analysis_id =
      outcome.source_analysis_id;

  task.cluster_key =
      outcome.cluster_key;

  task.context_key =
      outcome.context_key;

  task.data_scope_key =
      outcome.data_scope_key;

  task.diagnostic_code =
      outcome.diagnostic_code;

  task.evidence_revision =
      outcome.evidence_revision;

  task.review_target_key =
      source.review_target_key;

  task.approver_ref =
      request.approver_ref;

  task.rationale =
      request.rationale;

  return task;
}


QuestionDeliveryCandidate
make_question_delivery_candidate(
    const ReviewedAnalysisOutcomeRecord& outcome,
    const ProposalApprovalRequest& request) {
  const auto& source =
      *outcome.tester_question_proposal;

  QuestionDeliveryCandidate candidate;

  candidate.id =
      std::string(
          "question-delivery-v1|") +
      outcome.outcome_id;

  candidate.source_outcome_id =
      outcome.outcome_id;

  candidate.source_review_id =
      outcome.source_review_id;

  candidate.source_analysis_id =
      outcome.source_analysis_id;

  candidate.cluster_key =
      outcome.cluster_key;

  candidate.context_key =
      outcome.context_key;

  candidate.data_scope_key =
      outcome.data_scope_key;

  candidate.diagnostic_code =
      outcome.diagnostic_code;

  candidate.evidence_revision =
      outcome.evidence_revision;

  candidate.prompt_key =
      source.prompt_key;

  candidate.approver_ref =
      request.approver_ref;

  candidate.rationale =
      request.rationale;

  return candidate;
}


HypothesisConversionCandidate
make_hypothesis_conversion_candidate(
    const ReviewedAnalysisOutcomeRecord& outcome,
    const ProposalApprovalRequest& request) {
  const auto& source =
      *outcome.hypothesis_proposal;

  HypothesisConversionCandidate candidate;

  candidate.id =
      std::string(
          "hypothesis-conversion-v1|") +
      outcome.outcome_id;

  candidate.source_outcome_id =
      outcome.outcome_id;

  candidate.source_review_id =
      outcome.source_review_id;

  candidate.source_analysis_id =
      outcome.source_analysis_id;

  candidate.cluster_key =
      outcome.cluster_key;

  candidate.context_key =
      outcome.context_key;

  candidate.data_scope_key =
      outcome.data_scope_key;

  candidate.diagnostic_code =
      outcome.diagnostic_code;

  candidate.evidence_revision =
      outcome.evidence_revision;

  candidate.hypothesis_key =
      source.hypothesis_key;

  candidate.approver_ref =
      request.approver_ref;

  candidate.rationale =
      request.rationale;

  return candidate;
}


}  // namespace


const ProposalApprovalRecord*
ProposalApprovalWorkflow::find_by_approval_id(
    const std::string_view approval_id) const {
  for (const auto& record :
       records_) {
    if (record.approval_id ==
        approval_id) {
      return &record;
    }
  }

  return nullptr;
}


const ProposalApprovalRecord*
ProposalApprovalWorkflow::find_by_outcome_id(
    const std::string_view outcome_id) const {
  for (const auto& record :
       records_) {
    if (record.source_outcome_id ==
        outcome_id) {
      return &record;
    }
  }

  return nullptr;
}


ProposalApprovalApplyResult
ProposalApprovalWorkflow::apply(
    const ReviewedAnalysisOutcomeRecord& outcome,
    const ProposalApprovalRequest& request) {
  validate_request(
      request);

  validate_outcome(
      outcome);


  // Exact repeated approval request is idempotent.
  if (const auto* existing =
          find_by_approval_id(
              request.approval_id);
      existing != nullptr) {
    if (!same_request_identity(
            *existing,
            outcome,
            request)) {
      throw std::invalid_argument(
          "Approval id collision with different approval identity.");
    }

    return {
        ProposalApprovalApplyStatus::
            DuplicateIgnored,
        *existing,
    };
  }


  // One outcome receives one terminal approval/rejection record.
  //
  // This avoids contradictory approvers silently racing the same
  // proposal into different states.
  if (find_by_outcome_id(
          outcome.outcome_id) != nullptr) {
    throw std::logic_error(
        "Reviewed outcome already has an approval record.");
  }


  auto record =
      make_base_record(
          outcome,
          request);


  // Rejection is itself the complete terminal artifact.
  if (request.decision ==
      ProposalApprovalDecision::Reject) {
    records_.push_back(
        record);

    return {
        ProposalApprovalApplyStatus::Created,
        std::move(record),
    };
  }


  // Approval creates exactly one next-boundary candidate/task.
  //
  // It still does not perform presentation, learning, shadow
  // evaluation, map changes or routing changes.
  switch (outcome.kind) {
    case ReviewedAnalysisOutcomeKind::
        DataReviewCandidate:
      record.data_review_task =
          make_data_review_task(
              outcome,
              request);
      break;


    case ReviewedAnalysisOutcomeKind::
        TesterQuestionProposal:
      record.question_delivery_candidate =
          make_question_delivery_candidate(
              outcome,
              request);
      break;


    case ReviewedAnalysisOutcomeKind::
        HypothesisProposal:
      record.hypothesis_conversion_candidate =
          make_hypothesis_conversion_candidate(
              outcome,
              request);
      break;
  }


  records_.push_back(
      record);

  return {
      ProposalApprovalApplyStatus::Created,
      std::move(record),
  };
}

}  // namespace routing::core::intelligence
