#include "routing/core/intelligence/remediation_approval_shadow_validation.hpp"

#include <stdexcept>
#include <string>
#include <unordered_set>
#include <utility>

namespace routing::core::intelligence {

namespace {

struct SelectedRemediationProposal {
  const RemediationProposalBase* base = nullptr;

  std::string target_key;
};


void validate_proposal_base(
    const RemediationProposalRecord& record,
    const RemediationProposalBase& base) {
  if (base.schema_version !=
      kRemediationProposalSchemaVersion) {
    throw std::invalid_argument(
        "Unsupported remediation proposal schema.");
  }

  if (base.id.empty() ||
      base.source_evaluation_id.empty() ||
      base.source_evaluation_revision == 0 ||
      base.system_hypothesis_id.empty() ||
      base.hypothesis_target_key.empty() ||
      base.source_mapping_id.empty() ||
      base.source_approval_id.empty() ||
      base.source_conversion_candidate_id.empty() ||
      base.source_outcome_id.empty() ||
      base.source_review_id.empty() ||
      base.source_analysis_id.empty() ||
      base.cluster_key.empty() ||
      base.context_key.empty() ||
      base.data_scope_key.empty() ||
      base.diagnostic_code.empty() ||
      base.hypothesis_evidence_revision == 0 ||
      base.source_hypothesis_key.empty() ||
      base.evaluator_ref.empty() ||
      base.evaluation_evidence.empty() ||
      base.proposer_ref.empty() ||
      base.remediation_key.empty() ||
      base.rationale.empty()) {
    throw std::invalid_argument(
        "Remediation approval requires complete proposal identity.");
  }

  if (record.proposal_id.empty() ||
      record.source_evaluation_id.empty() ||
      record.source_evaluation_revision == 0 ||
      record.system_hypothesis_id.empty() ||
      record.hypothesis_target_key.empty() ||
      record.context_key.empty() ||
      record.data_scope_key.empty() ||
      record.diagnostic_code.empty() ||
      record.hypothesis_evidence_revision == 0 ||
      record.proposer_ref.empty() ||
      record.remediation_key.empty() ||
      record.rationale.empty()) {
    throw std::invalid_argument(
        "Remediation proposal record identity is incomplete.");
  }

  if (base.source_evaluation_id !=
          record.source_evaluation_id ||
      base.source_evaluation_revision !=
          record.source_evaluation_revision ||
      base.system_hypothesis_id !=
          record.system_hypothesis_id ||
      base.hypothesis_target_key !=
          record.hypothesis_target_key ||
      base.context_key !=
          record.context_key ||
      base.data_scope_key !=
          record.data_scope_key ||
      base.diagnostic_code !=
          record.diagnostic_code ||
      base.hypothesis_evidence_revision !=
          record.hypothesis_evidence_revision ||
      base.proposer_ref !=
          record.proposer_ref ||
      base.remediation_key !=
          record.remediation_key ||
      base.rationale !=
          record.rationale) {
    throw std::invalid_argument(
        "Remediation proposal/base identity mismatch.");
  }

  if (!base.explicit_approval_required ||
      !base.shadow_validation_required) {
    throw std::logic_error(
        "Remediation proposal must require approval and shadow validation.");
  }

  if (base.approval_record_created ||
      base.shadow_validation_created ||
      base.implementation_task_created ||
      base.preference_interpretation_allowed ||
      base.preference_target_created ||
      base.preference_hypothesis_created ||
      base.learning_gate_invoked ||
      base.automatic_apply_allowed ||
      base.data_write_allowed ||
      base.backend_change_allowed ||
      base.candidate_pipeline_change_allowed ||
      base.map_change_allowed ||
      base.routing_change_allowed ||
      base.cost_engine_change_allowed ||
      base.production_application_allowed ||
      base.evidence_scope_promotion_allowed) {
    throw std::logic_error(
        "Unsafe remediation proposal cannot be approved.");
  }


  std::unordered_set<std::string>
      evidence_ids;

  std::uint32_t supporting =
      0;

  std::uint32_t refuting =
      0;

  std::uint32_t context =
      0;


  for (const auto& evidence :
       base.evaluation_evidence) {
    if (evidence.evidence_id.empty() ||
        evidence.source_ref.empty() ||
        evidence.data_scope_key.empty() ||
        evidence.context_key.empty() ||
        evidence.detail.empty()) {
      throw std::invalid_argument(
          "Proposal evaluation evidence is incomplete.");
    }

    if (!evidence_ids.insert(
            evidence.evidence_id).second) {
      throw std::invalid_argument(
          "Proposal evaluation evidence contains duplicate evidence_id.");
    }

    if (evidence.data_scope_key !=
        base.data_scope_key) {
      throw std::logic_error(
          "Proposal evaluation evidence scope mismatch.");
    }

    if (evidence.context_key !=
        base.context_key) {
      throw std::logic_error(
          "Proposal evaluation evidence context mismatch.");
    }

    switch (evidence.relation) {
      case SystemHypothesisEvidenceRelation::Supports:
        ++supporting;
        break;

      case SystemHypothesisEvidenceRelation::Refutes:
        ++refuting;
        break;

      case SystemHypothesisEvidenceRelation::Context:
        ++context;
        break;
    }
  }


  if (supporting !=
          base.supporting_evidence_count ||
      refuting !=
          base.refuting_evidence_count ||
      context !=
          base.context_evidence_count) {
    throw std::invalid_argument(
        "Proposal evaluation evidence counters do not match.");
  }

  if (supporting == 0) {
    throw std::logic_error(
        "Remediation proposal requires supporting evaluation evidence.");
  }
}


SelectedRemediationProposal
select_proposal(
    const RemediationProposalRecord& record) {
  if (record.schema_version !=
      kRemediationProposalSchemaVersion) {
    throw std::invalid_argument(
        "Unsupported remediation proposal record schema.");
  }


  const unsigned int populated =
      (record.data_remediation_proposal.has_value()
           ? 1U
           : 0U) +
      (record.backend_remediation_proposal.has_value()
           ? 1U
           : 0U) +
      (record.candidate_pipeline_remediation_proposal.has_value()
           ? 1U
           : 0U);

  if (populated != 1U) {
    throw std::invalid_argument(
        "Remediation proposal record must contain exactly one artifact.");
  }


  SelectedRemediationProposal selected;

  switch (record.kind) {
    case RemediationProposalKind::Data: {
      if (!record.data_remediation_proposal.has_value() ||
          record.backend_remediation_proposal.has_value() ||
          record.candidate_pipeline_remediation_proposal.has_value()) {
        throw std::invalid_argument(
            "Data remediation proposal kind/artifact mismatch.");
      }

      const auto& proposal =
          *record.data_remediation_proposal;

      validate_proposal_base(
          record,
          proposal.base);

      if (proposal.base.system_hypothesis_kind !=
              SystemHypothesisKind::DataSource ||
          proposal.data_source_key.empty() ||
          proposal.data_source_key !=
              record.hypothesis_target_key) {
        throw std::invalid_argument(
            "Data remediation proposal target mismatch.");
      }

      selected.base =
          &proposal.base;

      selected.target_key =
          proposal.data_source_key;

      break;
    }


    case RemediationProposalKind::Backend: {
      if (record.data_remediation_proposal.has_value() ||
          !record.backend_remediation_proposal.has_value() ||
          record.candidate_pipeline_remediation_proposal.has_value()) {
        throw std::invalid_argument(
            "Backend remediation proposal kind/artifact mismatch.");
      }

      const auto& proposal =
          *record.backend_remediation_proposal;

      validate_proposal_base(
          record,
          proposal.base);

      if (proposal.base.system_hypothesis_kind !=
              SystemHypothesisKind::Backend ||
          proposal.backend_component_key.empty() ||
          proposal.backend_component_key !=
              record.hypothesis_target_key) {
        throw std::invalid_argument(
            "Backend remediation proposal target mismatch.");
      }

      selected.base =
          &proposal.base;

      selected.target_key =
          proposal.backend_component_key;

      break;
    }


    case RemediationProposalKind::CandidatePipeline: {
      if (record.data_remediation_proposal.has_value() ||
          record.backend_remediation_proposal.has_value() ||
          !record.candidate_pipeline_remediation_proposal.has_value()) {
        throw std::invalid_argument(
            "Candidate pipeline remediation proposal kind/artifact mismatch.");
      }

      const auto& proposal =
          *record.candidate_pipeline_remediation_proposal;

      validate_proposal_base(
          record,
          proposal.base);

      if (proposal.base.system_hypothesis_kind !=
              SystemHypothesisKind::CandidatePipeline ||
          proposal.pipeline_stage_key.empty() ||
          proposal.pipeline_stage_key !=
              record.hypothesis_target_key) {
        throw std::invalid_argument(
            "Candidate pipeline remediation proposal target mismatch.");
      }

      selected.base =
          &proposal.base;

      selected.target_key =
          proposal.pipeline_stage_key;

      break;
    }
  }


  if (selected.base == nullptr) {
    throw std::logic_error(
        "Remediation proposal selection failed.");
  }

  return selected;
}


void validate_evaluation_record(
    const RemediationProposalBase& base,
    const SystemHypothesisEvaluationRecord& evaluation) {
  if (evaluation.schema_version !=
      kSystemHypothesisEvaluationSchemaVersion) {
    throw std::invalid_argument(
        "Unsupported evaluation schema for remediation approval.");
  }

  if (evaluation.evaluation_id.empty() ||
      evaluation.evaluation_revision == 0 ||
      evaluation.system_hypothesis_id.empty() ||
      evaluation.hypothesis_target_key.empty() ||
      evaluation.source_mapping_id.empty() ||
      evaluation.source_approval_id.empty() ||
      evaluation.source_conversion_candidate_id.empty() ||
      evaluation.source_outcome_id.empty() ||
      evaluation.source_review_id.empty() ||
      evaluation.source_analysis_id.empty() ||
      evaluation.cluster_key.empty() ||
      evaluation.context_key.empty() ||
      evaluation.data_scope_key.empty() ||
      evaluation.diagnostic_code.empty() ||
      evaluation.hypothesis_evidence_revision == 0 ||
      evaluation.source_hypothesis_key.empty() ||
      evaluation.evaluator_ref.empty() ||
      evaluation.evidence.empty() ||
      evaluation.rationale.empty()) {
    throw std::invalid_argument(
        "Evaluation history contains incomplete evaluation.");
  }

  if (evaluation.system_hypothesis_id !=
          base.system_hypothesis_id ||
      evaluation.hypothesis_kind !=
          base.system_hypothesis_kind ||
      evaluation.hypothesis_target_key !=
          base.hypothesis_target_key ||
      evaluation.source_mapping_id !=
          base.source_mapping_id ||
      evaluation.source_approval_id !=
          base.source_approval_id ||
      evaluation.source_conversion_candidate_id !=
          base.source_conversion_candidate_id ||
      evaluation.source_outcome_id !=
          base.source_outcome_id ||
      evaluation.source_review_id !=
          base.source_review_id ||
      evaluation.source_analysis_id !=
          base.source_analysis_id ||
      evaluation.cluster_key !=
          base.cluster_key ||
      evaluation.context_key !=
          base.context_key ||
      evaluation.data_scope_key !=
          base.data_scope_key ||
      evaluation.diagnostic_code !=
          base.diagnostic_code ||
      evaluation.hypothesis_evidence_revision !=
          base.hypothesis_evidence_revision ||
      evaluation.source_hypothesis_key !=
          base.source_hypothesis_key) {
    throw std::invalid_argument(
        "Evaluation history/system hypothesis identity mismatch.");
  }


  std::unordered_set<std::string>
      evidence_ids;

  std::uint32_t supporting =
      0;

  std::uint32_t refuting =
      0;

  std::uint32_t context =
      0;


  for (const auto& evidence :
       evaluation.evidence) {
    if (evidence.evidence_id.empty() ||
        evidence.source_ref.empty() ||
        evidence.data_scope_key.empty() ||
        evidence.context_key.empty() ||
        evidence.detail.empty()) {
      throw std::invalid_argument(
          "Evaluation history evidence is incomplete.");
    }

    if (!evidence_ids.insert(
            evidence.evidence_id).second) {
      throw std::invalid_argument(
          "Evaluation history contains duplicate evidence_id.");
    }

    if (evidence.data_scope_key !=
        base.data_scope_key) {
      throw std::logic_error(
          "Evaluation history must preserve evidence scope.");
    }

    if (evidence.context_key !=
        base.context_key) {
      throw std::logic_error(
          "Evaluation history must preserve context.");
    }

    switch (evidence.relation) {
      case SystemHypothesisEvidenceRelation::Supports:
        ++supporting;
        break;

      case SystemHypothesisEvidenceRelation::Refutes:
        ++refuting;
        break;

      case SystemHypothesisEvidenceRelation::Context:
        ++context;
        break;
    }
  }


  if (supporting !=
          evaluation.supporting_evidence_count ||
      refuting !=
          evaluation.refuting_evidence_count ||
      context !=
          evaluation.context_evidence_count) {
    throw std::invalid_argument(
        "Evaluation history counters do not match evidence.");
  }


  if (evaluation.result ==
          SystemHypothesisEvaluationResult::Supported &&
      supporting == 0) {
    throw std::logic_error(
        "Supported evaluation requires supporting evidence.");
  }

  if (evaluation.result ==
          SystemHypothesisEvaluationResult::Refuted &&
      refuting == 0) {
    throw std::logic_error(
        "Refuted evaluation requires refuting evidence.");
  }


  if (evaluation.remediation_proposal_created ||
      evaluation.preference_interpretation_allowed ||
      evaluation.preference_target_created ||
      evaluation.preference_hypothesis_created ||
      evaluation.learning_gate_invoked ||
      evaluation.shadow_evaluation_created ||
      evaluation.automatic_fix_allowed ||
      evaluation.map_change_allowed ||
      evaluation.routing_change_allowed ||
      evaluation.cost_engine_change_allowed ||
      evaluation.production_application_allowed ||
      evaluation.evidence_scope_promotion_allowed) {
    throw std::logic_error(
        "Unsafe evaluation history cannot cross remediation approval.");
  }
}


const SystemHypothesisEvaluationRecord*
select_latest_supported_evaluation(
    const RemediationProposalBase& base,
    const std::vector<SystemHypothesisEvaluationRecord>&
        history) {
  if (history.empty()) {
    throw std::logic_error(
        "Remediation approval requires evaluation history.");
  }


  std::unordered_set<std::uint64_t>
      revisions;

  std::unordered_set<std::string>
      evaluation_ids;

  const SystemHypothesisEvaluationRecord*
      latest = nullptr;

  std::uint64_t max_revision =
      0;


  for (const auto& evaluation :
       history) {
    if (evaluation.system_hypothesis_id !=
        base.system_hypothesis_id) {
      continue;
    }

    validate_evaluation_record(
        base,
        evaluation);


    if (!revisions.insert(
            evaluation.evaluation_revision).second) {
      throw std::invalid_argument(
          "Evaluation history contains duplicate revision.");
    }

    if (!evaluation_ids.insert(
            evaluation.evaluation_id).second) {
      throw std::invalid_argument(
          "Evaluation history contains duplicate evaluation_id.");
    }


    if (evaluation.evaluation_revision >
        max_revision) {
      max_revision =
          evaluation.evaluation_revision;

      latest =
          &evaluation;
    }
  }


  if (latest == nullptr) {
    throw std::logic_error(
        "No evaluation exists for remediation proposal hypothesis.");
  }


  if (revisions.size() !=
      max_revision) {
    throw std::logic_error(
        "Evaluation history revisions are not contiguous.");
  }


  if (latest->evaluation_id !=
          base.source_evaluation_id ||
      latest->evaluation_revision !=
          base.source_evaluation_revision) {
    throw std::logic_error(
        "Remediation proposal source evaluation is no longer latest.");
  }


  if (latest->result !=
      SystemHypothesisEvaluationResult::Supported) {
    throw std::logic_error(
        "Latest remediation proposal evaluation must still be Supported.");
  }


  // The proposal must retain the exact evidence snapshot of the
  // Supported evaluation it was created from.
  if (latest->evaluator_ref !=
          base.evaluator_ref ||
      latest->evidence !=
          base.evaluation_evidence ||
      latest->supporting_evidence_count !=
          base.supporting_evidence_count ||
      latest->refuting_evidence_count !=
          base.refuting_evidence_count ||
      latest->context_evidence_count !=
          base.context_evidence_count) {
    throw std::invalid_argument(
        "Remediation proposal evaluation snapshot mismatch.");
  }


  return latest;
}


void validate_approval_request(
    const RemediationApprovalRequest& request) {
  if (request.approval_id.empty()) {
    throw std::invalid_argument(
        "Remediation approval requires approval_id.");
  }

  if (request.approver_ref.empty()) {
    throw std::invalid_argument(
        "Remediation approval requires approver_ref.");
  }

  if (request.rationale.empty()) {
    throw std::invalid_argument(
        "Remediation approval requires rationale.");
  }
}


bool same_approval_request(
    const RemediationApprovalRecord& existing,
    const RemediationProposalRecord& proposal,
    const RemediationApprovalRequest& request) {
  return
      existing.source_proposal_id ==
          proposal.proposal_id &&
      existing.approver_ref ==
          request.approver_ref &&
      existing.decision ==
          request.decision &&
      existing.rationale ==
          request.rationale;
}


RemediationApprovalRecord
make_approval_record(
    const RemediationProposalRecord& proposal,
    const RemediationProposalBase& base,
    const RemediationApprovalRequest& request) {
  RemediationApprovalRecord record;

  record.approval_id =
      request.approval_id;

  record.source_proposal_id =
      proposal.proposal_id;

  record.kind =
      proposal.kind;

  record.source_evaluation_id =
      base.source_evaluation_id;

  record.source_evaluation_revision =
      base.source_evaluation_revision;

  record.system_hypothesis_id =
      base.system_hypothesis_id;

  record.hypothesis_target_key =
      base.hypothesis_target_key;

  record.context_key =
      base.context_key;

  record.data_scope_key =
      base.data_scope_key;

  record.diagnostic_code =
      base.diagnostic_code;

  record.hypothesis_evidence_revision =
      base.hypothesis_evidence_revision;

  record.remediation_key =
      base.remediation_key;

  record.proposer_ref =
      base.proposer_ref;

  record.approver_ref =
      request.approver_ref;

  record.decision =
      request.decision;

  record.rationale =
      request.rationale;

  return record;
}


RemediationShadowValidationCandidate
make_shadow_candidate(
    const RemediationApprovalRecord& approval) {
  RemediationShadowValidationCandidate candidate;

  candidate.id =
      std::string(
          "remediation-shadow-validation-v1|") +
      approval.source_proposal_id;

  candidate.source_approval_id =
      approval.approval_id;

  candidate.source_proposal_id =
      approval.source_proposal_id;

  candidate.kind =
      approval.kind;

  candidate.source_evaluation_id =
      approval.source_evaluation_id;

  candidate.source_evaluation_revision =
      approval.source_evaluation_revision;

  candidate.system_hypothesis_id =
      approval.system_hypothesis_id;

  candidate.hypothesis_target_key =
      approval.hypothesis_target_key;

  candidate.context_key =
      approval.context_key;

  candidate.data_scope_key =
      approval.data_scope_key;

  candidate.diagnostic_code =
      approval.diagnostic_code;

  candidate.hypothesis_evidence_revision =
      approval.hypothesis_evidence_revision;

  candidate.remediation_key =
      approval.remediation_key;

  candidate.proposer_ref =
      approval.proposer_ref;

  candidate.approver_ref =
      approval.approver_ref;

  candidate.rationale =
      approval.rationale;

  return candidate;
}


void validate_shadow_candidate(
    const RemediationApprovalRecord& approval,
    const RemediationShadowValidationCandidate& candidate) {
  if (candidate.schema_version !=
      kRemediationApprovalShadowValidationSchemaVersion) {
    throw std::invalid_argument(
        "Unsupported remediation shadow validation candidate schema.");
  }

  if (candidate.id.empty() ||
      candidate.source_approval_id.empty() ||
      candidate.source_proposal_id.empty() ||
      candidate.source_evaluation_id.empty() ||
      candidate.source_evaluation_revision == 0 ||
      candidate.system_hypothesis_id.empty() ||
      candidate.hypothesis_target_key.empty() ||
      candidate.context_key.empty() ||
      candidate.data_scope_key.empty() ||
      candidate.diagnostic_code.empty() ||
      candidate.hypothesis_evidence_revision == 0 ||
      candidate.remediation_key.empty() ||
      candidate.proposer_ref.empty() ||
      candidate.approver_ref.empty() ||
      candidate.rationale.empty()) {
    throw std::invalid_argument(
        "Remediation shadow validation candidate identity is incomplete.");
  }

  if (candidate.source_approval_id !=
          approval.approval_id ||
      candidate.source_proposal_id !=
          approval.source_proposal_id ||
      candidate.kind !=
          approval.kind ||
      candidate.source_evaluation_id !=
          approval.source_evaluation_id ||
      candidate.source_evaluation_revision !=
          approval.source_evaluation_revision ||
      candidate.system_hypothesis_id !=
          approval.system_hypothesis_id ||
      candidate.hypothesis_target_key !=
          approval.hypothesis_target_key ||
      candidate.context_key !=
          approval.context_key ||
      candidate.data_scope_key !=
          approval.data_scope_key ||
      candidate.diagnostic_code !=
          approval.diagnostic_code ||
      candidate.hypothesis_evidence_revision !=
          approval.hypothesis_evidence_revision ||
      candidate.remediation_key !=
          approval.remediation_key ||
      candidate.proposer_ref !=
          approval.proposer_ref ||
      candidate.approver_ref !=
          approval.approver_ref ||
      candidate.rationale !=
          approval.rationale) {
    throw std::invalid_argument(
        "Remediation shadow candidate/approval identity mismatch.");
  }

  if (!candidate.isolated_validation_only ||
      candidate.production_traffic_allowed ||
      candidate.real_user_impact_allowed ||
      candidate.implementation_task_created ||
      candidate.deployment_candidate_created ||
      candidate.automatic_apply_allowed ||
      candidate.data_write_allowed ||
      candidate.backend_change_allowed ||
      candidate.candidate_pipeline_change_allowed ||
      candidate.map_change_allowed ||
      candidate.routing_change_allowed ||
      candidate.cost_engine_change_allowed ||
      candidate.preference_interpretation_allowed ||
      candidate.preference_hypothesis_created ||
      candidate.learning_gate_invoked ||
      candidate.production_application_allowed ||
      candidate.evidence_scope_promotion_allowed) {
    throw std::logic_error(
        "Unsafe remediation shadow validation candidate.");
  }
}


void validate_fresh_candidate_evaluation(
    const RemediationShadowValidationCandidate& candidate,
    const std::vector<SystemHypothesisEvaluationRecord>&
        history) {
  if (history.empty()) {
    throw std::logic_error(
        "Remediation shadow validation requires evaluation history.");
  }


  std::unordered_set<std::uint64_t>
      revisions;

  std::unordered_set<std::string>
      ids;

  const SystemHypothesisEvaluationRecord*
      latest = nullptr;

  std::uint64_t max_revision =
      0;


  for (const auto& evaluation :
       history) {
    if (evaluation.system_hypothesis_id !=
        candidate.system_hypothesis_id) {
      continue;
    }

    if (evaluation.schema_version !=
        kSystemHypothesisEvaluationSchemaVersion) {
      throw std::invalid_argument(
          "Unsupported evaluation schema during shadow validation.");
    }

    if (evaluation.evaluation_id.empty() ||
        evaluation.evaluation_revision == 0 ||
        evaluation.hypothesis_target_key.empty() ||
        evaluation.context_key.empty() ||
        evaluation.data_scope_key.empty() ||
        evaluation.diagnostic_code.empty() ||
        evaluation.hypothesis_evidence_revision == 0 ||
        evaluation.evidence.empty()) {
      throw std::invalid_argument(
          "Shadow validation evaluation history is incomplete.");
    }

    if (evaluation.hypothesis_target_key !=
            candidate.hypothesis_target_key ||
        evaluation.context_key !=
            candidate.context_key ||
        evaluation.data_scope_key !=
            candidate.data_scope_key ||
        evaluation.diagnostic_code !=
            candidate.diagnostic_code ||
        evaluation.hypothesis_evidence_revision !=
            candidate.hypothesis_evidence_revision) {
      throw std::invalid_argument(
          "Shadow validation evaluation history identity mismatch.");
    }


    for (const auto& evidence :
         evaluation.evidence) {
      if (evidence.data_scope_key !=
          candidate.data_scope_key) {
        throw std::logic_error(
            "Shadow validation evaluation history scope mismatch.");
      }

      if (evidence.context_key !=
          candidate.context_key) {
        throw std::logic_error(
            "Shadow validation evaluation history context mismatch.");
      }
    }


    if (!revisions.insert(
            evaluation.evaluation_revision).second) {
      throw std::invalid_argument(
          "Shadow validation history contains duplicate revision.");
    }

    if (!ids.insert(
            evaluation.evaluation_id).second) {
      throw std::invalid_argument(
          "Shadow validation history contains duplicate evaluation_id.");
    }


    if (evaluation.evaluation_revision >
        max_revision) {
      max_revision =
          evaluation.evaluation_revision;

      latest =
          &evaluation;
    }
  }


  if (latest == nullptr) {
    throw std::logic_error(
        "No evaluation exists for approved remediation candidate.");
  }


  if (revisions.size() !=
      max_revision) {
    throw std::logic_error(
        "Shadow validation evaluation history is not contiguous.");
  }


  if (latest->evaluation_id !=
          candidate.source_evaluation_id ||
      latest->evaluation_revision !=
          candidate.source_evaluation_revision) {
    throw std::logic_error(
        "Approved remediation candidate became stale after newer evaluation.");
  }


  if (latest->result !=
      SystemHypothesisEvaluationResult::Supported) {
    throw std::logic_error(
        "Approved remediation candidate requires latest Supported evaluation.");
  }
}


void validate_shadow_request(
    const RemediationShadowValidationCandidate& candidate,
    const RemediationShadowValidationRequest& request) {
  if (request.validation_id.empty()) {
    throw std::invalid_argument(
        "Remediation shadow validation requires validation_id.");
  }

  if (request.validator_ref.empty()) {
    throw std::invalid_argument(
        "Remediation shadow validation requires validator_ref.");
  }

  if (request.validation_revision == 0) {
    throw std::invalid_argument(
        "Remediation shadow validation requires non-zero revision.");
  }

  if (request.evidence.empty()) {
    throw std::invalid_argument(
        "Remediation shadow validation requires explicit evidence.");
  }

  if (request.rationale.empty()) {
    throw std::invalid_argument(
        "Remediation shadow validation requires rationale.");
  }


  std::unordered_set<std::string>
      evidence_ids;

  std::uint32_t supporting =
      0;

  std::uint32_t regressing =
      0;


  for (const auto& evidence :
       request.evidence) {
    if (evidence.evidence_id.empty() ||
        evidence.source_ref.empty() ||
        evidence.data_scope_key.empty() ||
        evidence.context_key.empty() ||
        evidence.detail.empty()) {
      throw std::invalid_argument(
          "Remediation shadow evidence is incomplete.");
    }

    if (!evidence_ids.insert(
            evidence.evidence_id).second) {
      throw std::invalid_argument(
          "Remediation shadow validation contains duplicate evidence_id.");
    }

    if (evidence.data_scope_key !=
        candidate.data_scope_key) {
      throw std::logic_error(
          "Remediation shadow validation must preserve evidence scope.");
    }

    if (evidence.context_key !=
        candidate.context_key) {
      throw std::logic_error(
          "Remediation shadow validation must preserve context.");
    }


    if (evidence.relation ==
        RemediationShadowEvidenceRelation::Supports) {
      ++supporting;
    }

    if (evidence.relation ==
        RemediationShadowEvidenceRelation::Regresses) {
      ++regressing;
    }
  }


  // Consistency guards only. Result selection remains explicit.
  if (request.result ==
          RemediationShadowValidationResult::Passed &&
      supporting == 0) {
    throw std::logic_error(
        "Passed remediation shadow validation requires supporting evidence.");
  }

  if (request.result ==
          RemediationShadowValidationResult::Passed &&
      regressing != 0) {
    throw std::logic_error(
        "Passed remediation shadow validation cannot contain regression evidence.");
  }

  if (request.result ==
          RemediationShadowValidationResult::Failed &&
      regressing == 0) {
    throw std::logic_error(
        "Failed remediation shadow validation requires regression evidence.");
  }
}


bool same_shadow_request(
    const RemediationShadowValidationRecord& existing,
    const RemediationShadowValidationCandidate& candidate,
    const RemediationShadowValidationRequest& request) {
  return
      existing.source_validation_candidate_id ==
          candidate.id &&
      existing.validator_ref ==
          request.validator_ref &&
      existing.validation_revision ==
          request.validation_revision &&
      existing.environment ==
          request.environment &&
      existing.result ==
          request.result &&
      existing.evidence ==
          request.evidence &&
      existing.rationale ==
          request.rationale;
}


RemediationShadowValidationRecord
make_shadow_record(
    const RemediationApprovalRecord& approval,
    const RemediationShadowValidationCandidate& candidate,
    const RemediationShadowValidationRequest& request) {
  RemediationShadowValidationRecord record;

  record.validation_id =
      request.validation_id;

  record.validation_revision =
      request.validation_revision;

  record.source_approval_id =
      approval.approval_id;

  record.source_proposal_id =
      approval.source_proposal_id;

  record.source_validation_candidate_id =
      candidate.id;

  record.kind =
      candidate.kind;

  record.source_evaluation_id =
      candidate.source_evaluation_id;

  record.source_evaluation_revision =
      candidate.source_evaluation_revision;

  record.system_hypothesis_id =
      candidate.system_hypothesis_id;

  record.hypothesis_target_key =
      candidate.hypothesis_target_key;

  record.context_key =
      candidate.context_key;

  record.data_scope_key =
      candidate.data_scope_key;

  record.diagnostic_code =
      candidate.diagnostic_code;

  record.hypothesis_evidence_revision =
      candidate.hypothesis_evidence_revision;

  record.remediation_key =
      candidate.remediation_key;

  record.approver_ref =
      candidate.approver_ref;

  record.validator_ref =
      request.validator_ref;

  record.environment =
      request.environment;

  record.result =
      request.result;

  record.evidence =
      request.evidence;

  record.rationale =
      request.rationale;


  for (const auto& evidence :
       record.evidence) {
    switch (evidence.relation) {
      case RemediationShadowEvidenceRelation::Supports:
        ++record.supporting_evidence_count;
        break;

      case RemediationShadowEvidenceRelation::Regresses:
        ++record.regression_evidence_count;
        break;

      case RemediationShadowEvidenceRelation::Context:
        ++record.context_evidence_count;
        break;
    }
  }


  return record;
}


}  // namespace


const RemediationApprovalRecord*
RemediationApprovalWorkflow::find_by_approval_id(
    const std::string_view approval_id) const {
  for (const auto& record :
       records_) {
    if (record.approval_id ==
        approval_id) {
      return &record;
    }
  }

  return nullptr;
}


const RemediationApprovalRecord*
RemediationApprovalWorkflow::find_by_proposal_id(
    const std::string_view proposal_id) const {
  for (const auto& record :
       records_) {
    if (record.source_proposal_id ==
        proposal_id) {
      return &record;
    }
  }

  return nullptr;
}


const RemediationApprovalRecord*
RemediationApprovalWorkflow::find_approved_for_evaluation(
    const std::string_view system_hypothesis_id,
    const std::string_view evaluation_id,
    const std::uint64_t evaluation_revision) const {
  for (const auto& record :
       records_) {
    if (record.decision ==
            RemediationApprovalDecision::Approve &&
        record.system_hypothesis_id ==
            system_hypothesis_id &&
        record.source_evaluation_id ==
            evaluation_id &&
        record.source_evaluation_revision ==
            evaluation_revision) {
      return &record;
    }
  }

  return nullptr;
}


RemediationApprovalApplyResult
RemediationApprovalWorkflow::apply(
    const RemediationProposalRecord& proposal,
    const std::vector<SystemHypothesisEvaluationRecord>&
        evaluation_history,
    const RemediationApprovalRequest& request) {
  validate_approval_request(
      request);


  const auto selected =
      select_proposal(
          proposal);


  (void)select_latest_supported_evaluation(
      *selected.base,
      evaluation_history);


  if (const auto* existing =
          find_by_approval_id(
              request.approval_id);
      existing != nullptr) {
    if (!same_approval_request(
            *existing,
            proposal,
            request)) {
      throw std::invalid_argument(
          "Remediation approval id collision.");
    }

    return {
        RemediationApprovalApplyStatus::
            DuplicateIgnored,
        *existing,
    };
  }


  if (find_by_proposal_id(
          proposal.proposal_id) != nullptr) {
    throw std::logic_error(
        "Remediation proposal already has terminal approval decision.");
  }


  if (request.decision ==
          RemediationApprovalDecision::Approve &&
      find_approved_for_evaluation(
          selected.base->system_hypothesis_id,
          selected.base->source_evaluation_id,
          selected.base->source_evaluation_revision) != nullptr) {
    throw std::logic_error(
        "Another remediation proposal is already approved for this evaluation.");
  }


  auto record =
      make_approval_record(
          proposal,
          *selected.base,
          request);


  if (request.decision ==
      RemediationApprovalDecision::Approve) {
    record.shadow_validation_candidate =
        make_shadow_candidate(
            record);
  }


  records_.push_back(
      record);

  return {
      RemediationApprovalApplyStatus::Created,
      std::move(record),
  };
}


const RemediationShadowValidationRecord*
RemediationShadowValidationWorkflow::find_by_validation_id(
    const std::string_view validation_id) const {
  for (const auto& record :
       records_) {
    if (record.validation_id ==
        validation_id) {
      return &record;
    }
  }

  return nullptr;
}


const RemediationShadowValidationRecord*
RemediationShadowValidationWorkflow::latest_for_candidate(
    const std::string_view candidate_id) const {
  const RemediationShadowValidationRecord*
      latest = nullptr;

  for (const auto& record :
       records_) {
    if (record.source_validation_candidate_id !=
        candidate_id) {
      continue;
    }

    if (latest == nullptr ||
        record.validation_revision >
            latest->validation_revision) {
      latest =
          &record;
    }
  }

  return latest;
}


RemediationShadowValidationApplyResult
RemediationShadowValidationWorkflow::apply(
    const RemediationApprovalRecord& approval,
    const std::vector<SystemHypothesisEvaluationRecord>&
        evaluation_history,
    const RemediationShadowValidationRequest& request) {
  if (approval.schema_version !=
      kRemediationApprovalShadowValidationSchemaVersion) {
    throw std::invalid_argument(
        "Unsupported remediation approval schema.");
  }

  if (approval.decision !=
      RemediationApprovalDecision::Approve) {
    throw std::logic_error(
        "Rejected remediation proposal cannot enter shadow validation.");
  }

  if (!approval.shadow_validation_candidate.has_value()) {
    throw std::invalid_argument(
        "Approved remediation is missing shadow validation candidate.");
  }


  const auto& candidate =
      *approval.shadow_validation_candidate;

  validate_shadow_candidate(
      approval,
      candidate);

  validate_fresh_candidate_evaluation(
      candidate,
      evaluation_history);

  validate_shadow_request(
      candidate,
      request);


  if (const auto* existing =
          find_by_validation_id(
              request.validation_id);
      existing != nullptr) {
    if (!same_shadow_request(
            *existing,
            candidate,
            request)) {
      throw std::invalid_argument(
          "Shadow validation id collision.");
    }

    return {
        RemediationShadowValidationApplyStatus::
            DuplicateIgnored,
        *existing,
    };
  }


  const auto* latest =
      latest_for_candidate(
          candidate.id);

  const std::uint64_t expected_revision =
      latest == nullptr
          ? 1
          : latest->validation_revision + 1;


  if (request.validation_revision !=
      expected_revision) {
    throw std::logic_error(
        "Shadow validation revision must advance exactly by one.");
  }


  auto record =
      make_shadow_record(
          approval,
          candidate,
          request);

  records_.push_back(
      record);

  return {
      RemediationShadowValidationApplyStatus::Created,
      std::move(record),
  };
}

}  // namespace routing::core::intelligence
