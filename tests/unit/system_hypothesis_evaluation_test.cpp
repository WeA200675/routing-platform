#include <cassert>
#include <iostream>
#include <stdexcept>
#include <string>

#include "routing/core/intelligence/system_hypothesis_evaluation.hpp"
#include "routing/core/intelligence/system_hypothesis_evaluation_report.hpp"

namespace {

routing::core::intelligence::SemanticHypothesisMappingRecord
make_mapping() {
  using namespace routing::core::intelligence;

  SemanticHypothesisMappingRecord mapping;

  mapping.mapping_id =
      "mapping:data-source:1";

  mapping.source_approval_id =
      "approval:data-source:1";

  mapping.source_conversion_candidate_id =
      "conversion:data-source:1";

  mapping.cluster_key =
      "cluster:data-source:1";

  mapping.context_key =
      "context:data-source:1";

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
      "Explicit source-data mapping.";


  DataSourceHypothesis hypothesis;

  hypothesis.base.id =
      "system-hypothesis-v1|mapping:data-source:1";

  hypothesis.base.source_mapping_id =
      mapping.mapping_id;

  hypothesis.base.source_approval_id =
      mapping.source_approval_id;

  hypothesis.base.source_conversion_candidate_id =
      mapping.source_conversion_candidate_id;

  hypothesis.base.source_outcome_id =
      "outcome:data-source:1";

  hypothesis.base.source_review_id =
      "review:data-source:1";

  hypothesis.base.source_analysis_id =
      "analysis:data-source:1";

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


routing::core::intelligence::SystemHypothesisEvidenceReference
supporting_evidence(
    const std::string& id,
    const std::string& detail) {
  using namespace routing::core::intelligence;

  SystemHypothesisEvidenceReference evidence;

  evidence.evidence_id =
      id;

  evidence.source_ref =
      "regression:urban-source-check";

  evidence.data_scope_key =
      "local-only";

  evidence.context_key =
      "context:data-source:1";

  evidence.relation =
      SystemHypothesisEvidenceRelation::Supports;

  evidence.detail =
      detail;

  return evidence;
}

}  // namespace


int main() {
  using namespace routing::core::intelligence;

  const auto mapping =
      make_mapping();

  SystemHypothesisEvaluationWorkflow workflow;

  SystemHypothesisEvaluationRequest request;

  request.evaluation_id =
      "evaluation:data-source:1";

  request.evaluator_ref =
      "evaluator:alpha";

  request.evaluation_revision =
      1;

  request.result =
      SystemHypothesisEvaluationResult::Supported;

  request.evidence = {
      supporting_evidence(
          "evidence:source-check:1",
          "Independent source review reproduces the coverage gap."),
  };

  request.rationale =
      "Current reviewed evidence supports the system hypothesis.";


  const auto created =
      workflow.apply(
          mapping,
          request);

  assert(
      created.status ==
      SystemHypothesisEvaluationApplyStatus::Created);

  assert(
      workflow.records().size() == 1);

  assert(
      created.record.result ==
      SystemHypothesisEvaluationResult::Supported);

  assert(
      created.record.evaluation_revision == 1);

  assert(
      created.record.supporting_evidence_count == 1);

  assert(
      created.record.refuting_evidence_count == 0);

  assert(
      created.record.data_scope_key ==
      "local-only");

  assert(
      !created.record.remediation_proposal_created);

  assert(
      !created.record.preference_interpretation_allowed);

  assert(
      !created.record.preference_target_created);

  assert(
      !created.record.preference_hypothesis_created);

  assert(
      !created.record.learning_gate_invoked);

  assert(
      !created.record.shadow_evaluation_created);

  assert(
      !created.record.automatic_fix_allowed);

  assert(
      !created.record.map_change_allowed);

  assert(
      !created.record.routing_change_allowed);

  assert(
      !created.record.cost_engine_change_allowed);

  assert(
      !created.record.production_application_allowed);

  assert(
      !created.record.evidence_scope_promotion_allowed);


  // Exact request is idempotent.
  const auto duplicate =
      workflow.apply(
          mapping,
          request);

  assert(
      duplicate.status ==
      SystemHypothesisEvaluationApplyStatus::
          DuplicateIgnored);

  assert(
      workflow.records().size() == 1);


  // Same id cannot acquire a different conclusion.
  auto collision =
      request;

  collision.result =
      SystemHypothesisEvaluationResult::Inconclusive;

  bool collision_rejected =
      false;

  try {
    (void)workflow.apply(
        mapping,
        collision);
  } catch (const std::invalid_argument&) {
    collision_rejected =
        true;
  }

  assert(
      collision_rejected);


  // Revision may not skip.
  auto skipped =
      request;

  skipped.evaluation_id =
      "evaluation:data-source:3";

  skipped.evaluation_revision =
      3;

  skipped.result =
      SystemHypothesisEvaluationResult::Inconclusive;

  skipped.rationale =
      "Skipped revision must fail.";

  bool skipped_rejected =
      false;

  try {
    (void)workflow.apply(
        mapping,
        skipped);
  } catch (const std::logic_error&) {
    skipped_rejected =
        true;
  }

  assert(
      skipped_rejected);


  // Later reviewed evidence can create a new explicit revision.
  SystemHypothesisEvaluationRequest second;

  second.evaluation_id =
      "evaluation:data-source:2";

  second.evaluator_ref =
      "evaluator:beta";

  second.evaluation_revision =
      2;

  second.result =
      SystemHypothesisEvaluationResult::Inconclusive;

  second.evidence = {
      supporting_evidence(
          "evidence:source-check:2",
          "A later check supports part of the hypothesis but is not decisive."),
  };

  second.rationale =
      "Additional evidence is not yet decisive.";


  const auto revision_two =
      workflow.apply(
          mapping,
          second);

  assert(
      revision_two.status ==
      SystemHypothesisEvaluationApplyStatus::Created);

  assert(
      revision_two.record.evaluation_revision == 2);

  assert(
      revision_two.record.result ==
      SystemHypothesisEvaluationResult::Inconclusive);

  assert(
      workflow.records().size() == 2);


  const std::string report =
      format_system_hypothesis_evaluation_report(
          created.record);

  assert(
      report.find(
          "SYSTEM HYPOTHESIS EVALUATION") !=
      std::string::npos);

  assert(
      report.find(
          "result: supported") !=
      std::string::npos);

  assert(
      report.find(
          "remediation proposal created: no") !=
      std::string::npos);

  assert(
      report.find(
          "production application allowed: no") !=
      std::string::npos);


  std::cout
      << "System hypothesis evaluation tests passed\n";

  return 0;
}
