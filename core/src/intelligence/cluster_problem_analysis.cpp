#include "routing/core/intelligence/cluster_problem_analysis.hpp"

#include <algorithm>
#include <cstddef>
#include <stdexcept>
#include <string>
#include <utility>

namespace routing::core::intelligence {

namespace {

using diagnostics::DiagnosticCategory;
using diagnostics::DiagnosticEvidence;
using diagnostics::DiagnosticEvidenceScope;
using diagnostics::InvestigationState;


bool advanced_workflow_state(
    const InvestigationState state) {
  return
      state == InvestigationState::HypothesisProposed ||
      state == InvestigationState::Resolved ||
      state == InvestigationState::Dismissed;
}


void add_action_once(
    std::vector<ClusterProblemNextAction>& actions,
    const ClusterProblemNextAction action) {
  if (std::find(
          actions.begin(),
          actions.end(),
          action) !=
      actions.end()) {
    return;
  }

  actions.push_back(
      action);
}


DiagnosticEvidence count_evidence(
    std::string key,
    const std::uint64_t value) {
  DiagnosticEvidence evidence;

  evidence.key =
      std::move(key);

  evidence.value =
      static_cast<double>(
          value);

  evidence.unit =
      "count";

  return evidence;
}


ClusterProblemFinding evidence_summary_finding(
    const diagnostics::InvestigationCandidate& candidate,
    const diagnostics::AnomalyCluster& cluster,
    const std::uint64_t job_revision) {
  ClusterProblemFinding finding;

  finding.code =
      "ANALYSIS_EVIDENCE_SUMMARY";

  finding.explanation_key =
      "analysis.cluster.evidence_summary";

  finding.detail =
      "The analysis records independent observations separately "
      "from route-level diagnostic occurrences.";

  finding.evidence.push_back(
      count_evidence(
          "job_evidence_revision",
          job_revision));

  finding.evidence.push_back(
      count_evidence(
          "candidate_independent_observations",
          static_cast<std::uint64_t>(
              candidate.observation_count)));

  finding.evidence.push_back(
      count_evidence(
          "cluster_independent_observations",
          static_cast<std::uint64_t>(
              cluster.observation_ids.size())));

  finding.evidence.push_back(
      count_evidence(
          "diagnostic_occurrences",
          cluster.occurrence_count));

  finding.evidence.push_back(
      count_evidence(
          "retained_evidence_samples",
          static_cast<std::uint64_t>(
              cluster.evidence_samples.size())));

  return finding;
}


ClusterProblemFinding hypothesis_boundary_finding() {
  ClusterProblemFinding finding;

  finding.code =
      "ANALYSIS_PREFERENCE_HYPOTHESIS_BOUNDARY";

  finding.explanation_key =
      "analysis.cluster.preference_hypothesis_boundary";

  finding.detail =
      "Diagnostic anomaly evidence is not explicit preference "
      "feedback and does not create a PreferenceHypothesis.";

  return finding;
}


ClusterProblemDomain classify_domain(
    const DiagnosticCategory category) {
  switch (category) {
    case DiagnosticCategory::Backend:
    case DiagnosticCategory::Enrichment:
      return
          ClusterProblemDomain::BackendReliability;

    case DiagnosticCategory::DataCoverage:
    case DiagnosticCategory::DataSignal:
      return
          ClusterProblemDomain::DataQuality;

    case DiagnosticCategory::CandidateSet:
      return
          ClusterProblemDomain::CandidatePipeline;
  }

  return
      ClusterProblemDomain::Unknown;
}


ClusterProblemFinding domain_finding(
    const ClusterProblemDomain domain,
    const diagnostics::AnomalyCluster& cluster) {
  ClusterProblemFinding finding;

  switch (domain) {
    case ClusterProblemDomain::DataQuality:
      finding.code =
          "ANALYSIS_DATA_QUALITY_REVIEW";

      finding.explanation_key =
          "analysis.cluster.data_quality_review";

      finding.detail =
          "The repeated diagnostic is a data-quality observation. "
          "Review source/enrichment data before inferring routing "
          "preferences or changing production behavior.";
      break;

    case ClusterProblemDomain::BackendReliability:
      finding.code =
          "ANALYSIS_BACKEND_RELIABILITY_REVIEW";

      finding.explanation_key =
          "analysis.cluster.backend_reliability_review";

      finding.detail =
          "The repeated diagnostic concerns backend or enrichment "
          "reliability and should be reviewed in that subsystem.";
      break;

    case ClusterProblemDomain::CandidatePipeline:
      finding.code =
          "ANALYSIS_CANDIDATE_PIPELINE_REVIEW";

      finding.explanation_key =
          "analysis.cluster.candidate_pipeline_review";

      finding.detail =
          "The repeated diagnostic concerns candidate generation, "
          "evaluation availability or orchestration behavior.";
      break;

    case ClusterProblemDomain::Unknown:
      finding.code =
          "ANALYSIS_DOMAIN_UNCLASSIFIED";

      finding.explanation_key =
          "analysis.cluster.domain_unclassified";

      finding.detail =
          "The diagnostic category has no dedicated analysis domain.";
      break;
  }

  finding.evidence.push_back(
      count_evidence(
          "affected_routes",
          static_cast<std::uint64_t>(
              cluster.affected_route_ids.size())));

  finding.evidence.push_back(
      count_evidence(
          "affected_families",
          static_cast<std::uint64_t>(
              cluster.affected_families.size())));

  finding.evidence.push_back(
      count_evidence(
          "source_refs",
          static_cast<std::uint64_t>(
              cluster.source_refs.size())));

  return finding;
}


void validate_same_subject(
    const IntelligenceJob& job,
    const diagnostics::InvestigationCandidate& candidate,
    const diagnostics::AnomalyCluster& cluster) {
  if (job.type !=
      IntelligenceJobType::ClusterProblem) {
    throw std::invalid_argument(
        "Cluster problem analysis requires a ClusterProblem job.");
  }

  if (job.state !=
      IntelligenceJobState::Running) {
    throw std::invalid_argument(
        "Cluster problem analysis requires a running job.");
  }

  if (job.id.empty()) {
    throw std::invalid_argument(
        "Cluster problem analysis requires job id.");
  }

  if (job.subject_key.empty()) {
    throw std::invalid_argument(
        "Cluster problem analysis requires job subject_key.");
  }

  if (candidate.cluster_key.empty() ||
      cluster.cluster_key.empty()) {
    throw std::invalid_argument(
        "Cluster problem analysis requires cluster identity.");
  }

  if (job.subject_key !=
          candidate.cluster_key ||
      job.subject_key !=
          cluster.cluster_key) {
    throw std::invalid_argument(
        "Cluster problem analysis subject identity mismatch.");
  }

  if (candidate.context_key.empty() ||
      cluster.context_key.empty() ||
      job.context_key.empty()) {
    throw std::invalid_argument(
        "Cluster problem analysis requires context identity.");
  }

  if (job.context_key !=
          candidate.context_key ||
      job.context_key !=
          cluster.context_key) {
    throw std::invalid_argument(
        "Cluster problem analysis context mismatch.");
  }

  const std::string candidate_scope{
      diagnostics::diagnostic_evidence_scope_key(
          candidate.evidence_scope)};

  const std::string cluster_scope{
      diagnostics::diagnostic_evidence_scope_key(
          cluster.evidence_scope)};

  if (job.data_scope_key.empty() ||
      job.data_scope_key != candidate_scope ||
      job.data_scope_key != cluster_scope) {
    throw std::invalid_argument(
        "Cluster problem analysis evidence scope mismatch.");
  }

  if (candidate.diagnostic_code.empty() ||
      cluster.diagnostic_code.empty() ||
      candidate.diagnostic_code !=
          cluster.diagnostic_code) {
    throw std::invalid_argument(
        "Cluster problem analysis diagnostic code mismatch.");
  }

  if (candidate.observation_count == 0 ||
      cluster.observation_ids.empty()) {
    throw std::invalid_argument(
        "Cluster problem analysis requires independent evidence.");
  }

  if (job.evidence_revision == 0) {
    throw std::invalid_argument(
        "Cluster problem analysis requires non-zero evidence revision.");
  }

  if (job.evidence_revision >
      static_cast<std::uint64_t>(
          cluster.observation_ids.size())) {
    throw std::invalid_argument(
        "Cluster problem job revision exceeds available cluster evidence.");
  }
}


ClusterProblemAnalysisResult base_result(
    const IntelligenceJob& job,
    const diagnostics::InvestigationCandidate& candidate,
    const diagnostics::AnomalyCluster& cluster) {
  ClusterProblemAnalysisResult result;

  result.analysis_id =
      std::string(
          "cluster-analysis-v1|") +
      job.subject_key +
      "|revision=" +
      std::to_string(
          job.evidence_revision);

  result.job_id =
      job.id;

  result.cluster_key =
      cluster.cluster_key;

  result.context_key =
      cluster.context_key;

  result.data_scope_key =
      job.data_scope_key;

  result.diagnostic_code =
      cluster.diagnostic_code;

  result.evidence_revision =
      job.evidence_revision;

  result.observed_cluster_revision =
      static_cast<std::uint64_t>(
          cluster.observation_ids.size());

  result.domain =
      classify_domain(
          cluster.category);

  result.severity =
      cluster.max_severity;

  result.findings.push_back(
      evidence_summary_finding(
          candidate,
          cluster,
          job.evidence_revision));

  result.findings.push_back(
      hypothesis_boundary_finding());

  return result;
}


}  // namespace


ClusterProblemAnalysisResult
analyze_cluster_problem(
    const IntelligenceJob& claimed_job,
    const diagnostics::InvestigationCandidate& candidate,
    const diagnostics::AnomalyCluster& cluster) {
  validate_same_subject(
      claimed_job,
      candidate,
      cluster);

  auto result =
      base_result(
          claimed_job,
          candidate,
          cluster);


  // Human/workflow state always wins over a running background analysis.
  if (advanced_workflow_state(
          cluster.state)) {
    result.status =
        ClusterProblemAnalysisStatus::
            SupersededByWorkflowState;

    ClusterProblemFinding finding;

    finding.code =
        "ANALYSIS_SUPERSEDED_BY_WORKFLOW_STATE";

    finding.explanation_key =
        "analysis.cluster.superseded_by_workflow_state";

    finding.detail =
        "The cluster workflow advanced while analysis work was "
        "running. The analysis does not move the workflow backwards.";

    result.findings.push_back(
        std::move(finding));

    return result;
  }


  const auto candidate_revision =
      static_cast<std::uint64_t>(
          candidate.observation_count);

  const auto cluster_revision =
      static_cast<std::uint64_t>(
          cluster.observation_ids.size());

  // Analyse only the evidence revision the job actually represents.
  //
  // Never silently consume newer evidence under an older job revision.
  if (claimed_job.evidence_revision !=
          candidate_revision ||
      claimed_job.evidence_revision !=
          cluster_revision) {
    result.status =
        ClusterProblemAnalysisStatus::
            StaleEvidence;

    ClusterProblemFinding finding;

    finding.code =
        "ANALYSIS_EVIDENCE_REVISION_STALE";

    finding.explanation_key =
        "analysis.cluster.evidence_revision_stale";

    finding.detail =
        "Newer independent evidence exists than the claimed job "
        "revision. A refreshed analysis is required.";

    finding.evidence.push_back(
        count_evidence(
            "job_evidence_revision",
            claimed_job.evidence_revision));

    finding.evidence.push_back(
        count_evidence(
            "candidate_revision",
            candidate_revision));

    finding.evidence.push_back(
        count_evidence(
            "cluster_revision",
            cluster_revision));

    result.findings.push_back(
        std::move(finding));

    add_action_once(
        result.next_actions,
        ClusterProblemNextAction::
            RefreshAnalysis);

    return result;
  }


  result.status =
      ClusterProblemAnalysisStatus::
          Completed;

  result.findings.push_back(
      domain_finding(
          result.domain,
          cluster));

  switch (result.domain) {
    case ClusterProblemDomain::DataQuality:
      add_action_once(
          result.next_actions,
          ClusterProblemNextAction::
              ReviewSourceData);
      break;

    case ClusterProblemDomain::BackendReliability:
      add_action_once(
          result.next_actions,
          ClusterProblemNextAction::
              ReviewBackendOrEnrichment);
      break;

    case ClusterProblemDomain::CandidatePipeline:
      add_action_once(
          result.next_actions,
          ClusterProblemNextAction::
              ReviewCandidatePipeline);
      break;

    case ClusterProblemDomain::Unknown:
      add_action_once(
          result.next_actions,
          ClusterProblemNextAction::
              CollectAdditionalContext);
      break;
  }

  return result;
}

}  // namespace routing::core::intelligence
