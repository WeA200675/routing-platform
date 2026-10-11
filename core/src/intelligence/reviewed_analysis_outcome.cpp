#include "routing/core/intelligence/reviewed_analysis_outcome.hpp"

#include <algorithm>
#include <stdexcept>
#include <string>
#include <utility>

namespace routing::core::intelligence {

namespace {

bool has_next_action(
    const ClusterProblemAnalysisResult& analysis,
    const ClusterProblemNextAction action) {
  return
      std::find(
          analysis.next_actions.begin(),
          analysis.next_actions.end(),
          action) !=
      analysis.next_actions.end();
}


void validate_request(
    const ReviewedAnalysisOutcomeRequest& request) {
  if (request.outcome_id.empty()) {
    throw std::invalid_argument(
        "Reviewed analysis outcome requires outcome_id.");
  }

  if (request.reviewer_ref.empty()) {
    throw std::invalid_argument(
        "Reviewed analysis outcome requires reviewer_ref.");
  }

  if (request.rationale.empty()) {
    throw std::invalid_argument(
        "Reviewed analysis outcome requires rationale.");
  }

  switch (request.kind) {
    case ReviewedAnalysisOutcomeKind::
        DataReviewCandidate:
      if (!request.semantic_key.empty()) {
        throw std::invalid_argument(
            "DataReviewCandidate must not use semantic_key.");
      }
      break;

    case ReviewedAnalysisOutcomeKind::
        TesterQuestionProposal:
    case ReviewedAnalysisOutcomeKind::
        HypothesisProposal:
      if (request.semantic_key.empty()) {
        throw std::invalid_argument(
            "Proposal outcome requires semantic_key.");
      }
      break;
  }
}


void validate_analysis(
    const ClusterProblemAnalysisResult& analysis) {
  if (analysis.schema_version !=
      kClusterProblemAnalysisSchemaVersion) {
    throw std::invalid_argument(
        "Unsupported cluster problem analysis schema.");
  }

  if (analysis.status !=
      ClusterProblemAnalysisStatus::Completed) {
    throw std::logic_error(
        "Only completed analysis may create reviewed outcomes.");
  }

  if (analysis.analysis_id.empty() ||
      analysis.job_id.empty() ||
      analysis.cluster_key.empty() ||
      analysis.context_key.empty() ||
      analysis.data_scope_key.empty() ||
      analysis.diagnostic_code.empty()) {
    throw std::invalid_argument(
        "Reviewed outcome requires complete analysis identity.");
  }

  if (analysis.evidence_revision == 0 ||
      analysis.observed_cluster_revision == 0) {
    throw std::invalid_argument(
        "Reviewed outcome requires evidence revision.");
  }

  if (analysis.evidence_revision !=
      analysis.observed_cluster_revision) {
    throw std::logic_error(
        "Reviewed outcome requires current analysis revision.");
  }
}


void validate_review(
    const ClusterProblemAnalysisResult& analysis,
    const ClusterProblemReviewRecord& review) {
  if (review.schema_version !=
      kClusterProblemReviewSchemaVersion) {
    throw std::invalid_argument(
        "Unsupported cluster problem review schema.");
  }

  if (review.review_id.empty() ||
      review.reviewer_ref.empty()) {
    throw std::invalid_argument(
        "Reviewed outcome requires complete review identity.");
  }

  if (review.analysis_id !=
          analysis.analysis_id ||
      review.job_id !=
          analysis.job_id ||
      review.cluster_key !=
          analysis.cluster_key ||
      review.context_key !=
          analysis.context_key ||
      review.data_scope_key !=
          analysis.data_scope_key ||
      review.diagnostic_code !=
          analysis.diagnostic_code) {
    throw std::invalid_argument(
        "Reviewed outcome analysis/review identity mismatch.");
  }

  if (review.analysis_status !=
      ClusterProblemAnalysisStatus::Completed) {
    throw std::logic_error(
        "Reviewed outcome requires completed review analysis.");
  }

  if (review.analysis_evidence_revision !=
          analysis.evidence_revision ||
      review.cluster_revision_at_review !=
          analysis.evidence_revision) {
    throw std::logic_error(
        "Reviewed outcome revision mismatch.");
  }

  if (review.refresh_job_requested) {
    throw std::logic_error(
        "Refresh review cannot create a downstream outcome.");
  }
}


bool same_request_identity(
    const ReviewedAnalysisOutcomeRecord& existing,
    const ClusterProblemAnalysisResult& analysis,
    const ClusterProblemReviewRecord& review,
    const ReviewedAnalysisOutcomeRequest& request) {
  return
      existing.source_review_id ==
          review.review_id &&
      existing.source_analysis_id ==
          analysis.analysis_id &&
      existing.outcome_reviewer_ref ==
          request.reviewer_ref &&
      existing.kind ==
          request.kind &&
      existing.semantic_key ==
          request.semantic_key &&
      existing.rationale ==
          request.rationale;
}


void validate_decision_for_kind(
    const ClusterProblemAnalysisResult& analysis,
    const ClusterProblemReviewRecord& review,
    const ReviewedAnalysisOutcomeRequest& request) {
  switch (request.kind) {
    case ReviewedAnalysisOutcomeKind::
        DataReviewCandidate:
      if (review.decision !=
          ClusterProblemReviewDecision::Acknowledge) {
        throw std::logic_error(
            "DataReviewCandidate requires Acknowledge review.");
      }

      if (review.resulting_state !=
          diagnostics::InvestigationState::Investigating) {
        throw std::logic_error(
            "DataReviewCandidate requires investigating state.");
      }

      if (analysis.domain !=
          ClusterProblemDomain::DataQuality) {
        throw std::logic_error(
            "DataReviewCandidate requires data-quality analysis.");
      }

      if (!has_next_action(
              analysis,
              ClusterProblemNextAction::
                  ReviewSourceData)) {
        throw std::logic_error(
            "DataReviewCandidate requires ReviewSourceData action.");
      }
      break;


    case ReviewedAnalysisOutcomeKind::
        TesterQuestionProposal:
      if (review.decision !=
          ClusterProblemReviewDecision::
              NeedsMoreEvidence) {
        throw std::logic_error(
            "TesterQuestionProposal requires NeedsMoreEvidence review.");
      }

      if (review.resulting_state !=
          diagnostics::InvestigationState::Investigating) {
        throw std::logic_error(
            "TesterQuestionProposal requires investigating state.");
      }
      break;


    case ReviewedAnalysisOutcomeKind::
        HypothesisProposal:
      if (review.decision !=
          ClusterProblemReviewDecision::Acknowledge) {
        throw std::logic_error(
            "HypothesisProposal requires Acknowledge review.");
      }

      if (review.resulting_state !=
          diagnostics::InvestigationState::Investigating) {
        throw std::logic_error(
            "HypothesisProposal requires investigating state.");
      }
      break;
  }
}


DataReviewCandidate
make_data_review_candidate(
    const ClusterProblemAnalysisResult& analysis,
    const ClusterProblemReviewRecord& review,
    const ReviewedAnalysisOutcomeRequest& request) {
  DataReviewCandidate candidate;

  candidate.id =
      std::string(
          "data-review-v1|") +
      request.outcome_id;

  candidate.source_review_id =
      review.review_id;

  candidate.source_analysis_id =
      analysis.analysis_id;

  candidate.cluster_key =
      analysis.cluster_key;

  candidate.context_key =
      analysis.context_key;

  candidate.data_scope_key =
      analysis.data_scope_key;

  candidate.diagnostic_code =
      analysis.diagnostic_code;

  candidate.evidence_revision =
      analysis.evidence_revision;

  candidate.domain =
      analysis.domain;

  candidate.severity =
      analysis.severity;

  candidate.review_target_key =
      "source-data";

  candidate.reviewer_ref =
      request.reviewer_ref;

  candidate.rationale =
      request.rationale;

  return candidate;
}


TesterQuestionProposal
make_tester_question_proposal(
    const ClusterProblemAnalysisResult& analysis,
    const ClusterProblemReviewRecord& review,
    const ReviewedAnalysisOutcomeRequest& request) {
  TesterQuestionProposal proposal;

  proposal.id =
      std::string(
          "tester-question-proposal-v1|") +
      request.outcome_id;

  proposal.source_review_id =
      review.review_id;

  proposal.source_analysis_id =
      analysis.analysis_id;

  proposal.cluster_key =
      analysis.cluster_key;

  proposal.context_key =
      analysis.context_key;

  proposal.data_scope_key =
      analysis.data_scope_key;

  proposal.diagnostic_code =
      analysis.diagnostic_code;

  proposal.evidence_revision =
      analysis.evidence_revision;

  proposal.prompt_key =
      request.semantic_key;

  proposal.reviewer_ref =
      request.reviewer_ref;

  proposal.rationale =
      request.rationale;

  return proposal;
}


HypothesisProposal
make_hypothesis_proposal(
    const ClusterProblemAnalysisResult& analysis,
    const ClusterProblemReviewRecord& review,
    const ReviewedAnalysisOutcomeRequest& request) {
  HypothesisProposal proposal;

  proposal.id =
      std::string(
          "hypothesis-proposal-v1|") +
      request.outcome_id;

  proposal.source_review_id =
      review.review_id;

  proposal.source_analysis_id =
      analysis.analysis_id;

  proposal.cluster_key =
      analysis.cluster_key;

  proposal.context_key =
      analysis.context_key;

  proposal.data_scope_key =
      analysis.data_scope_key;

  proposal.diagnostic_code =
      analysis.diagnostic_code;

  proposal.evidence_revision =
      analysis.evidence_revision;

  // Explicit reviewer-supplied semantic proposition key.
  //
  // No causal or preference hypothesis is inferred automatically.
  proposal.hypothesis_key =
      request.semantic_key;

  proposal.reviewer_ref =
      request.reviewer_ref;

  proposal.rationale =
      request.rationale;

  return proposal;
}


ReviewedAnalysisOutcomeRecord
make_base_record(
    const ClusterProblemAnalysisResult& analysis,
    const ClusterProblemReviewRecord& review,
    const ReviewedAnalysisOutcomeRequest& request) {
  ReviewedAnalysisOutcomeRecord record;

  record.outcome_id =
      request.outcome_id;

  record.source_review_id =
      review.review_id;

  record.source_analysis_id =
      analysis.analysis_id;

  record.review_reviewer_ref =
      review.reviewer_ref;

  record.outcome_reviewer_ref =
      request.reviewer_ref;

  record.cluster_key =
      analysis.cluster_key;

  record.context_key =
      analysis.context_key;

  record.data_scope_key =
      analysis.data_scope_key;

  record.diagnostic_code =
      analysis.diagnostic_code;

  record.evidence_revision =
      analysis.evidence_revision;

  record.kind =
      request.kind;

  record.semantic_key =
      request.semantic_key;

  record.rationale =
      request.rationale;

  return record;
}


}  // namespace


const ReviewedAnalysisOutcomeRecord*
ReviewedAnalysisOutcomeWorkflow::find_record(
    const std::string_view outcome_id) const {
  for (const auto& record :
       records_) {
    if (record.outcome_id ==
        outcome_id) {
      return &record;
    }
  }

  return nullptr;
}


ReviewedAnalysisOutcomeApplyResult
ReviewedAnalysisOutcomeWorkflow::apply(
    const ClusterProblemAnalysisResult& analysis,
    const ClusterProblemReviewRecord& review,
    const ReviewedAnalysisOutcomeRequest& request) {
  validate_request(
      request);

  validate_analysis(
      analysis);

  validate_review(
      analysis,
      review);


  // Idempotency is checked before any artifact creation.
  if (const auto* existing =
          find_record(
              request.outcome_id);
      existing != nullptr) {
    if (!same_request_identity(
            *existing,
            analysis,
            review,
            request)) {
      throw std::invalid_argument(
          "Outcome id collision with different outcome identity.");
    }

    return {
        ReviewedAnalysisOutcomeApplyStatus::
            DuplicateIgnored,
        *existing,
    };
  }


  validate_decision_for_kind(
      analysis,
      review,
      request);


  auto record =
      make_base_record(
          analysis,
          review,
          request);


  switch (request.kind) {
    case ReviewedAnalysisOutcomeKind::
        DataReviewCandidate:
      record.data_review_candidate =
          make_data_review_candidate(
              analysis,
              review,
              request);
      break;


    case ReviewedAnalysisOutcomeKind::
        TesterQuestionProposal:
      record.tester_question_proposal =
          make_tester_question_proposal(
              analysis,
              review,
              request);
      break;


    case ReviewedAnalysisOutcomeKind::
        HypothesisProposal:
      record.hypothesis_proposal =
          make_hypothesis_proposal(
              analysis,
              review,
              request);
      break;
  }


  records_.push_back(
      record);

  return {
      ReviewedAnalysisOutcomeApplyStatus::Created,
      std::move(record),
  };
}

}  // namespace routing::core::intelligence
