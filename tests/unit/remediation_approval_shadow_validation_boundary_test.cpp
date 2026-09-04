#include <cassert>
#include <iostream>
#include <stdexcept>
#include <string>
#include <vector>

#include "routing/core/intelligence/remediation_approval_shadow_validation.hpp"

namespace {

struct Fixture {
  routing::core::intelligence::RemediationProposalRecord proposal;
  routing::core::intelligence::SystemHypothesisEvaluationRecord evaluation;
};


Fixture make_fixture(
    const std::string& suffix,
    const routing::core::intelligence::RemediationProposalKind kind) {
  using namespace routing::core::intelligence;

  Fixture fixture;


  const SystemHypothesisKind hypothesis_kind =
      kind == RemediationProposalKind::Data
          ? SystemHypothesisKind::DataSource
          : kind == RemediationProposalKind::Backend
              ? SystemHypothesisKind::Backend
              : SystemHypothesisKind::CandidatePipeline;


  const std::string target =
      kind == RemediationProposalKind::Data
          ? "source:" + suffix
          : kind == RemediationProposalKind::Backend
              ? "backend:" + suffix
              : "candidate-pipeline:" + suffix;


  SystemHypothesisEvaluationRecord evaluation;

  evaluation.evaluation_id =
      "evaluation:" + suffix + ":1";

  evaluation.evaluation_revision =
      1;

  evaluation.system_hypothesis_id =
      "system-hypothesis:" + suffix;

  evaluation.hypothesis_kind =
      hypothesis_kind;

  evaluation.hypothesis_target_key =
      target;

  evaluation.source_mapping_id =
      "mapping:" + suffix;

  evaluation.source_approval_id =
      "upstream-approval:" + suffix;

  evaluation.source_conversion_candidate_id =
      "conversion:" + suffix;

  evaluation.source_outcome_id =
      "outcome:" + suffix;

  evaluation.source_review_id =
      "review:" + suffix;

  evaluation.source_analysis_id =
      "analysis:" + suffix;

  evaluation.cluster_key =
      "cluster:" + suffix;

  evaluation.context_key =
      "context:" + suffix;

  evaluation.data_scope_key =
      "personal";

  evaluation.diagnostic_code =
      "DIAGNOSTIC_" + suffix;

  evaluation.hypothesis_evidence_revision =
      4;

  evaluation.source_hypothesis_key =
      "hypothesis:" + suffix;

  evaluation.evaluator_ref =
      "evaluator:" + suffix;

  evaluation.result =
      SystemHypothesisEvaluationResult::Supported;


  SystemHypothesisEvidenceReference evidence;

  evidence.evidence_id =
      "evaluation-evidence:" + suffix;

  evidence.source_ref =
      "regression:" + suffix;

  evidence.data_scope_key =
      evaluation.data_scope_key;

  evidence.context_key =
      evaluation.context_key;

  evidence.relation =
      SystemHypothesisEvidenceRelation::Supports;

  evidence.detail =
      "Explicit supporting evaluation evidence.";

  evaluation.evidence = {
      evidence,
  };

  evaluation.supporting_evidence_count =
      1;

  evaluation.rationale =
      "Supported evaluation.";


  RemediationProposalBase base;

  base.id =
      "remediation-proposal-v1|proposal:" + suffix;

  base.source_evaluation_id =
      evaluation.evaluation_id;

  base.source_evaluation_revision =
      evaluation.evaluation_revision;

  base.system_hypothesis_id =
      evaluation.system_hypothesis_id;

  base.system_hypothesis_kind =
      evaluation.hypothesis_kind;

  base.hypothesis_target_key =
      target;

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
      1;

  base.proposer_ref =
      "proposer:" + suffix;

  base.remediation_key =
      "remediation:" + suffix;

  base.rationale =
      "Proposal for explicit approval.";


  RemediationProposalRecord proposal;

  proposal.proposal_id =
      "proposal:" + suffix;

  proposal.kind =
      kind;

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


  if (kind ==
      RemediationProposalKind::Data) {
    DataRemediationProposal typed;

    typed.base =
        base;

    typed.data_source_key =
        target;

    proposal.data_remediation_proposal =
        typed;
  } else if (kind ==
             RemediationProposalKind::Backend) {
    BackendRemediationProposal typed;

    typed.base =
        base;

    typed.backend_component_key =
        target;

    proposal.backend_remediation_proposal =
        typed;
  } else {
    CandidatePipelineRemediationProposal typed;

    typed.base =
        base;

    typed.pipeline_stage_key =
        target;

    proposal.candidate_pipeline_remediation_proposal =
        typed;
  }


  fixture.proposal =
      proposal;

  fixture.evaluation =
      evaluation;

  return fixture;
}


routing::core::intelligence::RemediationApprovalRequest
approve_request(
    const std::string& suffix) {
  using namespace routing::core::intelligence;

  RemediationApprovalRequest request;

  request.approval_id =
      "approval:" + suffix;

  request.approver_ref =
      "approver:" + suffix;

  request.decision =
      RemediationApprovalDecision::Approve;

  request.rationale =
      "Approve isolated shadow validation only.";

  return request;
}


routing::core::intelligence::RemediationShadowValidationRequest
validation_request(
    const std::string& suffix,
    const std::string& scope,
    const std::string& context) {
  using namespace routing::core::intelligence;

  RemediationShadowValidationRequest request;

  request.validation_id =
      "validation:" + suffix;

  request.validator_ref =
      "validator:" + suffix;

  request.validation_revision =
      1;

  request.environment =
      RemediationValidationEnvironment::ShadowSandbox;

  request.result =
      RemediationShadowValidationResult::Passed;


  RemediationShadowEvidenceReference evidence;

  evidence.evidence_id =
      "shadow-evidence:" + suffix;

  evidence.source_ref =
      "shadow-sandbox:" + suffix;

  evidence.data_scope_key =
      scope;

  evidence.context_key =
      context;

  evidence.relation =
      RemediationShadowEvidenceRelation::Supports;

  evidence.detail =
      "Isolated shadow evidence.";

  request.evidence = {
      evidence,
  };

  request.rationale =
      "Explicit isolated shadow validation.";

  return request;
}

}  // namespace


int main() {
  using namespace routing::core::intelligence;


  // -------------------------------------------------------------
  // ONLY ONE ALTERNATIVE MAY BE APPROVED PER EVALUATION
  // -------------------------------------------------------------

  auto first =
      make_fixture(
          "alternative-a",
          RemediationProposalKind::Backend);

  auto second =
      first;

  second.proposal.proposal_id =
      "proposal:alternative-b";

  second.proposal.remediation_key =
      "remediation:alternative-b";

  second.proposal.rationale =
      "Second alternative.";

  second.proposal.backend_remediation_proposal->base.id =
      "remediation-proposal-v1|proposal:alternative-b";

  second.proposal.backend_remediation_proposal->base.remediation_key =
      second.proposal.remediation_key;

  second.proposal.backend_remediation_proposal->base.rationale =
      second.proposal.rationale;


  const std::vector<SystemHypothesisEvaluationRecord>
      alternative_history = {
          first.evaluation,
      };


  RemediationApprovalWorkflow approval_workflow;

  const auto first_approval =
      approval_workflow.apply(
          first.proposal,
          alternative_history,
          approve_request(
              "alternative-a"));

  assert(
      first_approval.record.
          shadow_validation_candidate.has_value());


  bool second_approval_rejected =
      false;

  try {
    (void)approval_workflow.apply(
        second.proposal,
        alternative_history,
        approve_request(
            "alternative-b"));
  } catch (const std::logic_error&) {
    second_approval_rejected =
        true;
  }

  assert(
      second_approval_rejected);


  // -------------------------------------------------------------
  // A REJECTED PROPOSAL DOES NOT CREATE VALIDATION CANDIDATE
  // -------------------------------------------------------------

  const auto rejected_fixture =
      make_fixture(
          "rejected",
          RemediationProposalKind::Data);

  const std::vector<SystemHypothesisEvaluationRecord>
      rejected_history = {
          rejected_fixture.evaluation,
      };


  auto reject_request =
      approve_request(
          "rejected");

  reject_request.decision =
      RemediationApprovalDecision::Reject;

  reject_request.rationale =
      "Reject this remediation alternative.";


  const auto rejected =
      approval_workflow.apply(
          rejected_fixture.proposal,
          rejected_history,
          reject_request);

  assert(
      !rejected.record.
          shadow_validation_candidate.has_value());


  RemediationShadowValidationWorkflow validation_workflow;

  bool rejected_validation_rejected =
      false;

  try {
    (void)validation_workflow.apply(
        rejected.record,
        rejected_history,
        validation_request(
            "rejected",
            "personal",
            "context:rejected"));
  } catch (const std::logic_error&) {
    rejected_validation_rejected =
        true;
  }

  assert(
      rejected_validation_rejected);


  // -------------------------------------------------------------
  // OLD SUPPORTED EVALUATION CANNOT BE APPROVED AFTER NEWER REFUTATION
  // -------------------------------------------------------------

  const auto stale =
      make_fixture(
          "stale",
          RemediationProposalKind::Data);

  auto newer_refuted =
      stale.evaluation;

  newer_refuted.evaluation_id =
      "evaluation:stale:2";

  newer_refuted.evaluation_revision =
      2;

  newer_refuted.evaluator_ref =
      "evaluator:stale:2";

  newer_refuted.result =
      SystemHypothesisEvaluationResult::Refuted;

  newer_refuted.evidence[0].evidence_id =
      "evaluation-evidence:stale:2";

  newer_refuted.evidence[0].relation =
      SystemHypothesisEvidenceRelation::Refutes;

  newer_refuted.evidence[0].detail =
      "Later evidence refutes the system hypothesis.";

  newer_refuted.supporting_evidence_count =
      0;

  newer_refuted.refuting_evidence_count =
      1;

  newer_refuted.rationale =
      "Later reviewed evidence refutes the hypothesis.";


  const std::vector<SystemHypothesisEvaluationRecord>
      stale_history = {
          stale.evaluation,
          newer_refuted,
      };


  bool stale_approval_rejected =
      false;

  try {
    RemediationApprovalWorkflow stale_workflow;

    (void)stale_workflow.apply(
        stale.proposal,
        stale_history,
        approve_request(
            "stale"));
  } catch (const std::logic_error&) {
    stale_approval_rejected =
        true;
  }

  assert(
      stale_approval_rejected);


  // -------------------------------------------------------------
  // NEWER EVALUATION AFTER APPROVAL INVALIDATES SHADOW VALIDATION
  // -------------------------------------------------------------

  const auto freshness =
      make_fixture(
          "freshness",
          RemediationProposalKind::Backend);

  const std::vector<SystemHypothesisEvaluationRecord>
      freshness_history = {
          freshness.evaluation,
      };


  RemediationApprovalWorkflow freshness_approval_workflow;

  const auto freshness_approval =
      freshness_approval_workflow.apply(
          freshness.proposal,
          freshness_history,
          approve_request(
              "freshness"));


  auto later =
      freshness.evaluation;

  later.evaluation_id =
      "evaluation:freshness:2";

  later.evaluation_revision =
      2;

  later.evaluator_ref =
      "evaluator:freshness:2";

  later.result =
      SystemHypothesisEvaluationResult::Inconclusive;

  later.evidence[0].evidence_id =
      "evaluation-evidence:freshness:2";

  later.evidence[0].relation =
      SystemHypothesisEvidenceRelation::Context;

  later.evidence[0].detail =
      "New evidence makes the hypothesis inconclusive.";

  later.supporting_evidence_count =
      0;

  later.context_evidence_count =
      1;

  later.rationale =
      "Newer evidence is inconclusive.";


  const std::vector<SystemHypothesisEvaluationRecord>
      post_approval_history = {
          freshness.evaluation,
          later,
      };


  bool stale_validation_rejected =
      false;

  try {
    (void)validation_workflow.apply(
        freshness_approval.record,
        post_approval_history,
        validation_request(
            "freshness",
            "personal",
            "context:freshness"));
  } catch (const std::logic_error&) {
    stale_validation_rejected =
        true;
  }

  assert(
      stale_validation_rejected);


  // -------------------------------------------------------------
  // CROSS-SCOPE VALIDATION IS FORBIDDEN
  // -------------------------------------------------------------

  const auto scope =
      make_fixture(
          "scope",
          RemediationProposalKind::CandidatePipeline);

  const std::vector<SystemHypothesisEvaluationRecord>
      scope_history = {
          scope.evaluation,
      };


  RemediationApprovalWorkflow scope_approval_workflow;

  const auto scope_approval =
      scope_approval_workflow.apply(
          scope.proposal,
          scope_history,
          approve_request(
              "scope"));


  auto wrong_scope =
      validation_request(
          "scope",
          "global-reference",
          "context:scope");


  bool scope_rejected =
      false;

  try {
    (void)validation_workflow.apply(
        scope_approval.record,
        scope_history,
        wrong_scope);
  } catch (const std::logic_error&) {
    scope_rejected =
        true;
  }

  assert(
      scope_rejected);


  // -------------------------------------------------------------
  // PASSED MAY NOT CONTAIN REGRESSION EVIDENCE
  // -------------------------------------------------------------

  auto conflicting =
      validation_request(
          "conflicting",
          "personal",
          "context:scope");

  RemediationShadowEvidenceReference regression;

  regression.evidence_id =
      "shadow-evidence:conflicting:regression";

  regression.source_ref =
      "shadow-sandbox:regression";

  regression.data_scope_key =
      "personal";

  regression.context_key =
      "context:scope";

  regression.relation =
      RemediationShadowEvidenceRelation::Regresses;

  regression.detail =
      "A regression was observed.";

  conflicting.evidence.push_back(
      regression);


  bool conflicting_pass_rejected =
      false;

  try {
    (void)validation_workflow.apply(
        scope_approval.record,
        scope_history,
        conflicting);
  } catch (const std::logic_error&) {
    conflicting_pass_rejected =
        true;
  }

  assert(
      conflicting_pass_rejected);


  // -------------------------------------------------------------
  // UNSAFE PROPOSAL CANNOT BE APPROVED
  // -------------------------------------------------------------

  auto unsafe =
      make_fixture(
          "unsafe",
          RemediationProposalKind::Backend);

  unsafe.proposal.backend_remediation_proposal->
      base.backend_change_allowed =
          true;

  const std::vector<SystemHypothesisEvaluationRecord>
      unsafe_history = {
          unsafe.evaluation,
      };


  bool unsafe_rejected =
      false;

  try {
    RemediationApprovalWorkflow unsafe_workflow;

    (void)unsafe_workflow.apply(
        unsafe.proposal,
        unsafe_history,
        approve_request(
            "unsafe"));
  } catch (const std::logic_error&) {
    unsafe_rejected =
        true;
  }

  assert(
      unsafe_rejected);


  std::cout
      << "Remediation approval/shadow validation boundary tests passed\n";

  return 0;
}
