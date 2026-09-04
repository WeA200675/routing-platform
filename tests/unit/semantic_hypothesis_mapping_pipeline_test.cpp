#include <cassert>
#include <iostream>
#include <string>

#include "routing/core/diagnostics/anomaly_tracker.hpp"
#include "routing/core/diagnostics/investigation_candidate.hpp"
#include "routing/core/intelligence/cluster_problem_analysis.hpp"
#include "routing/core/intelligence/cluster_problem_review.hpp"
#include "routing/core/intelligence/diagnostic_investigation_job.hpp"
#include "routing/core/intelligence/proposal_approval.hpp"
#include "routing/core/intelligence/reviewed_analysis_outcome.hpp"
#include "routing/core/intelligence/semantic_hypothesis_mapping.hpp"

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
      "regression:semantic-mapping-pipeline";

  record.context_key =
      "li:vaduz-ruggell";

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


  // -------------------------------------------------------------
  // EVIDENCE -> CLUSTER
  // -------------------------------------------------------------

  AnomalyTracker tracker;

  (void)tracker.ingest(
      make_record(
          "record:1",
          "observation:1",
          "route-a"));

  (void)tracker.ingest(
      make_record(
          "record:2",
          "observation:2",
          "route-b"));

  assert(
      tracker.size() == 1);


  // -------------------------------------------------------------
  // CLUSTER -> INVESTIGATION
  // -------------------------------------------------------------

  const auto candidates =
      build_investigation_candidates(
          tracker.clusters());

  assert(
      candidates.size() == 1);

  const auto candidate =
      candidates.front();


  // -------------------------------------------------------------
  // INVESTIGATION -> JOB
  // -------------------------------------------------------------

  IntelligenceJobQueue queue;

  const auto bridge =
      ensure_cluster_problem_job(
          queue,
          candidate);

  assert(
      bridge.status ==
      DiagnosticInvestigationJobStatus::Added);


  ResourceSnapshot resources;

  resources.navigation_active =
      false;

  resources.device_charging =
      false;

  resources.network_available =
      false;

  resources.battery_percent =
      80;

  resources.thermal_state =
      ThermalState::Nominal;


  const auto claimed =
      queue.claim_next(
          resources);

  assert(
      claimed.has_value());


  // -------------------------------------------------------------
  // JOB -> ANALYSIS
  // -------------------------------------------------------------

  const auto* cluster =
      tracker.find(
          candidate.cluster_key);

  assert(cluster != nullptr);


  const auto analysis =
      analyze_cluster_problem(
          *claimed,
          candidate,
          *cluster);

  assert(
      analysis.status ==
      ClusterProblemAnalysisStatus::Completed);

  assert(
      analysis.domain ==
      ClusterProblemDomain::DataQuality);


  queue.mark_completed(
      claimed->id);


  // -------------------------------------------------------------
  // ANALYSIS -> REVIEW
  // -------------------------------------------------------------

  ClusterProblemReviewWorkflow review_workflow;

  ClusterProblemReviewRequest review_request;

  review_request.review_id =
      "review:semantic-pipeline";

  review_request.reviewer_ref =
      "reviewer:alpha";

  review_request.decision =
      ClusterProblemReviewDecision::Acknowledge;

  review_request.rationale =
      "Acknowledge system investigation.";


  const auto review =
      review_workflow.apply(
          tracker,
          queue,
          analysis,
          review_request);

  assert(
      review.record.resulting_state ==
      InvestigationState::Investigating);


  // -------------------------------------------------------------
  // REVIEW -> HYPOTHESIS PROPOSAL
  // -------------------------------------------------------------

  ReviewedAnalysisOutcomeWorkflow outcome_workflow;

  ReviewedAnalysisOutcomeRequest outcome_request;

  outcome_request.outcome_id =
      "outcome:semantic-pipeline";

  outcome_request.reviewer_ref =
      "reviewer:beta";

  outcome_request.kind =
      ReviewedAnalysisOutcomeKind::
          HypothesisProposal;

  outcome_request.semantic_key =
      "hypothesis.data.urban_coverage_source_gap";

  outcome_request.rationale =
      "Propose a system explanation for explicit review.";


  const auto outcome =
      outcome_workflow.apply(
          analysis,
          review.record,
          outcome_request);

  assert(
      outcome.record.hypothesis_proposal.has_value());


  // -------------------------------------------------------------
  // PROPOSAL -> APPROVED CONVERSION CANDIDATE
  // -------------------------------------------------------------

  ProposalApprovalWorkflow approval_workflow;

  ProposalApprovalRequest approval_request;

  approval_request.approval_id =
      "approval:semantic-pipeline";

  approval_request.approver_ref =
      "approver:alpha";

  approval_request.decision =
      ProposalApprovalDecision::Approve;

  approval_request.rationale =
      "Approve explicit system semantic mapping only.";


  const auto approval =
      approval_workflow.apply(
          outcome.record,
          approval_request);

  assert(
      approval.record.
          hypothesis_conversion_candidate.has_value());


  const HypothesisConversionCandidate& conversion =
      *approval.record.
          hypothesis_conversion_candidate;

  assert(
      conversion.explicit_semantic_mapping_required);

  assert(
      !conversion.preference_target_created);

  assert(
      !conversion.preference_hypothesis_created);


  // -------------------------------------------------------------
  // EXPLICIT SEMANTIC MAPPING -> DATA SOURCE HYPOTHESIS
  // -------------------------------------------------------------

  SemanticHypothesisMappingWorkflow mapping_workflow;

  SemanticHypothesisMappingRequest mapping_request;

  mapping_request.mapping_id =
      "mapping:semantic-pipeline";

  mapping_request.mapper_ref =
      "mapper:alpha";

  mapping_request.decision =
      SemanticHypothesisMappingDecision::Map;

  mapping_request.kind =
      SystemHypothesisKind::DataSource;

  mapping_request.target_key =
      "source:urban-coverage";

  mapping_request.rationale =
      "Explicitly classify this as a data-source hypothesis.";


  const auto mapping =
      mapping_workflow.apply(
          approval.record,
          mapping_request);

  assert(
      mapping.status ==
      SemanticHypothesisMappingApplyStatus::Created);

  assert(
      mapping.record.data_source_hypothesis.has_value());


  const DataSourceHypothesis& system_hypothesis =
      *mapping.record.data_source_hypothesis;

  assert(
      system_hypothesis.data_source_key ==
      "source:urban-coverage");

  assert(
      system_hypothesis.base.data_scope_key ==
      "local-only");

  assert(
      system_hypothesis.base.evidence_revision == 2);

  assert(
      !system_hypothesis.base.preference_interpretation_allowed);

  assert(
      !system_hypothesis.base.preference_target_created);

  assert(
      !system_hypothesis.base.preference_hypothesis_created);

  assert(
      !system_hypothesis.base.learning_gate_invoked);

  assert(
      !system_hypothesis.base.shadow_evaluation_created);

  assert(
      !system_hypothesis.base.automatic_fix_allowed);

  assert(
      !system_hypothesis.base.map_change_allowed);

  assert(
      !system_hypothesis.base.routing_change_allowed);

  assert(
      !system_hypothesis.base.production_application_allowed);

  assert(
      !system_hypothesis.base.evidence_scope_promotion_allowed);


  // Mapping does not create or mutate intelligence jobs.
  assert(
      queue.size() == 1);

  const auto* completed_job =
      queue.find(
          claimed->id);

  assert(completed_job != nullptr);

  assert(
      completed_job->state ==
      IntelligenceJobState::Completed);


  // Mapping does not advance or resolve the investigation state.
  const auto* mapped_cluster =
      tracker.find(
          candidate.cluster_key);

  assert(mapped_cluster != nullptr);

  assert(
      mapped_cluster->state ==
      InvestigationState::Investigating);


  std::cout
      << "Semantic hypothesis mapping pipeline tests passed\n";

  return 0;
}
