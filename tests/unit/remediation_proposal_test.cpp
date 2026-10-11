#include <cassert>
#include <iostream>
#include <stdexcept>
#include <string>
#include <vector>

#include "routing/core/intelligence/remediation_proposal.hpp"
#include "routing/core/intelligence/remediation_proposal_report.hpp"

namespace {

routing::core::intelligence::SemanticHypothesisMappingRecord
make_mapping() {
  using namespace routing::core::intelligence;

  SemanticHypothesisMappingRecord mapping;

  mapping.mapping_id =
      "mapping:data:1";

  mapping.source_approval_id =
      "approval:data:1";

  mapping.source_conversion_candidate_id =
      "conversion:data:1";

  mapping.cluster_key =
      "cluster:data:1";

  mapping.context_key =
      "context:data:1";

  mapping.data_scope_key =
      "local-only";

  mapping.diagnostic_code =
      "DATA_COVERAGE_URBAN_LOW";

  mapping.evidence_revision =
      2;

  mapping.source_hypothesis_key =
      "hypothesis.data.urban_source_gap";

  mapping.mapper_ref =
      "mapper:alpha";

  mapping.decision =
      SemanticHypothesisMappingDecision::Map;

  mapping.kind =
      SystemHypothesisKind::DataSource;

  mapping.target_key =
      "source:urban-classification";

  mapping.rationale =
      "Explicit source-data system mapping.";


  DataSourceHypothesis hypothesis;

  hypothesis.base.id =
      "system-hypothesis-v1|mapping:data:1";

  hypothesis.base.source_mapping_id =
      mapping.mapping_id;

  hypothesis.base.source_approval_id =
      mapping.source_approval_id;

  hypothesis.base.source_conversion_candidate_id =
      mapping.source_conversion_candidate_id;

  hypothesis.base.source_outcome_id =
      "outcome:data:1";

  hypothesis.base.source_review_id =
      "review:data:1";

  hypothesis.base.source_analysis_id =
      "analysis:data:1";

  hypothesis.base.cluster_key =
      mapping.cluster_key;

  hypothesis.base.context_key =
      mapping.context_key;

  hypothesis.base.data_scope_key =
      mapping.data_scope_key;

  hypothesis.base.diagnostic_code =
      mapping.diagnostic_code;

  hypothesis.base.evidence_revision =
      mapping.evidence_revision;

  hypothesis.base.source_hypothesis_key =
      mapping.source_hypothesis_key;

  hypothesis.base.mapper_ref =
      mapping.mapper_ref;

  hypothesis.base.rationale =
      mapping.rationale;

  hypothesis.data_source_key =
      mapping.target_key;

  mapping.data_source_hypothesis =
      hypothesis;

  return mapping;
}


routing::core::intelligence::SystemHypothesisEvaluationRecord
make_supported_evaluation(
    const routing::core::intelligence::
        SemanticHypothesisMappingRecord& mapping) {
  using namespace routing::core::intelligence;

  const auto& hypothesis =
      *mapping.data_source_hypothesis;

  SystemHypothesisEvaluationRecord evaluation;

  evaluation.evaluation_id =
      "evaluation:data:1";

  evaluation.evaluation_revision =
      1;

  evaluation.system_hypothesis_id =
      hypothesis.base.id;

  evaluation.hypothesis_kind =
      mapping.kind;

  evaluation.hypothesis_target_key =
      mapping.target_key;

  evaluation.source_mapping_id =
      hypothesis.base.source_mapping_id;

  evaluation.source_approval_id =
      hypothesis.base.source_approval_id;

  evaluation.source_conversion_candidate_id =
      hypothesis.base.source_conversion_candidate_id;

  evaluation.source_outcome_id =
      hypothesis.base.source_outcome_id;

  evaluation.source_review_id =
      hypothesis.base.source_review_id;

  evaluation.source_analysis_id =
      hypothesis.base.source_analysis_id;

  evaluation.cluster_key =
      hypothesis.base.cluster_key;

  evaluation.context_key =
      hypothesis.base.context_key;

  evaluation.data_scope_key =
      hypothesis.base.data_scope_key;

  evaluation.diagnostic_code =
      hypothesis.base.diagnostic_code;

  evaluation.hypothesis_evidence_revision =
      hypothesis.base.evidence_revision;

  evaluation.source_hypothesis_key =
      hypothesis.base.source_hypothesis_key;

  evaluation.evaluator_ref =
      "evaluator:alpha";

  evaluation.result =
      SystemHypothesisEvaluationResult::Supported;


  SystemHypothesisEvidenceReference evidence;

  evidence.evidence_id =
      "evidence:data:1";

  evidence.source_ref =
      "review:source-data";

  evidence.data_scope_key =
      hypothesis.base.data_scope_key;

  evidence.context_key =
      hypothesis.base.context_key;

  evidence.relation =
      SystemHypothesisEvidenceRelation::Supports;

  evidence.detail =
      "Independent source review reproduces the coverage problem.";

  evaluation.evidence = {
      evidence,
  };

  evaluation.supporting_evidence_count =
      1;

  evaluation.rationale =
      "Reviewed evidence supports the data-source hypothesis.";

  return evaluation;
}

}  // namespace


int main() {
  using namespace routing::core::intelligence;

  const auto mapping =
      make_mapping();

  const auto evaluation =
      make_supported_evaluation(
          mapping);

  const std::vector<SystemHypothesisEvaluationRecord>
      history = {
          evaluation,
      };


  RemediationProposalWorkflow workflow;

  RemediationProposalRequest request;

  request.proposal_id =
      "proposal:data:1";

  request.proposer_ref =
      "proposer:alpha";

  request.source_evaluation_id =
      evaluation.evaluation_id;

  request.source_evaluation_revision =
      evaluation.evaluation_revision;

  request.remediation_key =
      "remediation.data.review-urban-source-import";

  request.rationale =
      "Propose a reviewed source-data remediation for later approval.";


  const auto created =
      workflow.apply(
          mapping,
          history,
          request);

  assert(
      created.status ==
      RemediationProposalApplyStatus::Created);

  assert(
      workflow.records().size() == 1);

  assert(
      created.record.data_remediation_proposal.has_value());

  assert(
      !created.record.backend_remediation_proposal.has_value());

  assert(
      !created.record.
          candidate_pipeline_remediation_proposal.has_value());


  const DataRemediationProposal& proposal =
      *created.record.data_remediation_proposal;

  assert(
      proposal.data_source_key ==
      "source:urban-classification");

  assert(
      proposal.base.source_evaluation_revision == 1);

  assert(
      proposal.base.supporting_evidence_count == 1);

  assert(
      proposal.base.data_scope_key ==
      "local-only");

  assert(
      proposal.base.explicit_approval_required);

  assert(
      proposal.base.shadow_validation_required);

  assert(
      !proposal.base.approval_record_created);

  assert(
      !proposal.base.shadow_validation_created);

  assert(
      !proposal.base.implementation_task_created);

  assert(
      !proposal.base.automatic_apply_allowed);

  assert(
      !proposal.base.data_write_allowed);

  assert(
      !proposal.base.backend_change_allowed);

  assert(
      !proposal.base.candidate_pipeline_change_allowed);

  assert(
      !proposal.base.map_change_allowed);

  assert(
      !proposal.base.routing_change_allowed);

  assert(
      !proposal.base.cost_engine_change_allowed);

  assert(
      !proposal.base.production_application_allowed);

  assert(
      !proposal.base.evidence_scope_promotion_allowed);


  // Exact repeated request is idempotent.
  const auto duplicate =
      workflow.apply(
          mapping,
          history,
          request);

  assert(
      duplicate.status ==
      RemediationProposalApplyStatus::
          DuplicateIgnored);

  assert(
      workflow.records().size() == 1);


  // Same id cannot acquire a different remediation concept.
  auto collision =
      request;

  collision.remediation_key =
      "remediation.data.different";

  bool collision_rejected =
      false;

  try {
    (void)workflow.apply(
        mapping,
        history,
        collision);
  } catch (const std::invalid_argument&) {
    collision_rejected =
        true;
  }

  assert(
      collision_rejected);


  // Same remediation concept cannot hide behind another id.
  auto semantic_duplicate =
      request;

  semantic_duplicate.proposal_id =
      "proposal:data:duplicate";

  bool semantic_duplicate_rejected =
      false;

  try {
    (void)workflow.apply(
        mapping,
        history,
        semantic_duplicate);
  } catch (const std::logic_error&) {
    semantic_duplicate_rejected =
        true;
  }

  assert(
      semantic_duplicate_rejected);


  // A genuinely different alternative is allowed.
  auto alternative =
      request;

  alternative.proposal_id =
      "proposal:data:alternative";

  alternative.remediation_key =
      "remediation.data.compare-upstream-source";

  alternative.rationale =
      "Propose a distinct alternative for later approval comparison.";


  const auto alternative_result =
      workflow.apply(
          mapping,
          history,
          alternative);

  assert(
      alternative_result.status ==
      RemediationProposalApplyStatus::Created);

  assert(
      workflow.records().size() == 2);


  const std::string report =
      format_remediation_proposal_report(
          created.record);

  assert(
      report.find(
          "REMEDIATION PROPOSAL") !=
      std::string::npos);

  assert(
      report.find(
          "DATA REMEDIATION PROPOSAL") !=
      std::string::npos);

  assert(
      report.find(
          "explicit approval required: yes") !=
      std::string::npos);

  assert(
      report.find(
          "data write allowed: no") !=
      std::string::npos);

  assert(
      report.find(
          "production application allowed: no") !=
      std::string::npos);


  std::cout
      << "Remediation proposal tests passed\n";

  return 0;
}
