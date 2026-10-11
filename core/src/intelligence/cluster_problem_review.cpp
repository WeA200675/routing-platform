#include "routing/core/intelligence/cluster_problem_review.hpp"

#include <stdexcept>
#include <string>
#include <utility>
#include <vector>

namespace routing::core::intelligence {

namespace {

using diagnostics::AnomalyCluster;
using diagnostics::InvestigationCandidate;
using diagnostics::InvestigationState;


bool is_advanced_state(
    const InvestigationState state) {
  return
      state == InvestigationState::HypothesisProposed ||
      state == InvestigationState::Resolved ||
      state == InvestigationState::Dismissed;
}


void validate_request(
    const ClusterProblemReviewRequest& request) {
  if (request.review_id.empty()) {
    throw std::invalid_argument(
        "Cluster problem review requires review_id.");
  }

  if (request.reviewer_ref.empty()) {
    throw std::invalid_argument(
        "Cluster problem review requires reviewer_ref.");
  }

  if (request.rationale.empty()) {
    throw std::invalid_argument(
        "Cluster problem review requires rationale.");
  }
}


void validate_analysis(
    const ClusterProblemAnalysisResult& analysis) {
  if (analysis.schema_version !=
      kClusterProblemAnalysisSchemaVersion) {
    throw std::invalid_argument(
        "Unsupported cluster problem analysis schema.");
  }

  if (analysis.analysis_id.empty() ||
      analysis.job_id.empty() ||
      analysis.cluster_key.empty() ||
      analysis.context_key.empty() ||
      analysis.data_scope_key.empty() ||
      analysis.diagnostic_code.empty()) {
    throw std::invalid_argument(
        "Cluster problem review requires complete analysis identity.");
  }

  if (analysis.evidence_revision == 0 ||
      analysis.observed_cluster_revision == 0) {
    throw std::invalid_argument(
        "Cluster problem review requires non-zero evidence revision.");
  }
}


void validate_analysis_against_cluster(
    const ClusterProblemAnalysisResult& analysis,
    const AnomalyCluster& cluster) {
  if (analysis.cluster_key !=
      cluster.cluster_key) {
    throw std::invalid_argument(
        "Review analysis/cluster identity mismatch.");
  }

  if (analysis.context_key !=
      cluster.context_key) {
    throw std::invalid_argument(
        "Review analysis/cluster context mismatch.");
  }

  if (analysis.diagnostic_code !=
      cluster.diagnostic_code) {
    throw std::invalid_argument(
        "Review analysis/cluster diagnostic mismatch.");
  }

  const std::string scope_key{
      diagnostics::diagnostic_evidence_scope_key(
          cluster.evidence_scope)};

  if (analysis.data_scope_key !=
      scope_key) {
    throw std::invalid_argument(
        "Review analysis/cluster evidence scope mismatch.");
  }

  const std::uint64_t current_revision =
      static_cast<std::uint64_t>(
          cluster.observation_ids.size());

  if (current_revision == 0) {
    throw std::invalid_argument(
        "Review requires cluster evidence.");
  }

  if (analysis.observed_cluster_revision >
      current_revision) {
    throw std::invalid_argument(
        "Review analysis observes evidence newer than current cluster.");
  }
}


bool same_request_identity(
    const ClusterProblemReviewRecord& existing,
    const ClusterProblemAnalysisResult& analysis,
    const ClusterProblemReviewRequest& request) {
  return
      existing.analysis_id ==
          analysis.analysis_id &&
      existing.reviewer_ref ==
          request.reviewer_ref &&
      existing.decision ==
          request.decision &&
      existing.rationale ==
          request.rationale;
}


InvestigationCandidate
current_refresh_candidate(
    const AnomalyCluster& cluster) {
  std::vector<AnomalyCluster>
      snapshot;

  snapshot.push_back(
      cluster);

  auto candidates =
      diagnostics::build_investigation_candidates(
          snapshot);

  if (candidates.size() != 1 ||
      candidates.front().cluster_key !=
          cluster.cluster_key) {
    throw std::logic_error(
        "Current cluster no longer qualifies for investigation refresh.");
  }

  return
      candidates.front();
}


void set_state(
    diagnostics::AnomalyTracker& tracker,
    AnomalyCluster& cluster,
    const InvestigationState target,
    ClusterProblemReviewRecord& record) {
  if (cluster.state ==
      target) {
    record.resulting_state =
        target;

    return;
  }

  if (!tracker.set_investigation_state(
          cluster.cluster_key,
          target)) {
    throw std::logic_error(
        "Could not update investigation state.");
  }

  record.state_changed =
      true;

  record.resulting_state =
      target;
}


}  // namespace


const ClusterProblemReviewRecord*
ClusterProblemReviewWorkflow::find_record(
    const std::string_view review_id) const {
  for (const auto& record :
       records_) {
    if (record.review_id ==
        review_id) {
      return &record;
    }
  }

  return nullptr;
}


ClusterProblemReviewApplyResult
ClusterProblemReviewWorkflow::apply(
    diagnostics::AnomalyTracker& tracker,
    IntelligenceJobQueue& queue,
    const ClusterProblemAnalysisResult& analysis,
    const ClusterProblemReviewRequest& request) {
  validate_request(
      request);

  validate_analysis(
      analysis);


  // -------------------------------------------------------------
  // Idempotency first.
  //
  // A repeated identical review request is safe even when the first
  // application already changed workflow state.
  // -------------------------------------------------------------

  if (const auto* existing =
          find_record(
              request.review_id);
      existing != nullptr) {
    if (!same_request_identity(
            *existing,
            analysis,
            request)) {
      throw std::invalid_argument(
          "Review id collision with different review identity.");
    }

    return {
        ClusterProblemReviewApplyStatus::
            DuplicateIgnored,
        *existing,
    };
  }


  auto* cluster =
      tracker.find(
          analysis.cluster_key);

  if (cluster == nullptr) {
    throw std::invalid_argument(
        "Review references unknown anomaly cluster.");
  }

  validate_analysis_against_cluster(
      analysis,
      *cluster);


  if (analysis.status ==
      ClusterProblemAnalysisStatus::
          SupersededByWorkflowState) {
    throw std::logic_error(
        "Superseded analysis cannot drive a new review transition.");
  }


  if (is_advanced_state(
          cluster->state)) {
    throw std::logic_error(
        "Review must not move an advanced investigation state backwards.");
  }


  const std::uint64_t current_revision =
      static_cast<std::uint64_t>(
          cluster->observation_ids.size());


  // -------------------------------------------------------------
  // Decision/revision contract.
  // -------------------------------------------------------------

  if (request.decision ==
      ClusterProblemReviewDecision::
          RefreshAnalysis) {
    if (current_revision <=
        analysis.evidence_revision) {
      throw std::logic_error(
          "RefreshAnalysis requires newer independent evidence.");
    }
  } else {
    // Non-refresh decisions must be made against a current,
    // completed analysis of the exact evidence revision.
    if (analysis.status !=
        ClusterProblemAnalysisStatus::Completed) {
      throw std::logic_error(
          "Only completed current analysis may drive this review decision.");
    }

    if (analysis.evidence_revision !=
            current_revision ||
        analysis.observed_cluster_revision !=
            current_revision) {
      throw std::logic_error(
          "Analysis became stale before review; refresh is required.");
    }
  }


  ClusterProblemReviewRecord record;

  record.review_id =
      request.review_id;

  record.reviewer_ref =
      request.reviewer_ref;

  record.analysis_id =
      analysis.analysis_id;

  record.job_id =
      analysis.job_id;

  record.cluster_key =
      analysis.cluster_key;

  record.context_key =
      analysis.context_key;

  record.data_scope_key =
      analysis.data_scope_key;

  record.diagnostic_code =
      analysis.diagnostic_code;

  record.analysis_evidence_revision =
      analysis.evidence_revision;

  record.cluster_revision_at_review =
      current_revision;

  record.analysis_status =
      analysis.status;

  record.decision =
      request.decision;

  record.prior_state =
      cluster->state;

  record.resulting_state =
      cluster->state;

  record.rationale =
      request.rationale;


  // -------------------------------------------------------------
  // Explicit review transition.
  // -------------------------------------------------------------

  switch (request.decision) {
    case ClusterProblemReviewDecision::Acknowledge:
      set_state(
          tracker,
          *cluster,
          InvestigationState::Investigating,
          record);
      break;


    case ClusterProblemReviewDecision::
        NeedsMoreEvidence:
      // NeedsMoreEvidence is preserved as the explicit review decision,
      // while the coarse cluster lifecycle remains Investigating.
      set_state(
          tracker,
          *cluster,
          InvestigationState::Investigating,
          record);
      break;


    case ClusterProblemReviewDecision::
        RefreshAnalysis: {
      auto fresh_candidate =
          current_refresh_candidate(
              *cluster);

      // Explicit review starts/continues investigation.
      // Set it on the job candidate before construction so scheduling
      // priority reflects active investigation.
      fresh_candidate.state =
          InvestigationState::Investigating;

      auto refreshed_job =
          make_cluster_problem_job(
              fresh_candidate);

      refreshed_job.reason_key =
          "diagnostic.investigation.review_refresh";

      if (refreshed_job.id !=
          analysis.job_id) {
        throw std::logic_error(
            "Refresh job changed stable ClusterProblem identity.");
      }

      const auto refresh =
          queue.reopen_terminal_for_new_evidence(
              std::move(refreshed_job));

      record.refresh_job_requested =
          true;

      record.refresh_job_id =
          refresh.id;

      record.refresh_from_revision =
          refresh.previous_evidence_revision;

      record.refresh_to_revision =
          refresh.evidence_revision;

      set_state(
          tracker,
          *cluster,
          InvestigationState::Investigating,
          record);

      break;
    }


    case ClusterProblemReviewDecision::Resolve:
      set_state(
          tracker,
          *cluster,
          InvestigationState::Resolved,
          record);
      break;


    case ClusterProblemReviewDecision::Dismiss:
      set_state(
          tracker,
          *cluster,
          InvestigationState::Dismissed,
          record);
      break;
  }


  records_.push_back(
      record);

  return {
      ClusterProblemReviewApplyStatus::Applied,
      std::move(record),
  };
}

}  // namespace routing::core::intelligence
