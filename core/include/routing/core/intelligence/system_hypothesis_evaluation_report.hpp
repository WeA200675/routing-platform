#pragma once

#include <sstream>
#include <string>

#include "routing/core/intelligence/system_hypothesis_evaluation.hpp"

namespace routing::core::intelligence {

[[nodiscard]]
inline std::string
format_system_hypothesis_evaluation_report(
    const SystemHypothesisEvaluationRecord& record) {
  std::ostringstream output;

  output
      << "SYSTEM HYPOTHESIS EVALUATION\n"
      << "schema: "
      << record.schema_version
      << "\n"
      << "evaluation id: "
      << record.evaluation_id
      << "\n"
      << "evaluation revision: "
      << record.evaluation_revision
      << "\n"
      << "result: "
      << system_hypothesis_evaluation_result_key(
             record.result)
      << "\n"
      << "system hypothesis id: "
      << record.system_hypothesis_id
      << "\n"
      << "system kind: "
      << system_hypothesis_kind_key(
             record.hypothesis_kind)
      << "\n"
      << "target: "
      << record.hypothesis_target_key
      << "\n"
      << "source mapping: "
      << record.source_mapping_id
      << "\n"
      << "source approval: "
      << record.source_approval_id
      << "\n"
      << "cluster: "
      << record.cluster_key
      << "\n"
      << "context: "
      << record.context_key
      << "\n"
      << "data scope: "
      << record.data_scope_key
      << "\n"
      << "diagnostic: "
      << record.diagnostic_code
      << "\n"
      << "hypothesis evidence revision: "
      << record.hypothesis_evidence_revision
      << "\n"
      << "source hypothesis key: "
      << record.source_hypothesis_key
      << "\n"
      << "evaluator: "
      << record.evaluator_ref
      << "\n"
      << "supporting evidence: "
      << record.supporting_evidence_count
      << "\n"
      << "refuting evidence: "
      << record.refuting_evidence_count
      << "\n"
      << "context evidence: "
      << record.context_evidence_count
      << "\n"
      << "rationale: "
      << record.rationale
      << "\n\n"
      << "EVIDENCE\n";


  for (const auto& evidence :
       record.evidence) {
    output
        << "  - id: "
        << evidence.evidence_id
        << "\n"
        << "    source: "
        << evidence.source_ref
        << "\n"
        << "    relation: "
        << system_hypothesis_evidence_relation_key(
               evidence.relation)
        << "\n"
        << "    scope: "
        << evidence.data_scope_key
        << "\n"
        << "    context: "
        << evidence.context_key
        << "\n"
        << "    detail: "
        << evidence.detail
        << "\n";
  }


  output
      << "\nBOUNDARY\n"
      << "  remediation proposal created: "
      << (record.remediation_proposal_created
              ? "yes"
              : "no")
      << "\n"
      << "  preference interpretation allowed: "
      << (record.preference_interpretation_allowed
              ? "yes"
              : "no")
      << "\n"
      << "  PreferenceTarget created: "
      << (record.preference_target_created
              ? "yes"
              : "no")
      << "\n"
      << "  PreferenceHypothesis created: "
      << (record.preference_hypothesis_created
              ? "yes"
              : "no")
      << "\n"
      << "  LearningGate invoked: "
      << (record.learning_gate_invoked
              ? "yes"
              : "no")
      << "\n"
      << "  ShadowEvaluation created: "
      << (record.shadow_evaluation_created
              ? "yes"
              : "no")
      << "\n"
      << "  automatic fix allowed: "
      << (record.automatic_fix_allowed
              ? "yes"
              : "no")
      << "\n"
      << "  map change allowed: "
      << (record.map_change_allowed
              ? "yes"
              : "no")
      << "\n"
      << "  routing change allowed: "
      << (record.routing_change_allowed
              ? "yes"
              : "no")
      << "\n"
      << "  CostEngine change allowed: "
      << (record.cost_engine_change_allowed
              ? "yes"
              : "no")
      << "\n"
      << "  production application allowed: "
      << (record.production_application_allowed
              ? "yes"
              : "no")
      << "\n"
      << "  evidence scope promotion allowed: "
      << (record.evidence_scope_promotion_allowed
              ? "yes"
              : "no")
      << "\n";

  return output.str();
}

}  // namespace routing::core::intelligence
