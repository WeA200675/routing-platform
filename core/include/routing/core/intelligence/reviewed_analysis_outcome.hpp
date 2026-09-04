#pragma once

#include <cstdint>
#include <optional>
#include <string>
#include <string_view>
#include <vector>

#include "routing/core/intelligence/cluster_problem_analysis.hpp"
#include "routing/core/intelligence/cluster_problem_review.hpp"

namespace routing::core::intelligence {

inline constexpr std::uint32_t
kReviewedAnalysisOutcomeSchemaVersion = 1;


// Explicit reviewed downstream proposal.
//
// Nothing is created merely because an analysis or review exists.
// A caller must explicitly request one outcome kind.
enum class ReviewedAnalysisOutcomeKind : std::uint8_t {
  DataReviewCandidate = 0,
  TesterQuestionProposal,
  HypothesisProposal,
};


[[nodiscard]]
constexpr std::string_view
reviewed_analysis_outcome_kind_key(
    const ReviewedAnalysisOutcomeKind kind) {
  switch (kind) {
    case ReviewedAnalysisOutcomeKind::
        DataReviewCandidate:
      return "data-review-candidate";

    case ReviewedAnalysisOutcomeKind::
        TesterQuestionProposal:
      return "tester-question-proposal";

    case ReviewedAnalysisOutcomeKind::
        HypothesisProposal:
      return "hypothesis-proposal";
  }

  return "unknown";
}


struct ReviewedAnalysisOutcomeRequest {
  // Caller-supplied stable idempotency key.
  std::string outcome_id;

  // Explicit reviewer/operator responsible for this downstream step.
  std::string reviewer_ref;

  ReviewedAnalysisOutcomeKind kind =
      ReviewedAnalysisOutcomeKind::
          DataReviewCandidate;

  // Stable semantic key.
  //
  // Required for TesterQuestionProposal and HypothesisProposal.
  //
  // For TesterQuestionProposal:
  //   semantic_key == prompt_key
  //
  // For HypothesisProposal:
  //   semantic_key == hypothesis_key
  //
  // DataReviewCandidate must leave this empty.
  std::string semantic_key;

  std::string rationale;
};


// Candidate for explicit source-data review.
//
// This type cannot edit map data or routing.
struct DataReviewCandidate {
  std::uint32_t schema_version =
      kReviewedAnalysisOutcomeSchemaVersion;

  std::string id;

  std::string source_review_id;
  std::string source_analysis_id;

  std::string cluster_key;
  std::string context_key;
  std::string data_scope_key;
  std::string diagnostic_code;

  std::uint64_t evidence_revision = 0;

  ClusterProblemDomain domain =
      ClusterProblemDomain::DataQuality;

  diagnostics::DiagnosticSeverity severity =
      diagnostics::DiagnosticSeverity::Info;

  // Stable machine target for a later review adapter.
  std::string review_target_key;

  std::string reviewer_ref;
  std::string rationale;

  bool map_change_allowed = false;
  bool routing_change_allowed = false;
  bool automatic_publish_allowed = false;
  bool evidence_scope_promotion_allowed = false;
};


// Proposal for a tester/reviewer question.
//
// This is NOT QuestionCandidate and is never shown automatically.
struct TesterQuestionProposal {
  std::uint32_t schema_version =
      kReviewedAnalysisOutcomeSchemaVersion;

  std::string id;

  std::string source_review_id;
  std::string source_analysis_id;

  std::string cluster_key;
  std::string context_key;
  std::string data_scope_key;
  std::string diagnostic_code;

  std::uint64_t evidence_revision = 0;

  std::string prompt_key;

  std::string reviewer_ref;
  std::string rationale;

  // Safety / UX boundary.
  bool post_drive_only = true;
  bool automatic_presentation_allowed = false;

  // This proposal has not been converted into the existing
  // DriveSession QuestionCandidate type.
  bool question_candidate_created = false;

  bool answer_application_allowed = false;
  bool production_application_allowed = false;
  bool evidence_scope_promotion_allowed = false;
};


// Explicit problem-hypothesis proposal.
//
// This is deliberately NOT PreferenceHypothesis.
//
// It has no PreferenceTarget, strength, confidence or learning
// permission. The semantic hypothesis key is supplied explicitly by
// the reviewer and is not invented from diagnostic evidence.
struct HypothesisProposal {
  std::uint32_t schema_version =
      kReviewedAnalysisOutcomeSchemaVersion;

  std::string id;

  std::string source_review_id;
  std::string source_analysis_id;

  std::string cluster_key;
  std::string context_key;
  std::string data_scope_key;
  std::string diagnostic_code;

  std::uint64_t evidence_revision = 0;

  std::string hypothesis_key;

  std::string reviewer_ref;
  std::string rationale;

  // A later explicit approval/conversion step is mandatory.
  bool explicit_conversion_required = true;

  bool preference_hypothesis_created = false;
  bool learning_gate_invoked = false;
  bool shadow_evaluation_created = false;

  bool production_application_allowed = false;
  bool evidence_scope_promotion_allowed = false;
};


struct ReviewedAnalysisOutcomeRecord {
  std::uint32_t schema_version =
      kReviewedAnalysisOutcomeSchemaVersion;

  std::string outcome_id;

  std::string source_review_id;
  std::string source_analysis_id;

  std::string review_reviewer_ref;
  std::string outcome_reviewer_ref;

  std::string cluster_key;
  std::string context_key;
  std::string data_scope_key;
  std::string diagnostic_code;

  std::uint64_t evidence_revision = 0;

  ReviewedAnalysisOutcomeKind kind =
      ReviewedAnalysisOutcomeKind::
          DataReviewCandidate;

  std::string semantic_key;
  std::string rationale;

  std::optional<DataReviewCandidate>
      data_review_candidate;

  std::optional<TesterQuestionProposal>
      tester_question_proposal;

  std::optional<HypothesisProposal>
      hypothesis_proposal;
};


enum class ReviewedAnalysisOutcomeApplyStatus : std::uint8_t {
  Created = 0,
  DuplicateIgnored,
};


struct ReviewedAnalysisOutcomeApplyResult {
  ReviewedAnalysisOutcomeApplyStatus status =
      ReviewedAnalysisOutcomeApplyStatus::Created;

  ReviewedAnalysisOutcomeRecord record;
};


// Explicit, idempotent downstream proposal ledger.
//
// This workflow does NOT:
//   - mutate AnomalyTracker investigation state,
//   - enqueue IntelligenceJobs,
//   - create QuestionCandidate,
//   - create PreferenceHypothesis,
//   - call LearningGate,
//   - create ShadowEvaluationCandidate,
//   - change map data,
//   - change routing,
//   - promote evidence scope.
class ReviewedAnalysisOutcomeWorkflow {
 public:
  [[nodiscard]]
  ReviewedAnalysisOutcomeApplyResult apply(
      const ClusterProblemAnalysisResult& analysis,
      const ClusterProblemReviewRecord& review,
      const ReviewedAnalysisOutcomeRequest& request);

  [[nodiscard]]
  const std::vector<ReviewedAnalysisOutcomeRecord>&
  records() const noexcept {
    return records_;
  }

 private:
  [[nodiscard]]
  const ReviewedAnalysisOutcomeRecord*
  find_record(
      std::string_view outcome_id) const;

  std::vector<ReviewedAnalysisOutcomeRecord>
      records_;
};

}  // namespace routing::core::intelligence
