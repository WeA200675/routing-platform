#pragma once

#include <cstdint>
#include <optional>
#include <stdexcept>
#include <string>
#include <string_view>
#include <utility>
#include <vector>

#include "routing/core/candidates/candidate_family_plan.hpp"
#include "routing/core/diagnostics/routing_diagnostic.hpp"

namespace routing::core::diagnostics {

inline constexpr std::uint32_t
kDiagnosticEvidenceSchemaVersion = 1;

enum class DiagnosticEvidenceSource : std::uint8_t {
  Runtime = 0,
  Scenario,
  RegressionCase,
  DriveSession,
  RouteLab,
  Imported,
};

enum class DiagnosticEvidenceScope : std::uint8_t {
  LocalOnly = 0,
  Personal,
  TesterShared,
  GlobalReference,
};

[[nodiscard]]
inline constexpr std::string_view
diagnostic_evidence_source_key(
    const DiagnosticEvidenceSource source) {
  switch (source) {
    case DiagnosticEvidenceSource::Runtime:
      return "runtime";

    case DiagnosticEvidenceSource::Scenario:
      return "scenario";

    case DiagnosticEvidenceSource::RegressionCase:
      return "regression-case";

    case DiagnosticEvidenceSource::DriveSession:
      return "drive-session";

    case DiagnosticEvidenceSource::RouteLab:
      return "route-lab";

    case DiagnosticEvidenceSource::Imported:
      return "imported";
  }

  return "unknown";
}

[[nodiscard]]
inline constexpr std::string_view
diagnostic_evidence_scope_key(
    const DiagnosticEvidenceScope scope) {
  switch (scope) {
    case DiagnosticEvidenceScope::LocalOnly:
      return "local-only";

    case DiagnosticEvidenceScope::Personal:
      return "personal";

    case DiagnosticEvidenceScope::TesterShared:
      return "tester-shared";

    case DiagnosticEvidenceScope::GlobalReference:
      return "global-reference";
  }

  return "unknown";
}

struct DiagnosticEvidenceContext {
  DiagnosticEvidenceSource source =
      DiagnosticEvidenceSource::Runtime;

  // Privacy boundary.
  // LocalOnly is deliberately the default.
  DiagnosticEvidenceScope evidence_scope =
      DiagnosticEvidenceScope::LocalOnly;

  // One independent routing/test/drive observation.
  std::string observation_id;

  // Stable provenance reference:
  // scenario id, regression case id, drive-session id, etc.
  std::string source_ref;

  // Explicit reviewed grouping context:
  // corridor, fixture, region, test area, ...
  //
  // Never inferred from route_id.
  std::string context_key;

  // Evidence provenance only.
  // Not part of cluster identity so recurrence can be observed
  // across versions.
  std::string version_ref;

  std::optional<std::uint64_t>
      observed_at_ms;
};

struct DiagnosticEvidenceRecord {
  std::uint32_t schema_version =
      kDiagnosticEvidenceSchemaVersion;

  std::string record_id;

  DiagnosticEvidenceSource source =
      DiagnosticEvidenceSource::Runtime;

  DiagnosticEvidenceScope evidence_scope =
      DiagnosticEvidenceScope::LocalOnly;

  std::string observation_id;
  std::string source_ref;
  std::string context_key;
  std::string version_ref;

  std::optional<std::uint64_t>
      observed_at_ms;

  RoutingDiagnostic diagnostic;
};

inline void
validate_diagnostic_evidence_context(
    const DiagnosticEvidenceContext& context) {
  if (context.observation_id.empty()) {
    throw std::invalid_argument(
        "Diagnostic evidence requires observation_id.");
  }

  if (context.source_ref.empty()) {
    throw std::invalid_argument(
        "Diagnostic evidence requires source_ref.");
  }

  if (context.context_key.empty()) {
    throw std::invalid_argument(
        "Diagnostic evidence requires explicit context_key.");
  }
}

inline void
validate_diagnostic_evidence_record(
    const DiagnosticEvidenceRecord& record) {
  if (record.schema_version !=
      kDiagnosticEvidenceSchemaVersion) {
    throw std::invalid_argument(
        "Unsupported diagnostic evidence schema version.");
  }

  if (record.record_id.empty()) {
    throw std::invalid_argument(
        "Diagnostic evidence requires record_id.");
  }

  DiagnosticEvidenceContext context;

  context.source =
      record.source;

  context.evidence_scope =
      record.evidence_scope;

  context.observation_id =
      record.observation_id;

  context.source_ref =
      record.source_ref;

  context.context_key =
      record.context_key;

  context.version_ref =
      record.version_ref;

  context.observed_at_ms =
      record.observed_at_ms;

  validate_diagnostic_evidence_context(
      context);

  if (record.diagnostic.code.empty()) {
    throw std::invalid_argument(
        "Diagnostic evidence requires diagnostic.code.");
  }
}

[[nodiscard]]
inline std::string
diagnostic_key_component(
    const std::string_view value) {
  std::string result;

  result.reserve(
      value.size());

  for (const char character :
       value) {
    switch (character) {
      case '%':
        result += "%25";
        break;

      case '|':
        result += "%7C";
        break;

      case '\n':
        result += "%0A";
        break;

      case '\r':
        result += "%0D";
        break;

      default:
        result.push_back(
            character);
        break;
    }
  }

  return result;
}

[[nodiscard]]
inline std::vector<DiagnosticEvidenceRecord>
make_diagnostic_evidence_records(
    const std::vector<RoutingDiagnostic>& diagnostics,
    const DiagnosticEvidenceContext& context) {
  validate_diagnostic_evidence_context(
      context);

  std::vector<DiagnosticEvidenceRecord>
      result;

  result.reserve(
      diagnostics.size());

  for (std::size_t index = 0;
       index < diagnostics.size();
       ++index) {
    DiagnosticEvidenceRecord record;

    record.record_id =
        context.observation_id +
        ":diagnostic:" +
        std::to_string(
            index + 1);

    record.source =
        context.source;

    record.evidence_scope =
        context.evidence_scope;

    record.observation_id =
        context.observation_id;

    record.source_ref =
        context.source_ref;

    record.context_key =
        context.context_key;

    record.version_ref =
        context.version_ref;

    record.observed_at_ms =
        context.observed_at_ms;

    record.diagnostic =
        diagnostics[index];

    validate_diagnostic_evidence_record(
        record);

    result.push_back(
        std::move(record));
  }

  return result;
}

// Stable textual grouping identity.
//
// It is deliberately NOT described as a hash or cryptographic fingerprint.
//
// Included:
//   privacy/share scope
//   explicit context
//   diagnostic category
//   diagnostic scope
//   diagnostic code
//   family only for Family-scoped diagnostics
//
// Excluded:
//   route_id
//   source_ref
//   observation_id
//   version_ref
//
// Therefore repeated observations can accumulate across route instances
// and versions without silently crossing privacy or context boundaries.
[[nodiscard]]
inline std::string
diagnostic_cluster_key(
    const DiagnosticEvidenceRecord& record) {
  validate_diagnostic_evidence_record(
      record);

  std::string key =
      "diagnostic-cluster-v1";

  key +=
      "|evidence_scope=" +
      diagnostic_key_component(
          diagnostic_evidence_scope_key(
              record.evidence_scope));

  key +=
      "|context=" +
      diagnostic_key_component(
          record.context_key);

  key +=
      "|category=" +
      diagnostic_key_component(
          diagnostic_category_key(
              record.diagnostic.category));

  key +=
      "|diagnostic_scope=" +
      diagnostic_key_component(
          diagnostic_scope_key(
              record.diagnostic.scope));

  key +=
      "|code=" +
      diagnostic_key_component(
          record.diagnostic.code);

  if (record.diagnostic.scope ==
      DiagnosticScope::Family) {
    key +=
        "|family=";

    if (record.diagnostic.family.has_value()) {
      key +=
          diagnostic_key_component(
              candidates::candidate_family_key(
                  *record.diagnostic.family));
    } else {
      key +=
          "none";
    }
  }

  return key;
}

}  // namespace routing::core::diagnostics
