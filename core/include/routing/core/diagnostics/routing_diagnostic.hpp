#pragma once

#include <cstdint>
#include <optional>
#include <string>
#include <string_view>
#include <vector>

#include "routing/core/routing_engine.hpp"

namespace routing::core::diagnostics {

// Diagnostics are observational.
//
// They are deliberately not routing rules, CostEngine contributions,
// candidate penalties or regression assertions.
enum class DiagnosticSeverity : std::uint8_t {
  Info = 0,
  Warning,
  Error,
};

enum class DiagnosticCategory : std::uint8_t {
  Backend = 0,
  Enrichment,
  DataCoverage,
  DataSignal,
  CandidateSet,
};

enum class DiagnosticScope : std::uint8_t {
  Orchestration = 0,
  Family,
  Route,
};

struct DiagnosticEvidence {
  // Stable machine-readable evidence key.
  std::string key;

  double value = 0.0;

  // Examples: "ratio", "m", "count".
  std::string unit;
};

struct RoutingDiagnostic {
  // Stable machine-readable code.
  std::string code;

  DiagnosticSeverity severity =
      DiagnosticSeverity::Info;

  DiagnosticCategory category =
      DiagnosticCategory::DataSignal;

  DiagnosticScope scope =
      DiagnosticScope::Route;

  std::optional<CandidateFamily> family;

  std::string route_id;

  // Stable explanation key for UI/localisation.
  std::string explanation_key;

  // Human-readable factual detail.
  // This must not claim causality that was not observed.
  std::string detail;

  std::vector<DiagnosticEvidence>
      evidence;
};

[[nodiscard]]
inline constexpr std::string_view
diagnostic_severity_key(
    const DiagnosticSeverity value) {
  switch (value) {
    case DiagnosticSeverity::Info:
      return "info";

    case DiagnosticSeverity::Warning:
      return "warning";

    case DiagnosticSeverity::Error:
      return "error";
  }

  return "unknown";
}

[[nodiscard]]
inline constexpr std::string_view
diagnostic_category_key(
    const DiagnosticCategory value) {
  switch (value) {
    case DiagnosticCategory::Backend:
      return "backend";

    case DiagnosticCategory::Enrichment:
      return "enrichment";

    case DiagnosticCategory::DataCoverage:
      return "data-coverage";

    case DiagnosticCategory::DataSignal:
      return "data-signal";

    case DiagnosticCategory::CandidateSet:
      return "candidate-set";
  }

  return "unknown";
}

[[nodiscard]]
inline constexpr std::string_view
diagnostic_scope_key(
    const DiagnosticScope value) {
  switch (value) {
    case DiagnosticScope::Orchestration:
      return "orchestration";

    case DiagnosticScope::Family:
      return "family";

    case DiagnosticScope::Route:
      return "route";
  }

  return "unknown";
}

}  // namespace routing::core::diagnostics
