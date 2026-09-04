#include <cassert>
#include <iostream>
#include <stdexcept>
#include <string>

#include "routing/core/intelligence/semantic_hypothesis_mapping.hpp"
#include "routing/core/intelligence/semantic_hypothesis_mapping_report.hpp"

namespace {

routing::core::intelligence::ProposalApprovalRecord
make_approval() {
  using namespace routing::core::intelligence;

  ProposalApprovalRecord approval;

  approval.approval_id =
      "approval:hypothesis:1";

  approval.source_outcome_id =
      "outcome:hypothesis:1";

  approval.source_review_id =
      "review:hypothesis:1";

  approval.source_analysis_id =
      "analysis:hypothesis:1";

  approval.outcome_kind =
      ReviewedAnalysisOutcomeKind::
          HypothesisProposal;

  approval.outcome_reviewer_ref =
      "reviewer:alpha";

  approval.approver_ref =
      "approver:alpha";

  approval.cluster_key =
      "cluster:data-source:1";

  approval.context_key =
      "context:data-source:1";

  approval.data_scope_key =
      "local-only";

  approval.diagnostic_code =
      "DATA_COVERAGE_URBAN_LOW";

  approval.evidence_revision =
      2;

  approval.decision =
      ProposalApprovalDecision::Approve;

  approval.rationale =
      "Approve explicit semantic mapping review.";


  HypothesisConversionCandidate candidate;

  candidate.id =
      "hypothesis-conversion-v1|outcome:hypothesis:1";

  candidate.source_outcome_id =
      approval.source_outcome_id;

  candidate.source_review_id =
      approval.source_review_id;

  candidate.source_analysis_id =
      approval.source_analysis_id;

  candidate.cluster_key =
      approval.cluster_key;

  candidate.context_key =
      approval.context_key;

  candidate.data_scope_key =
      approval.data_scope_key;

  candidate.diagnostic_code =
      approval.diagnostic_code;

  candidate.evidence_revision =
      approval.evidence_revision;

  candidate.hypothesis_key =
      "hypothesis.data.urban_signal_source_gap";

  candidate.approver_ref =
      approval.approver_ref;

  candidate.rationale =
      approval.rationale;

  approval.hypothesis_conversion_candidate =
      candidate;

  return approval;
}

}  // namespace


int main() {
  using namespace routing::core::intelligence;

  const auto approval =
      make_approval();

  SemanticHypothesisMappingWorkflow workflow;

  SemanticHypothesisMappingRequest request;

  request.mapping_id =
      "mapping:data-source:1";

  request.mapper_ref =
      "mapper:alpha";

  request.decision =
      SemanticHypothesisMappingDecision::Map;

  request.kind =
      SystemHypothesisKind::DataSource;

  request.target_key =
      "source:urban-classification";

  request.rationale =
      "Map the approved proposition to an explicit data-source hypothesis.";


  const auto mapped =
      workflow.apply(
          approval,
          request);

  assert(
      mapped.status ==
      SemanticHypothesisMappingApplyStatus::Created);

  assert(
      workflow.records().size() == 1);

  assert(
      mapped.record.data_source_hypothesis.has_value());

  assert(
      !mapped.record.backend_hypothesis.has_value());

  assert(
      !mapped.record.candidate_pipeline_hypothesis.has_value());


  const DataSourceHypothesis& hypothesis =
      *mapped.record.data_source_hypothesis;

  assert(
      hypothesis.data_source_key ==
      "source:urban-classification");

  assert(
      hypothesis.base.source_hypothesis_key ==
      "hypothesis.data.urban_signal_source_gap");

  assert(
      hypothesis.base.data_scope_key ==
      "local-only");

  assert(
      hypothesis.base.evidence_revision == 2);

  assert(
      !hypothesis.base.preference_interpretation_allowed);

  assert(
      !hypothesis.base.preference_target_created);

  assert(
      !hypothesis.base.preference_hypothesis_created);

  assert(
      !hypothesis.base.learning_gate_invoked);

  assert(
      !hypothesis.base.shadow_evaluation_created);

  assert(
      !hypothesis.base.automatic_fix_allowed);

  assert(
      !hypothesis.base.map_change_allowed);

  assert(
      !hypothesis.base.routing_change_allowed);

  assert(
      !hypothesis.base.production_application_allowed);

  assert(
      !hypothesis.base.evidence_scope_promotion_allowed);


  // Exact request is idempotent.
  const auto duplicate =
      workflow.apply(
          approval,
          request);

  assert(
      duplicate.status ==
      SemanticHypothesisMappingApplyStatus::
          DuplicateIgnored);

  assert(
      workflow.records().size() == 1);


  // Same mapping id may not acquire a new target.
  auto collision =
      request;

  collision.target_key =
      "source:different";

  bool collision_rejected =
      false;

  try {
    (void)workflow.apply(
        approval,
        collision);
  } catch (const std::invalid_argument&) {
    collision_rejected =
        true;
  }

  assert(
      collision_rejected);


  // Same approved candidate may not receive a second terminal mapping.
  auto second =
      request;

  second.mapping_id =
      "mapping:data-source:second";

  bool second_rejected =
      false;

  try {
    (void)workflow.apply(
        approval,
        second);
  } catch (const std::logic_error&) {
    second_rejected =
        true;
  }

  assert(
      second_rejected);


  const std::string report =
      format_semantic_hypothesis_mapping_report(
          mapped.record);

  assert(
      report.find(
          "SEMANTIC HYPOTHESIS MAPPING") !=
      std::string::npos);

  assert(
      report.find(
          "DATA SOURCE HYPOTHESIS") !=
      std::string::npos);

  assert(
      report.find(
          "preference interpretation allowed: no") !=
      std::string::npos);

  assert(
      report.find(
          "production application allowed: no") !=
      std::string::npos);


  std::cout
      << "Semantic hypothesis mapping tests passed\n";

  return 0;
}
