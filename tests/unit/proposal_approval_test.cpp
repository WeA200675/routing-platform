#include <cassert>
#include <iostream>
#include <stdexcept>
#include <string>

#include "routing/core/intelligence/proposal_approval.hpp"
#include "routing/core/intelligence/proposal_approval_report.hpp"

namespace {

routing::core::intelligence::ReviewedAnalysisOutcomeRecord
make_data_outcome(
    const std::string& outcome_id =
        "outcome:data:1") {
  using namespace routing::core::diagnostics;
  using namespace routing::core::intelligence;

  ReviewedAnalysisOutcomeRecord outcome;

  outcome.outcome_id =
      outcome_id;

  outcome.source_review_id =
      "review:data:1";

  outcome.source_analysis_id =
      "analysis:data:1";

  outcome.review_reviewer_ref =
      "reviewer:alpha";

  outcome.outcome_reviewer_ref =
      "reviewer:beta";

  outcome.cluster_key =
      "cluster:data:1";

  outcome.context_key =
      "context:data:1";

  outcome.data_scope_key =
      "local-only";

  outcome.diagnostic_code =
      "DATA_COVERAGE_URBAN_LOW";

  outcome.evidence_revision =
      2;

  outcome.kind =
      ReviewedAnalysisOutcomeKind::
          DataReviewCandidate;

  outcome.rationale =
      "Review source data.";


  DataReviewCandidate candidate;

  candidate.id =
      "data-review-v1|" +
      outcome_id;

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

  candidate.domain =
      ClusterProblemDomain::DataQuality;

  candidate.severity =
      DiagnosticSeverity::Warning;

  candidate.review_target_key =
      "source-data";

  candidate.reviewer_ref =
      outcome.outcome_reviewer_ref;

  candidate.rationale =
      outcome.rationale;

  outcome.data_review_candidate =
      candidate;

  return outcome;
}

}  // namespace


int main() {
  using namespace routing::core::intelligence;

  const auto outcome =
      make_data_outcome();

  ProposalApprovalWorkflow workflow;

  ProposalApprovalRequest request;

  request.approval_id =
      "approval:data:1";

  request.approver_ref =
      "approver:alpha";

  request.decision =
      ProposalApprovalDecision::Approve;

  request.rationale =
      "Approve manual source-data review only.";


  const auto approved =
      workflow.apply(
          outcome,
          request);

  assert(
      approved.status ==
      ProposalApprovalApplyStatus::Created);

  assert(
      workflow.records().size() == 1);

  assert(
      approved.record.data_review_task.has_value());

  assert(
      !approved.record.question_delivery_candidate.has_value());

  assert(
      !approved.record.hypothesis_conversion_candidate.has_value());


  const auto& task =
      *approved.record.data_review_task;

  assert(
      task.manual_review_authorized);

  assert(
      task.data_scope_key ==
      "local-only");

  assert(
      task.review_target_key ==
      "source-data");

  assert(
      !task.map_change_allowed);

  assert(
      !task.routing_change_allowed);

  assert(
      !task.automatic_publish_allowed);

  assert(
      !task.evidence_scope_promotion_allowed);


  // Exact approval request is idempotent.
  const auto duplicate =
      workflow.apply(
          outcome,
          request);

  assert(
      duplicate.status ==
      ProposalApprovalApplyStatus::
          DuplicateIgnored);

  assert(
      workflow.records().size() == 1);


  // Same approval id may not acquire different meaning.
  auto collision =
      request;

  collision.decision =
      ProposalApprovalDecision::Reject;

  bool collision_rejected =
      false;

  try {
    (void)workflow.apply(
        outcome,
        collision);
  } catch (const std::invalid_argument&) {
    collision_rejected =
        true;
  }

  assert(
      collision_rejected);


  // The outcome itself is terminally reviewed once.
  auto second_approval =
      request;

  second_approval.approval_id =
      "approval:data:second";

  second_approval.approver_ref =
      "approver:beta";

  bool second_rejected =
      false;

  try {
    (void)workflow.apply(
        outcome,
        second_approval);
  } catch (const std::logic_error&) {
    second_rejected =
        true;
  }

  assert(
      second_rejected);


  const std::string report =
      format_proposal_approval_report(
          approved.record);

  assert(
      report.find(
          "PROPOSAL APPROVAL") !=
      std::string::npos);

  assert(
      report.find(
          "decision: approve") !=
      std::string::npos);

  assert(
      report.find(
          "DATA REVIEW TASK") !=
      std::string::npos);

  assert(
      report.find(
          "map change allowed: no") !=
      std::string::npos);

  assert(
      report.find(
          "routing change allowed: no") !=
      std::string::npos);


  std::cout
      << "Proposal approval tests passed\n";

  return 0;
}
