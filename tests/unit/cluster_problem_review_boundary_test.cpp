#include <cassert>
#include <iostream>
#include <stdexcept>
#include <string>

#include "routing/core/diagnostics/anomaly_tracker.hpp"
#include "routing/core/diagnostics/investigation_candidate.hpp"
#include "routing/core/intelligence/cluster_problem_analysis.hpp"
#include "routing/core/intelligence/cluster_problem_review.hpp"
#include "routing/core/intelligence/cluster_problem_review_report.hpp"
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
      DiagnosticEvidenceSource::DriveSession;

  record.evidence_scope =
      DiagnosticEvidenceScope::Personal;

  record.observation_id =
      observation_id;

  record.source_ref =
      "drive:personal-review";

  record.context_key =
      "personal:corridor:test";

  record.version_ref =
      "session:v1";

  record.diagnostic.code =
      "DATA_URBAN_POSITIVE_SIGNAL_ABSENT";

  record.diagnostic.severity =
      DiagnosticSeverity::Info;

  record.diagnostic.category =
      DiagnosticCategory::DataSignal;

  record.diagnostic.scope =
      DiagnosticScope::Route;

  record.diagnostic.family =
      CandidateFamily::ProfileOptimal;

  record.diagnostic.route_id =
      route_id;

  record.diagnostic.explanation_key =
      "diagnostic.data.urban.positive_signal_absent";

  return record;
}

}  // namespace


int main() {
  using namespace routing::core::diagnostics;
  using namespace routing::core::intelligence;


  // INFO investigation requires three independent observations.
  AnomalyTracker tracker;

  tracker.ingest(
      make_record(
          "personal:1",
          "observation:1",
          "route-a"));

  tracker.ingest(
      make_record(
          "personal:2",
          "observation:2",
          "route-b"));

  tracker.ingest(
      make_record(
          "personal:3",
          "observation:3",
          "route-c"));


  const auto candidates =
      build_investigation_candidates(
          tracker.clusters());

  assert(
      candidates.size() == 1);

  const auto candidate =
      candidates.front();

  assert(
      candidate.evidence_scope ==
      DiagnosticEvidenceScope::Personal);


  const auto* cluster =
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

  assert(
      analysis.data_scope_key ==
      "personal");


  IntelligenceJobQueue queue;
  ClusterProblemReviewWorkflow workflow;


  // -------------------------------------------------------------
  // NEEDS MORE EVIDENCE creates no downstream artifact/job.
  // -------------------------------------------------------------

  ClusterProblemReviewRequest needs_more;

  needs_more.review_id =
      "review:personal:more";

  needs_more.reviewer_ref =
      "tester:alpha";

  needs_more.decision =
      ClusterProblemReviewDecision::
          NeedsMoreEvidence;

  needs_more.rationale =
      "Keep this personal observation under review.";

  const auto more =
      workflow.apply(
          tracker,
          queue,
          analysis,
          needs_more);

  assert(
      more.record.data_scope_key ==
      "personal");

  assert(
      !more.record.refresh_job_requested);

  assert(
      queue.size() == 0);


  // No downstream semantic action is manufactured.
  assert(
      !more.record.data_review_request_created);

  assert(
      !more.record.tester_question_created);

  assert(
      !more.record.hypothesis_proposal_created);

  assert(
      !more.record.preference_hypothesis_created);

  assert(
      !more.record.learning_gate_invoked);

  assert(
      !more.record.production_application_allowed);

  assert(
      !more.record.evidence_scope_promotion_allowed);


  // Refresh without newer evidence is forbidden.
  ClusterProblemReviewRequest invalid_refresh;

  invalid_refresh.review_id =
      "review:personal:refresh";

  invalid_refresh.reviewer_ref =
      "tester:alpha";

  invalid_refresh.decision =
      ClusterProblemReviewDecision::
          RefreshAnalysis;

  invalid_refresh.rationale =
      "No newer evidence exists, so this must fail.";

  bool refresh_rejected =
      false;

  try {
    (void)workflow.apply(
        tracker,
        queue,
        analysis,
        invalid_refresh);
  } catch (const std::logic_error&) {
    refresh_rejected =
        true;
  }

  assert(
      refresh_rejected);


  // -------------------------------------------------------------
  // DISMISS is an explicit terminal review decision.
  // -------------------------------------------------------------

  ClusterProblemReviewRequest dismiss;

  dismiss.review_id =
      "review:personal:dismiss";

  dismiss.reviewer_ref =
      "tester:alpha";

  dismiss.decision =
      ClusterProblemReviewDecision::Dismiss;

  dismiss.rationale =
      "No actionable problem established in this personal context.";

  const auto dismissed =
      workflow.apply(
          tracker,
          queue,
          analysis,
          dismiss);

  assert(
      dismissed.record.resulting_state ==
      InvestigationState::Dismissed);

  assert(
      dismissed.record.data_scope_key ==
      "personal");

  assert(
      !dismissed.record.evidence_scope_promotion_allowed);

  assert(
      !dismissed.record.hypothesis_proposal_created);

  assert(
      !dismissed.record.production_application_allowed);


  const std::string report =
      format_cluster_problem_review_report(
          dismissed.record);

  assert(
      report.find(
          "CLUSTER PROBLEM REVIEW") !=
      std::string::npos);

  assert(
      report.find(
          "data scope: personal") !=
      std::string::npos);

  assert(
      report.find(
          "decision: dismiss") !=
      std::string::npos);

  assert(
      report.find(
          "hypothesis proposal created: no") !=
      std::string::npos);

  assert(
      report.find(
          "production application allowed: no") !=
      std::string::npos);

  assert(
      report.find(
          "evidence scope promotion allowed: no") !=
      std::string::npos);


  std::cout
      << "Cluster problem review boundary tests passed\n";

  return 0;
}
