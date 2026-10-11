#pragma once

#include <algorithm>
#include <cstddef>
#include <cstdint>
#include <optional>
#include <string>
#include <string_view>
#include <unordered_set>
#include <utility>
#include <vector>

#include "routing/core/diagnostics/diagnostic_evidence.hpp"

namespace routing::core::diagnostics {

inline constexpr std::uint32_t
kAnomalyClusterSchemaVersion = 1;

inline constexpr std::size_t
kDefaultAnomalyEvidenceSampleLimit = 16;

enum class InvestigationState : std::uint8_t {
  Observed = 0,
  Investigating,
  HypothesisProposed,
  Resolved,
  Dismissed,
};

[[nodiscard]]
inline constexpr std::string_view
investigation_state_key(
    const InvestigationState state) {
  switch (state) {
    case InvestigationState::Observed:
      return "observed";

    case InvestigationState::Investigating:
      return "investigating";

    case InvestigationState::HypothesisProposed:
      return "hypothesis-proposed";

    case InvestigationState::Resolved:
      return "resolved";

    case InvestigationState::Dismissed:
      return "dismissed";
  }

  return "unknown";
}

struct AnomalyCluster {
  std::uint32_t schema_version =
      kAnomalyClusterSchemaVersion;

  std::string cluster_key;

  DiagnosticEvidenceScope evidence_scope =
      DiagnosticEvidenceScope::LocalOnly;

  std::string context_key;
  std::string diagnostic_code;

  DiagnosticCategory category =
      DiagnosticCategory::DataSignal;

  DiagnosticScope diagnostic_scope =
      DiagnosticScope::Route;

  std::optional<CandidateFamily>
      grouped_family;

  DiagnosticSeverity max_severity =
      DiagnosticSeverity::Info;

  InvestigationState state =
      InvestigationState::Observed;

  // Individual diagnostic records.
  std::uint64_t occurrence_count = 0;

  // Independent routing/test/drive observations.
  //
  // Investigation thresholds use this collection, not occurrence_count.
  std::vector<std::string>
      observation_ids;

  std::vector<std::string>
      source_refs;

  std::vector<std::string>
      affected_route_ids;

  std::vector<CandidateFamily>
      affected_families;

  std::vector<std::string>
      version_refs;

  std::optional<std::uint64_t>
      first_seen_ms;

  std::optional<std::uint64_t>
      last_seen_ms;

  // Exact evidence retention is intentionally bounded.
  std::vector<DiagnosticEvidenceRecord>
      evidence_samples;
};

enum class AnomalyIngestStatus : std::uint8_t {
  AddedNewCluster = 0,
  AddedExistingCluster,
  DuplicateIgnored,
};

struct AnomalyIngestResult {
  AnomalyIngestStatus status =
      AnomalyIngestStatus::DuplicateIgnored;

  std::string cluster_key;
};

namespace detail {

template <typename T>
inline void add_unique(
    std::vector<T>& values,
    const T& value) {
  if (std::find(
          values.begin(),
          values.end(),
          value) !=
      values.end()) {
    return;
  }

  values.push_back(
      value);
}

inline bool severity_greater(
    const DiagnosticSeverity left,
    const DiagnosticSeverity right) {
  return
      static_cast<std::uint8_t>(left) >
      static_cast<std::uint8_t>(right);
}

}  // namespace detail


class AnomalyTracker {
 public:
  explicit AnomalyTracker(
      const std::size_t evidence_sample_limit =
          kDefaultAnomalyEvidenceSampleLimit)
      : evidence_sample_limit_(
            evidence_sample_limit) {}

  [[nodiscard]]
  const std::vector<AnomalyCluster>&
  clusters() const noexcept {
    return clusters_;
  }

  [[nodiscard]]
  std::size_t size() const noexcept {
    return clusters_.size();
  }

  [[nodiscard]]
  const AnomalyCluster*
  find(
      const std::string& cluster_key) const {
    const auto found =
        std::find_if(
            clusters_.begin(),
            clusters_.end(),
            [&](const auto& cluster) {
              return cluster.cluster_key ==
                  cluster_key;
            });

    return found == clusters_.end()
        ? nullptr
        : &*found;
  }

  [[nodiscard]]
  AnomalyCluster*
  find(
      const std::string& cluster_key) {
    const auto found =
        std::find_if(
            clusters_.begin(),
            clusters_.end(),
            [&](auto& cluster) {
              return cluster.cluster_key ==
                  cluster_key;
            });

    return found == clusters_.end()
        ? nullptr
        : &*found;
  }

  [[nodiscard]]
  AnomalyIngestResult ingest(
      const DiagnosticEvidenceRecord& record) {
    validate_diagnostic_evidence_record(
        record);

    const std::string cluster_key =
        diagnostic_cluster_key(
            record);

    if (seen_record_ids_.find(
            record.record_id) !=
        seen_record_ids_.end()) {
      return {
          AnomalyIngestStatus::DuplicateIgnored,
          cluster_key,
      };
    }

    AnomalyCluster* cluster =
        find(
            cluster_key);

    const bool new_cluster =
        cluster == nullptr;

    if (new_cluster) {
      AnomalyCluster created;

      created.cluster_key =
          cluster_key;

      created.evidence_scope =
          record.evidence_scope;

      created.context_key =
          record.context_key;

      created.diagnostic_code =
          record.diagnostic.code;

      created.category =
          record.diagnostic.category;

      created.diagnostic_scope =
          record.diagnostic.scope;

      if (record.diagnostic.scope ==
          DiagnosticScope::Family) {
        created.grouped_family =
            record.diagnostic.family;
      }

      created.max_severity =
          record.diagnostic.severity;

      clusters_.push_back(
          std::move(created));

      cluster =
          &clusters_.back();
    }

    seen_record_ids_.insert(
        record.record_id);

    apply_record(
        *cluster,
        record);

    return {
        new_cluster
            ? AnomalyIngestStatus::AddedNewCluster
            : AnomalyIngestStatus::AddedExistingCluster,
        cluster_key,
    };
  }

  void ingest(
      const std::vector<DiagnosticEvidenceRecord>& records) {
    for (const auto& record :
         records) {
      (void)ingest(
          record);
    }
  }

  // Explicit workflow action only.
  // Evidence ingestion never changes investigation state automatically.
  [[nodiscard]]
  bool set_investigation_state(
      const std::string& cluster_key,
      const InvestigationState state) {
    auto* cluster =
        find(
            cluster_key);

    if (cluster == nullptr) {
      return false;
    }

    cluster->state =
        state;

    return true;
  }

 private:
  void apply_record(
      AnomalyCluster& cluster,
      const DiagnosticEvidenceRecord& record) {
    ++cluster.occurrence_count;

    if (detail::severity_greater(
            record.diagnostic.severity,
            cluster.max_severity)) {
      cluster.max_severity =
          record.diagnostic.severity;
    }

    detail::add_unique(
        cluster.observation_ids,
        record.observation_id);

    detail::add_unique(
        cluster.source_refs,
        record.source_ref);

    if (!record.diagnostic.route_id.empty()) {
      detail::add_unique(
          cluster.affected_route_ids,
          record.diagnostic.route_id);
    }

    if (record.diagnostic.family.has_value()) {
      detail::add_unique(
          cluster.affected_families,
          *record.diagnostic.family);
    }

    if (!record.version_ref.empty()) {
      detail::add_unique(
          cluster.version_refs,
          record.version_ref);
    }

    if (record.observed_at_ms.has_value()) {
      if (!cluster.first_seen_ms.has_value() ||
          *record.observed_at_ms <
              *cluster.first_seen_ms) {
        cluster.first_seen_ms =
            record.observed_at_ms;
      }

      if (!cluster.last_seen_ms.has_value() ||
          *record.observed_at_ms >
              *cluster.last_seen_ms) {
        cluster.last_seen_ms =
            record.observed_at_ms;
      }
    }

    if (cluster.evidence_samples.size() <
        evidence_sample_limit_) {
      cluster.evidence_samples.push_back(
          record);
    }
  }

  std::size_t evidence_sample_limit_ =
      kDefaultAnomalyEvidenceSampleLimit;

  std::unordered_set<std::string>
      seen_record_ids_;

  std::vector<AnomalyCluster>
      clusters_;
};

}  // namespace routing::core::diagnostics
