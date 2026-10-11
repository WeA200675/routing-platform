#pragma once

#include <cstdint>
#include <string>
#include <string_view>
#include <vector>

#include "routing/core/diagnostics/anomaly_tracker.hpp"
#include "routing/core/intelligence/cluster_problem_analysis.hpp"
#include "routing/core/intelligence/diagnostic_investigation_job.hpp"

namespace routing::core::intelligence {

inline constexpr std::uint32_t
kClusterProblemReviewSchemaVersion = 1;


// Explicit human/operator review decision.
//
// These decisions are review workflow state only.
// They are not routing rules, learning permission or AI application.
enum class ClusterProblemReviewDecision : std::uint8_t {
  Acknowledge = 0,
  NeedsMoreEvidence,
  RefreshAnalysis,
  Resolve,
  Dismiss,
};


[[nodiscard]]
constexpr std::string_view
cluster_problem_review_decision_key(
    const ClusterProblemReviewDecision decision) {
  switch (decision) {
    case ClusterProblemReviewDecision::Acknowledge:
      return "acknowledge";

    case ClusterProblemReviewDecision::NeedsMoreEvidence:
      return "needs-more-evidence";

    case ClusterProblemReviewDecision::RefreshAnalysis:
      return "refresh-analysis";

    case ClusterProblemReviewDecision::Resolve:
      return "resolve";

    case ClusterProblemReviewDecision::Dismiss:
      return "dismiss";
  }

  return "unknown";
}


struct ClusterProblemReviewRequest {
  // Caller-supplied stable idempotency key.
  std::string review_id;

  // Stable local reviewer/operator reference.
  std::string reviewer_ref;

  ClusterProblemReviewDecision decision =
      ClusterProblemReviewDecision::Acknowledge;

  // Explicit rationale is required for durable review evidence.
  std::string rationale;
};


struct ClusterProblemReviewRecord {
  std::uint32_t schema_version =
      kClusterProblemReviewSchemaVersion;

  std::string review_id;
  std::string reviewer_ref;

  std::string analysis_id;
  std::string job_id;
  std::string cluster_key;
  std::string context_key;
  std::string data_scope_key;
  std::string diagnostic_code;

  std::uint64_t analysis_evidence_revision = 0;
  std::uint64_t cluster_revision_at_review = 0;

  ClusterProblemAnalysisStatus analysis_status =
      ClusterProblemAnalysisStatus::Completed;

  ClusterProblemReviewDecision decision =
      ClusterProblemReviewDecision::Acknowledge;

  diagnostics::InvestigationState prior_state =
      diagnostics::InvestigationState::Observed;

  diagnostics::InvestigationState resulting_state =
      diagnostics::InvestigationState::Observed;

  bool state_changed = false;

  std::string rationale;

  // Populated only for an explicit RefreshAnalysis decision.
  bool refresh_job_requested = false;
  std::string refresh_job_id;

  std::uint64_t refresh_from_revision = 0;
  std::uint64_t refresh_to_revision = 0;

  // Hard downstream boundary for this sprint.
  bool data_review_request_created = false;
  bool tester_question_created = false;
  bool hypothesis_proposal_created = false;
  bool preference_hypothesis_created = false;
  bool learning_gate_invoked = false;
  bool production_application_allowed = false;
  bool evidence_scope_promotion_allowed = false;
};


enum class ClusterProblemReviewApplyStatus : std::uint8_t {
  Applied = 0,
  DuplicateIgnored,
};


struct ClusterProblemReviewApplyResult {
  ClusterProblemReviewApplyStatus status =
      ClusterProblemReviewApplyStatus::Applied;

  ClusterProblemReviewRecord record;
};


// In-memory deterministic review ledger.
//
// Persistence can later serialize these versioned records without
// changing the state-transition semantics here.
class ClusterProblemReviewWorkflow {
 public:
  [[nodiscard]]
  ClusterProblemReviewApplyResult apply(
      diagnostics::AnomalyTracker& tracker,
      IntelligenceJobQueue& queue,
      const ClusterProblemAnalysisResult& analysis,
      const ClusterProblemReviewRequest& request);

  [[nodiscard]]
  const std::vector<ClusterProblemReviewRecord>&
  records() const noexcept {
    return records_;
  }

 private:
  [[nodiscard]]
  const ClusterProblemReviewRecord*
  find_record(
      std::string_view review_id) const;

  std::vector<ClusterProblemReviewRecord>
      records_;
};

}  // namespace routing::core::intelligence
