#pragma once

#include <cstdint>
#include <optional>
#include <string>
#include <string_view>
#include <vector>

#include "routing/core/drive/drive_evidence.hpp"
#include "routing/core/testing/regression_case.hpp"

namespace routing::core::testing {

enum class RegressionPromotionReadiness : std::uint8_t {
  MetricSuggested = 0,
  AlternativeSuggested,
  DiagnosticOnly,
  NeedsReview,
};

enum class MetricImprovementDirection : std::uint8_t {
  LowerIsBetter = 0,
  HigherIsBetter,
};

struct RegressionMetricSuggestion {
  RouteMetric metric =
      RouteMetric::Speed30OrLowerShare;

  MetricImprovementDirection direction =
      MetricImprovementDirection::LowerIsBetter;

  // Stable explanation key. This is not a threshold and not
  // an automatically approved routing preference.
  std::string reason_key;
};

struct RegressionPromotionProposal {
  std::string proposal_id;

  std::string session_id;
  std::string source_event_id;

  drive::EvidenceKind evidence_kind =
      drive::EvidenceKind::ExplicitFeedback;

  std::vector<std::string> evidence_ids;

  RegressionPromotionReadiness readiness =
      RegressionPromotionReadiness::NeedsReview;

  std::string issue_key;
  std::string title;
  std::string summary;

  std::string observed_route_id;
  std::string preferred_alternative_route_id;

  std::optional<RegressionMetricSuggestion>
      metric_suggestion;

  // Preserve the exact version references recorded by the drive.
  // These are evidence/provenance, not executable runtime objects.
  drive::VersionSnapshot source_versions;

  std::vector<std::string> context_tags;

  // Reconstructable location/request portion of the future scenario.
  // VehicleProfile, RuleSet and RoutingContext are intentionally NOT
  // claimed to be reconstructed from version identifiers.
  RoutingScenario scenario_seed;

  bool runtime_semantics_complete = false;

  std::vector<std::string>
      missing_runtime_inputs;

  // Always true in v1. No proposal can silently become a regression.
  bool human_approval_required = true;
};

struct RegressionPromotionApproval {
  bool approved = false;

  // Reviewer/resolver confirms that the actual desired VehicleProfile,
  // RuleSet and RoutingContext have been supplied in reviewed_scenario.
  bool confirmed_runtime_semantics = false;

  // Reviewer confirms that the scenario expectations really encode
  // the reported problem rather than merely "route succeeds".
  bool confirmed_expectation_captures_issue = false;

  RegressionDisposition disposition =
      RegressionDisposition::ObserveOnly;

  // A separate explicit switch is required before Gating is possible.
  bool allow_gating = false;

  std::string case_id;
  std::string title;
  std::string issue_key;

  RoutingScenario reviewed_scenario;
};

[[nodiscard]]
std::string_view regression_promotion_readiness_key(
    RegressionPromotionReadiness readiness);

[[nodiscard]]
std::string_view metric_improvement_direction_key(
    MetricImprovementDirection direction);

[[nodiscard]]
std::vector<RegressionPromotionProposal>
build_regression_promotion_proposals(
    const drive::DriveSession& session);

[[nodiscard]]
std::optional<RoutingRegressionCase>
approve_regression_promotion(
    const RegressionPromotionProposal& proposal,
    const RegressionPromotionApproval& approval);

}  // namespace routing::core::testing
