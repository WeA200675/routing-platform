#pragma once

#include <sstream>
#include <string>

#include "routing/core/intelligence/cluster_problem_review.hpp"

namespace routing::core::intelligence {

[[nodiscard]]
inline std::string
format_cluster_problem_review_report(
    const ClusterProblemReviewRecord& record) {
  std::ostringstream output;

  output
      << "CLUSTER PROBLEM REVIEW\n"
      << "schema: "
      << record.schema_version
      << "\n"
      << "review id: "
      << record.review_id
      << "\n"
      << "reviewer: "
      << record.reviewer_ref
      << "\n"
      << "analysis id: "
      << record.analysis_id
      << "\n"
      << "cluster: "
      << record.cluster_key
      << "\n"
      << "context: "
      << record.context_key
      << "\n"
      << "data scope: "
      << record.data_scope_key
      << "\n"
      << "diagnostic: "
      << record.diagnostic_code
      << "\n"
      << "analysis status: "
      << cluster_problem_analysis_status_key(
             record.analysis_status)
      << "\n"
      << "analysis evidence revision: "
      << record.analysis_evidence_revision
      << "\n"
      << "cluster revision at review: "
      << record.cluster_revision_at_review
      << "\n"
      << "decision: "
      << cluster_problem_review_decision_key(
             record.decision)
      << "\n"
      << "prior state: "
      << diagnostics::investigation_state_key(
             record.prior_state)
      << "\n"
      << "resulting state: "
      << diagnostics::investigation_state_key(
             record.resulting_state)
      << "\n"
      << "state changed: "
      << (record.state_changed
              ? "yes"
              : "no")
      << "\n"
      << "rationale: "
      << record.rationale
      << "\n\n";

  output
      << "refresh\n"
      << "  requested: "
      << (record.refresh_job_requested
              ? "yes"
              : "no")
      << "\n";

  if (record.refresh_job_requested) {
    output
        << "  job id: "
        << record.refresh_job_id
        << "\n"
        << "  from revision: "
        << record.refresh_from_revision
        << "\n"
        << "  to revision: "
        << record.refresh_to_revision
        << "\n";
  }

  output
      << "\n"
      << "downstream boundary\n"
      << "  data review request created: "
      << (record.data_review_request_created
              ? "yes"
              : "no")
      << "\n"
      << "  tester question created: "
      << (record.tester_question_created
              ? "yes"
              : "no")
      << "\n"
      << "  hypothesis proposal created: "
      << (record.hypothesis_proposal_created
              ? "yes"
              : "no")
      << "\n"
      << "  preference hypothesis created: "
      << (record.preference_hypothesis_created
              ? "yes"
              : "no")
      << "\n"
      << "  learning gate invoked: "
      << (record.learning_gate_invoked
              ? "yes"
              : "no")
      << "\n"
      << "  production application allowed: "
      << (record.production_application_allowed
              ? "yes"
              : "no")
      << "\n"
      << "  evidence scope promotion allowed: "
      << (record.evidence_scope_promotion_allowed
              ? "yes"
              : "no")
      << "\n";

  return output.str();
}

}  // namespace routing::core::intelligence
