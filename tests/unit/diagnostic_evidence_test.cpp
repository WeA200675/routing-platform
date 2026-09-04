#include <cassert>
#include <iostream>
#include <string>
#include <vector>

#include "routing/core/diagnostics/diagnostic_evidence.hpp"

int main() {
  using namespace routing::core;
  using namespace routing::core::diagnostics;

  RoutingDiagnostic first;

  first.code =
      "DATA_URBAN_POSITIVE_SIGNAL_ABSENT";

  first.severity =
      DiagnosticSeverity::Info;

  first.category =
      DiagnosticCategory::DataSignal;

  first.scope =
      DiagnosticScope::Route;

  first.family =
      CandidateFamily::Fastest;

  first.route_id =
      "route-a";

  first.explanation_key =
      "diagnostic.data.urban.positive_signal_absent";

  RoutingDiagnostic second =
      first;

  second.family =
      CandidateFamily::MajorRoads;

  second.route_id =
      "route-b";

  DiagnosticEvidenceContext context;

  context.source =
      DiagnosticEvidenceSource::Scenario;

  context.evidence_scope =
      DiagnosticEvidenceScope::LocalOnly;

  context.observation_id =
      "observation:1";

  context.source_ref =
      "scenario:urban-watch";

  context.context_key =
      "li:vaduz-ruggell";

  context.version_ref =
      "fixture:v1";

  context.observed_at_ms =
      1000;

  const auto records =
      make_diagnostic_evidence_records(
          {first, second},
          context);

  assert(records.size() == 2);

  assert(
      records[0].record_id ==
      "observation:1:diagnostic:1");

  assert(
      records[1].record_id ==
      "observation:1:diagnostic:2");

  // Different route/family instances of a Route-scoped data
  // observation belong to the same explicit contextual cluster.
  assert(
      diagnostic_cluster_key(
          records[0]) ==
      diagnostic_cluster_key(
          records[1]));

  auto personal =
      records[0];

  personal.record_id =
      "personal:1";

  personal.observation_id =
      "personal-observation:1";

  personal.evidence_scope =
      DiagnosticEvidenceScope::Personal;

  // Privacy is a hard boundary.
  assert(
      diagnostic_cluster_key(
          personal) !=
      diagnostic_cluster_key(
          records[0]));

  auto other_context =
      records[0];

  other_context.record_id =
      "other-context:1";

  other_context.observation_id =
      "other-context-observation";

  other_context.context_key =
      "li:other-corridor";

  // Explicit context is a hard boundary.
  assert(
      diagnostic_cluster_key(
          other_context) !=
      diagnostic_cluster_key(
          records[0]));

  auto family_a =
      records[0];

  family_a.record_id =
      "family-a:1";

  family_a.observation_id =
      "family-a-observation";

  family_a.diagnostic.scope =
      DiagnosticScope::Family;

  family_a.diagnostic.family =
      CandidateFamily::MajorRoads;

  auto family_b =
      family_a;

  family_b.record_id =
      "family-b:1";

  family_b.observation_id =
      "family-b-observation";

  family_b.diagnostic.family =
      CandidateFamily::LowUrban;

  // Family-scoped operational problems remain family-specific.
  assert(
      diagnostic_cluster_key(
          family_a) !=
      diagnostic_cluster_key(
          family_b));

  const auto family_key =
      diagnostic_cluster_key(
          family_a);

  // Reuse the platform's existing canonical candidate-family key.
  // Diagnostic clustering must not invent a second spelling.
  const std::string canonical_major_roads_key{
      candidates::candidate_family_key(
          CandidateFamily::MajorRoads)};

  assert(
      !canonical_major_roads_key.empty());

  assert(
      family_key.find(
          "family=" +
          canonical_major_roads_key) !=
      std::string::npos);

  // Never serialize the enum ordinal as semantic identity.
  assert(
      family_key.find(
          "family=3") ==
      std::string::npos);

  assert(
      diagnostic_cluster_key(
          records[0])
          .find("diagnostic-cluster-v1|") == 0);

  std::cout
      << "Diagnostic evidence tests passed\n";

  return 0;
}
