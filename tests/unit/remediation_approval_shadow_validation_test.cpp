#include <cassert>
#include <iostream>
#include <stdexcept>
#include <string>
#include <vector>

#include "routing/core/intelligence/remediation_approval_shadow_validation.hpp"
#include "routing/core/intelligence/remediation_approval_shadow_validation_report.hpp"

namespace {

struct Fixture {
  routing::core::intelligence::RemediationProposalRecord proposal;
  routing::core::intelligence::SystemHypothesisEvaluationRecord evaluation;
};


Fixture make_fixture() {
  using namespace routing::core::intelligence;

  Fixture fixture;


  SystemHypothesisEvaluationRecord evaluation;

  evaluation.evaluation_id =
      "evaluation:data:1";

  evaluation.evaluation_revision =
      1;

  evaluation.system_hypothesis_id =
      "system-hypothesis:data:1";

  evaluation.hypothesis_kind =
      SystemHypothesisKind::DataSource;

  evaluation.hypothesis_target_key =
      "source:urban-classification";

  evaluation.source_mapping_id =
      "mapping:data:1";

  evaluation.source_approval_id =
      "proposal-approval:data:1";

  evaluation.source_conversion_candidate_id =
      "conversion:data:1";

  evaluation.source_outcome_id =
      "outcome:data:1";

  evaluation.source_review_id =
      "review:data:1";

  evaluation.source_analysis_id =
      "analysis:data:1";

  evaluation.cluster_key =
      "cluster:data:1";

  evaluation.context_key =
      "context:data:1";

  evaluation.data_scope_key =
      "local-only";

  evaluation.diagnostic_code =
      "DATA_COVERAGE_URBAN_LOW";

  evaluation.hypothesis_evidence_revision =
      2;

  evaluation.source_hypothesis_key =
      "hypothesis.data.urban-source-gap";

  evaluation.evaluator_ref =
      "evaluator:alpha";

  evaluation.result =
      SystemHypothesisEvaluationResult::Supported;


  SystemHypothesisEvidenceReference source_evidence;

  source_evidence.evidence_id =
      "evaluation-evidence:data:1";

  source_evidence.source_ref =
      "regression:source-review";

  source_evidence.data_scope_key =
      evaluation.data_scope_key;

  source_evidence.context_key =
      evaluation.context_key;

  source_evidence.relation =
      SystemHypothesisEvidenceRelation::Supports;

  source_evidence.detail =
      "Reviewed evidence supports the system hypothesis.";

  evaluation.evidence = {
      source_evidence,
  };

  evaluation.supporting_evidence_count =
      1;

  evaluation.rationale =
      "Current reviewed evidence supports the hypothesis.";


  RemediationProposalBase base;

  base.id =
      "remediation-proposal-v1|proposal:data:1";

  base.source_evaluation_id =
      evaluation.evaluation_id;

  base.source_evaluation_revision =
      evaluation.evaluation_revision;

  base.system_hypothesis_id =
      evaluation.system_hypothesis_id;

  base.system_hypothesis_kind =
      evaluation.hypothesis_kind;

  base.hypothesis_target_key =
      evaluation.hypothesis_target_key;

  base.source_mapping_id =
      evaluation.source_mapping_id;

  base.source_approval_id =
      evaluation.source_approval_id;

  base.source_conversion_candidate_id =
      evaluation.source_conversion_candidate_id;

  base.source_outcome_id =
      evaluation.source_outcome_id;

  base.source_review_id =
      evaluation.source_review_id;

  base.source_analysis_id =
      evaluation.source_analysis_id;

  base.cluster_key =
      evaluation.cluster_key;

  base.context_key =
      evaluation.context_key;

  base.data_scope_key =
      evaluation.data_scope_key;

  base.diagnostic_code =
      evaluation.diagnostic_code;

  base.hypothesis_evidence_revision =
      evaluation.hypothesis_evidence_revision;

  base.source_hypothesis_key =
      evaluation.source_hypothesis_key;

  base.evaluator_ref =
      evaluation.evaluator_ref;

  base.evaluation_evidence =
      evaluation.evidence;

  base.supporting_evidence_count =
      evaluation.supporting_evidence_count;

  base.refuting_evidence_count =
      evaluation.refuting_evidence_count;

  base.context_evidence_count =
      evaluation.context_evidence_count;

  base.proposer_ref =
      "proposer:alpha";

  base.remediation_key =
      "remediation.data.review-source-import";

  base.rationale =
      "Propose isolated source-data remediation validation.";


  DataRemediationProposal typed;

  typed.base =
      base;

  typed.data_source_key =
      evaluation.hypothesis_target_key;


  RemediationProposalRecord proposal;

  proposal.proposal_id =
      "proposal:data:1";

  proposal.kind =
      RemediationProposalKind::Data;

  proposal.source_evaluation_id =
      base.source_evaluation_id;

  proposal.source_evaluation_revision =
      base.source_evaluation_revision;

  proposal.system_hypothesis_id =
      base.system_hypothesis_id;

  proposal.hypothesis_target_key =
      base.hypothesis_target_key;

  proposal.context_key =
      base.context_key;

  proposal.data_scope_key =
      base.data_scope_key;

  proposal.diagnostic_code =
      base.diagnostic_code;

  proposal.hypothesis_evidence_revision =
      base.hypothesis_evidence_revision;

  proposal.proposer_ref =
      base.proposer_ref;

  proposal.remediation_key =
      base.remediation_key;

  proposal.rationale =
      base.rationale;

  proposal.data_remediation_proposal =
      typed;


  fixture.proposal =
      proposal;

  fixture.evaluation =
      evaluation;

  return fixture;
}

}  // namespace


int main() {
  using namespace routing::core::intelligence;

  const auto fixture =
      make_fixture();

  const std::vector<SystemHypothesisEvaluationRecord>
      history = {
          fixture.evaluation,
      };


  // -------------------------------------------------------------
  // APPROVAL
  // -------------------------------------------------------------

  RemediationApprovalWorkflow approval_workflow;

  RemediationApprovalRequest approval_request;

  approval_request.approval_id =
      "remediation-approval:data:1";

  approval_request.approver_ref =
      "approver:alpha";

  approval_request.decision =
      RemediationApprovalDecision::Approve;

  approval_request.rationale =
      "Approve isolated validation only.";


  const auto approval =
      approval_workflow.apply(
          fixture.proposal,
          history,
          approval_request);

  assert(
      approval.status ==
      RemediationApprovalApplyStatus::Created);

  assert(
      approval.record.
          shadow_validation_candidate.has_value());


  const RemediationShadowValidationCandidate& candidate =
      *approval.record.shadow_validation_candidate;

  assert(
      candidate.isolated_validation_only);

  assert(
      !candidate.production_traffic_allowed);

  assert(
      !candidate.real_user_impact_allowed);

  assert(
      !candidate.implementation_task_created);

  assert(
      !candidate.deployment_candidate_created);

  assert(
      !candidate.automatic_apply_allowed);

  assert(
      !candidate.data_write_allowed);

  assert(
      !candidate.routing_change_allowed);

  assert(
      !candidate.cost_engine_change_allowed);

  assert(
      !candidate.production_application_allowed);


  const auto duplicate_approval =
      approval_workflow.apply(
          fixture.proposal,
          history,
          approval_request);

  assert(
      duplicate_approval.status ==
      RemediationApprovalApplyStatus::
          DuplicateIgnored);


  auto approval_collision =
      approval_request;

  approval_collision.decision =
      RemediationApprovalDecision::Reject;

  bool approval_collision_rejected =
      false;

  try {
    (void)approval_workflow.apply(
        fixture.proposal,
        history,
        approval_collision);
  } catch (const std::invalid_argument&) {
    approval_collision_rejected =
        true;
  }

  assert(
      approval_collision_rejected);


  // -------------------------------------------------------------
  // SHADOW VALIDATION
  // -------------------------------------------------------------

  RemediationShadowValidationWorkflow validation_workflow;

  RemediationShadowValidationRequest validation_request;

  validation_request.validation_id =
      "shadow-validation:data:1";

  validation_request.validator_ref =
      "validator:alpha";

  validation_request.validation_revision =
      1;

  validation_request.environment =
      RemediationValidationEnvironment::
          RegressionFixture;

  validation_request.result =
      RemediationShadowValidationResult::Passed;


  RemediationShadowEvidenceReference validation_evidence;

  validation_evidence.evidence_id =
      "shadow-evidence:data:1";

  validation_evidence.source_ref =
      "regression:isolated-remediation-check";

  validation_evidence.data_scope_key =
      "local-only";

  validation_evidence.context_key =
      "context:data:1";

  validation_evidence.relation =
      RemediationShadowEvidenceRelation::Supports;

  validation_evidence.detail =
      "Isolated fixture validates the proposed direction.";

  validation_request.evidence = {
      validation_evidence,
  };

  validation_request.rationale =
      "Isolated regression validation passes.";


  const auto validation =
      validation_workflow.apply(
          approval.record,
          history,
          validation_request);

  assert(
      validation.status ==
      RemediationShadowValidationApplyStatus::Created);

  assert(
      validation.record.result ==
      RemediationShadowValidationResult::Passed);

  assert(
      validation.record.supporting_evidence_count == 1);

  assert(
      validation.record.regression_evidence_count == 0);

  assert(
      !validation.record.implementation_candidate_created);

  assert(
      !validation.record.implementation_task_created);

  assert(
      !validation.record.deployment_candidate_created);

  assert(
      !validation.record.automatic_apply_allowed);

  assert(
      !validation.record.data_write_allowed);

  assert(
      !validation.record.routing_change_allowed);

  assert(
      !validation.record.cost_engine_change_allowed);

  assert(
      !validation.record.production_application_allowed);


  const auto duplicate_validation =
      validation_workflow.apply(
          approval.record,
          history,
          validation_request);

  assert(
      duplicate_validation.status ==
      RemediationShadowValidationApplyStatus::
          DuplicateIgnored);


  auto skipped =
      validation_request;

  skipped.validation_id =
      "shadow-validation:data:3";

  skipped.validation_revision =
      3;

  skipped.result =
      RemediationShadowValidationResult::Inconclusive;

  skipped.rationale =
      "Revision skip must fail.";

  bool skipped_rejected =
      false;

  try {
    (void)validation_workflow.apply(
        approval.record,
        history,
        skipped);
  } catch (const std::logic_error&) {
    skipped_rejected =
        true;
  }

  assert(
      skipped_rejected);


  const std::string approval_report =
      format_remediation_approval_report(
          approval.record);

  assert(
      approval_report.find(
          "isolated validation only: yes") !=
      std::string::npos);

  assert(
      approval_report.find(
          "production traffic allowed: no") !=
      std::string::npos);


  const std::string validation_report =
      format_remediation_shadow_validation_report(
          validation.record);

  assert(
      validation_report.find(
          "result: passed") !=
      std::string::npos);

  assert(
      validation_report.find(
          "implementation candidate created: no") !=
      std::string::npos);

  assert(
      validation_report.find(
          "production application allowed: no") !=
      std::string::npos);


  std::cout
      << "Remediation approval/shadow validation tests passed\n";

  return 0;
}
