#include <cassert>
#include <iostream>
#include <stdexcept>
#include <string>

#include "routing/core/intelligence/reviewed_analysis_outcome.hpp"
#include "routing/core/intelligence/reviewed_analysis_outcome_report.hpp"

namespace {

routing::core::intelligence::ClusterProblemAnalysisResult
make_analysis() {
  using namespace routing::core::diagnostics;
  using namespace routing::core::intelligence;

  ClusterProblemAnalysisResult analysis;

  analysis.analysis_id =
      "cluster-analysis-v1|cluster:test|revision=2";

  analysis.job_id =
      "cluster-problem-v1|cluster:test";

  analysis.cluster_key =
      "cluster:test";

  analysis.context_key =
      "context:test";

  analysis.data_scope_key =
      "local-only";

  analysis.diagnostic_code =
      "DATA_COVERAGE_URBAN_LOW";

  analysis.evidence_revision =
      2;

  analysis.observed_cluster_revision =
      2;

  analysis.status =
      ClusterProblemAnalysisStatus::Completed;

  analysis.domain =
      ClusterProblemDomain::DataQuality;

  analysis.severity =
      DiagnosticSeverity::Warning;

  analysis.next_actions = {
      ClusterProblemNextAction::ReviewSourceData,
  };

  return analysis;
}


routing::core::intelligence::ClusterProblemReviewRecord
make_review(
    const routing::core::intelligence::
        ClusterProblemAnalysisResult& analysis) {
  using namespace routing::core::diagnostics;
  using namespace routing::core::intelligence;

  ClusterProblemReviewRecord review;

  review.review_id =
      "review:data:1";

  review.reviewer_ref =
      "reviewer:alpha";

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
      ClusterProblemReviewDecision::Acknowledge;

  review.prior_state =
      InvestigationState::Observed;

  review.resulting_state =
      InvestigationState::Investigating;

  review.state_changed =
      true;

  review.rationale =
      "Repeated data-quality observation acknowledged.";

  return review;
}

}  // namespace


int main() {
  using namespace routing::core::intelligence;

  const auto analysis =
      make_analysis();

  const auto review =
      make_review(
          analysis);

  ReviewedAnalysisOutcomeWorkflow workflow;

  ReviewedAnalysisOutcomeRequest request;

  request.outcome_id =
      "outcome:data-review:1";

  request.reviewer_ref =
      "reviewer:beta";

  request.kind =
      ReviewedAnalysisOutcomeKind::
          DataReviewCandidate;

  request.rationale =
      "Review source data before considering any semantic change.";


  const auto created =
      workflow.apply(
          analysis,
          review,
          request);

  assert(
      created.status ==
      ReviewedAnalysisOutcomeApplyStatus::Created);

  assert(
      workflow.records().size() == 1);

  assert(
      created.record.data_review_candidate.has_value());

  assert(
      !created.record.tester_question_proposal.has_value());

  assert(
      !created.record.hypothesis_proposal.has_value());


  const auto& candidate =
      *created.record.data_review_candidate;

  assert(
      candidate.data_scope_key ==
      "local-only");

  assert(
      candidate.review_target_key ==
      "source-data");

  assert(
      candidate.domain ==
      ClusterProblemDomain::DataQuality);

  assert(
      candidate.evidence_revision == 2);

  assert(
      !candidate.map_change_allowed);

  assert(
      !candidate.routing_change_allowed);

  assert(
      !candidate.automatic_publish_allowed);

  assert(
      !candidate.evidence_scope_promotion_allowed);


  // Exact request is idempotent.
  const auto duplicate =
      workflow.apply(
          analysis,
          review,
          request);

  assert(
      duplicate.status ==
      ReviewedAnalysisOutcomeApplyStatus::
          DuplicateIgnored);

  assert(
      workflow.records().size() == 1);


  // Same id may not alias another semantic outcome.
  auto collision =
      request;

  collision.kind =
      ReviewedAnalysisOutcomeKind::
          HypothesisProposal;

  collision.semantic_key =
      "hypothesis:test";

  bool collision_rejected =
      false;

  try {
    (void)workflow.apply(
        analysis,
        review,
        collision);
  } catch (const std::invalid_argument&) {
    collision_rejected =
        true;
  }

  assert(
      collision_rejected);


  const std::string report =
      format_reviewed_analysis_outcome_report(
          created.record);

  assert(
      report.find(
          "REVIEWED ANALYSIS OUTCOME") !=
      std::string::npos);

  assert(
      report.find(
          "kind: data-review-candidate") !=
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
      << "Reviewed analysis outcome tests passed\n";

  return 0;
}
