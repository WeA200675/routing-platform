#include <cassert>
#include <iostream>
#include <stdexcept>
#include <string>

#include "routing/core/diagnostics/anomaly_tracker.hpp"
#include "routing/core/diagnostics/investigation_candidate.hpp"
#include "routing/core/intelligence/cluster_problem_analysis.hpp"
#include "routing/core/intelligence/cluster_problem_review.hpp"
#include "routing/core/intelligence/diagnostic_investigation_job.hpp"

namespace {

routing::core::diagnostics::DiagnosticEvidenceRecord
make_record(
    const std::string& record_id,
    const std::string& observation_id,
    const std::string& route_id) {
  using namespace routing::core;
  using namespace routing::core::diagnostics;

  DiagnosticEvidenceRecord record;

  record.record_id =
      record_id;

  record.source =
      DiagnosticEvidenceSource::RegressionCase;

  record.evidence_scope =
      DiagnosticEvidenceScope::LocalOnly;

  record.observation_id =
      observation_id;

  record.source_ref =
      "regression:review-test";

  record.context_key =
      "context:review-test";

  record.version_ref =
      "fixture:v1";

  record.diagnostic.code =
      "DATA_COVERAGE_URBAN_LOW";

  record.diagnostic.severity =
      DiagnosticSeverity::Warning;

  record.diagnostic.category =
      DiagnosticCategory::DataCoverage;

  record.diagnostic.scope =
      DiagnosticScope::Route;

  record.diagnostic.family =
      CandidateFamily::ProfileOptimal;

  record.diagnostic.route_id =
      route_id;

  record.diagnostic.explanation_key =
      "diagnostic.data.coverage.urban_low";

  return record;
}

}  // namespace


int main() {
  using namespace routing::core::diagnostics;
  using namespace routing::core::intelligence;

  AnomalyTracker tracker;

  tracker.ingest(
      make_record(
          "record:1",
          "observation:1",
          "route-a"));

  tracker.ingest(
      make_record(
          "record:2",
          "observation:2",
          "route-b"));

  auto candidates =
      build_investigation_candidates(
          tracker.clusters());

  assert(
      candidates.size() == 1);

  const auto candidate =
      candidates.front();

  auto* cluster =
      tracker.find(
          candidate.cluster_key);

  assert(cluster != nullptr);

  IntelligenceJob job =
      make_cluster_problem_job(
          candidate);

  job.state =
      IntelligenceJobState::Running;

  const auto analysis =
      analyze_cluster_problem(
          job,
          candidate,
          *cluster);

  assert(
      analysis.status ==
      ClusterProblemAnalysisStatus::Completed);


  IntelligenceJobQueue queue;
  ClusterProblemReviewWorkflow workflow;


  // -------------------------------------------------------------
  // ACKNOWLEDGE
  // -------------------------------------------------------------

  ClusterProblemReviewRequest acknowledge;

  acknowledge.review_id =
      "review:ack:1";

  acknowledge.reviewer_ref =
      "tester:alpha";

  acknowledge.decision =
      ClusterProblemReviewDecision::Acknowledge;

  acknowledge.rationale =
      "Observed repeatedly and accepted for investigation.";

  const auto acknowledged =
      workflow.apply(
          tracker,
          queue,
          analysis,
          acknowledge);

  assert(
      acknowledged.status ==
      ClusterProblemReviewApplyStatus::Applied);

  assert(
      acknowledged.record.prior_state ==
      InvestigationState::Observed);

  assert(
      acknowledged.record.resulting_state ==
      InvestigationState::Investigating);

  assert(
      acknowledged.record.state_changed);

  cluster =
      tracker.find(
          candidate.cluster_key);

  assert(cluster != nullptr);

  assert(
      cluster->state ==
      InvestigationState::Investigating);


  // Same request id is exactly idempotent.
  const auto duplicate =
      workflow.apply(
          tracker,
          queue,
          analysis,
          acknowledge);

  assert(
      duplicate.status ==
      ClusterProblemReviewApplyStatus::
          DuplicateIgnored);

  assert(
      workflow.records().size() == 1);


  // Same review id may never mean something else.
  auto collision =
      acknowledge;

  collision.decision =
      ClusterProblemReviewDecision::Resolve;

  bool collision_rejected =
      false;

  try {
    (void)workflow.apply(
        tracker,
        queue,
        analysis,
        collision);
  } catch (const std::invalid_argument&) {
    collision_rejected =
        true;
  }

  assert(
      collision_rejected);


  // -------------------------------------------------------------
  // NEEDS MORE EVIDENCE
  // -------------------------------------------------------------

  ClusterProblemReviewRequest needs_more;

  needs_more.review_id =
      "review:more:1";

  needs_more.reviewer_ref =
      "tester:alpha";

  needs_more.decision =
      ClusterProblemReviewDecision::
          NeedsMoreEvidence;

  needs_more.rationale =
      "Keep investigating but collect more independent observations.";

  const auto more =
      workflow.apply(
          tracker,
          queue,
          analysis,
          needs_more);

  assert(
      more.status ==
      ClusterProblemReviewApplyStatus::Applied);

  // Explicit decision is retained, while coarse lifecycle stays
  // Investigating.
  assert(
      more.record.decision ==
      ClusterProblemReviewDecision::
          NeedsMoreEvidence);

  assert(
      more.record.resulting_state ==
      InvestigationState::Investigating);

  assert(
      !more.record.state_changed);


  // -------------------------------------------------------------
  // RESOLVE
  // -------------------------------------------------------------

  ClusterProblemReviewRequest resolve;

  resolve.review_id =
      "review:resolve:1";

  resolve.reviewer_ref =
      "tester:alpha";

  resolve.decision =
      ClusterProblemReviewDecision::Resolve;

  resolve.rationale =
      "Reviewed and considered resolved for this context.";

  const auto resolved =
      workflow.apply(
          tracker,
          queue,
          analysis,
          resolve);

  assert(
      resolved.record.resulting_state ==
      InvestigationState::Resolved);

  assert(
      resolved.record.state_changed);

  cluster =
      tracker.find(
          candidate.cluster_key);

  assert(cluster != nullptr);

  assert(
      cluster->state ==
      InvestigationState::Resolved);


  // No later review may silently move terminal workflow state
  // backwards to Investigating.
  ClusterProblemReviewRequest backwards;

  backwards.review_id =
      "review:backwards";

  backwards.reviewer_ref =
      "tester:alpha";

  backwards.decision =
      ClusterProblemReviewDecision::Acknowledge;

  backwards.rationale =
      "This must be rejected.";

  bool backwards_rejected =
      false;

  try {
    (void)workflow.apply(
        tracker,
        queue,
        analysis,
        backwards);
  } catch (const std::logic_error&) {
    backwards_rejected =
        true;
  }

  assert(
      backwards_rejected);

  assert(
      workflow.records().size() == 3);

  std::cout
      << "Cluster problem review tests passed\n";

  return 0;
}
