#pragma once

#include <sstream>
#include <string>

#include "routing/core/intelligence/semantic_hypothesis_mapping.hpp"

namespace routing::core::intelligence {

inline void
append_system_hypothesis_safety(
    std::ostringstream& output,
    const SystemHypothesisBase& base) {
  output
      << "  preference interpretation allowed: "
      << (base.preference_interpretation_allowed
              ? "yes"
              : "no")
      << "\n"
      << "  PreferenceTarget created: "
      << (base.preference_target_created
              ? "yes"
              : "no")
      << "\n"
      << "  PreferenceHypothesis created: "
      << (base.preference_hypothesis_created
              ? "yes"
              : "no")
      << "\n"
      << "  LearningGate invoked: "
      << (base.learning_gate_invoked
              ? "yes"
              : "no")
      << "\n"
      << "  ShadowEvaluation created: "
      << (base.shadow_evaluation_created
              ? "yes"
              : "no")
      << "\n"
      << "  automatic fix allowed: "
      << (base.automatic_fix_allowed
              ? "yes"
              : "no")
      << "\n"
      << "  map change allowed: "
      << (base.map_change_allowed
              ? "yes"
              : "no")
      << "\n"
      << "  routing change allowed: "
      << (base.routing_change_allowed
              ? "yes"
              : "no")
      << "\n"
      << "  production application allowed: "
      << (base.production_application_allowed
              ? "yes"
              : "no")
      << "\n"
      << "  evidence scope promotion allowed: "
      << (base.evidence_scope_promotion_allowed
              ? "yes"
              : "no")
      << "\n";
}


[[nodiscard]]
inline std::string
format_semantic_hypothesis_mapping_report(
    const SemanticHypothesisMappingRecord& record) {
  std::ostringstream output;

  output
      << "SEMANTIC HYPOTHESIS MAPPING\n"
      << "schema: "
      << record.schema_version
      << "\n"
      << "mapping id: "
      << record.mapping_id
      << "\n"
      << "decision: "
      << semantic_hypothesis_mapping_decision_key(
             record.decision)
      << "\n"
      << "system kind: "
      << system_hypothesis_kind_key(
             record.kind)
      << "\n"
      << "source approval: "
      << record.source_approval_id
      << "\n"
      << "source conversion candidate: "
      << record.source_conversion_candidate_id
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
      << "evidence revision: "
      << record.evidence_revision
      << "\n"
      << "source hypothesis key: "
      << record.source_hypothesis_key
      << "\n"
      << "mapper: "
      << record.mapper_ref
      << "\n"
      << "target key: "
      << record.target_key
      << "\n"
      << "rationale: "
      << record.rationale
      << "\n\n";


  if (record.data_source_hypothesis.has_value()) {
    const auto& hypothesis =
        *record.data_source_hypothesis;

    output
        << "DATA SOURCE HYPOTHESIS\n"
        << "  id: "
        << hypothesis.base.id
        << "\n"
        << "  data source key: "
        << hypothesis.data_source_key
        << "\n";

    append_system_hypothesis_safety(
        output,
        hypothesis.base);
  }


  if (record.backend_hypothesis.has_value()) {
    const auto& hypothesis =
        *record.backend_hypothesis;

    output
        << "BACKEND HYPOTHESIS\n"
        << "  id: "
        << hypothesis.base.id
        << "\n"
        << "  backend component key: "
        << hypothesis.backend_component_key
        << "\n";

    append_system_hypothesis_safety(
        output,
        hypothesis.base);
  }


  if (record.candidate_pipeline_hypothesis.has_value()) {
    const auto& hypothesis =
        *record.candidate_pipeline_hypothesis;

    output
        << "CANDIDATE PIPELINE HYPOTHESIS\n"
        << "  id: "
        << hypothesis.base.id
        << "\n"
        << "  pipeline stage key: "
        << hypothesis.pipeline_stage_key
        << "\n";

    append_system_hypothesis_safety(
        output,
        hypothesis.base);
  }


  if (record.decision ==
          SemanticHypothesisMappingDecision::Reject &&
      !record.data_source_hypothesis.has_value() &&
      !record.backend_hypothesis.has_value() &&
      !record.candidate_pipeline_hypothesis.has_value()) {
    output
        << "SYSTEM HYPOTHESIS\n"
        << "  created: no\n";
  }

  return output.str();
}

}  // namespace routing::core::intelligence
