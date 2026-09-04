#pragma once

#include <cstddef>
#include <cstdint>
#include <optional>
#include <string>
#include <string_view>
#include <vector>

#include "routing/core/intelligence/resource_governor.hpp"

namespace routing::core::intelligence {

enum class IntelligenceJobType : std::uint8_t {
  SummarizeDrive = 0,
  ClassifyDeviation,
  CompareAlternatives,
  UpdatePreference,
  SelectQuestion,
  ShadowRoute,
  ClusterProblem,
  DeepAnalysis,
};

enum class IntelligenceJobState : std::uint8_t {
  Pending = 0,
  Running,
  Deferred,
  Completed,
  Failed,
};

struct IntelligenceJob {
  std::string id;

  IntelligenceJobType type =
      IntelligenceJobType::SummarizeDrive;

  WorkloadClass workload =
      WorkloadClass::PostDrive;

  // 0..100
  std::uint8_t priority = 50;

  bool requires_network = false;
  bool requires_charging = false;

  std::uint8_t minimum_battery_percent = 0;

  IntelligenceJobState state =
      IntelligenceJobState::Pending;

  std::uint32_t attempts = 0;

  // Stable identity of the thing to analyse.
  //
  // For ClusterProblem this is the AnomalyCluster cluster_key.
  std::string subject_key;

  // Explicit reviewed context boundary.
  std::string context_key;

  // Stable data/privacy scope key.
  //
  // This is descriptive metadata, not permission to publish or
  // promote evidence.
  std::string data_scope_key;

  // Why this work was requested.
  std::string reason_key;

  // Monotonic evidence revision known when the request was created.
  //
  // For diagnostic investigation jobs this is the distinct
  // observation count, not route-level occurrence_count.
  std::uint64_t evidence_revision = 0;
};


enum class IntelligenceJobEnqueueStatus : std::uint8_t {
  Added = 0,

  // Existing Pending/Deferred job was updated conservatively.
  Coalesced,

  // Running work is never mutated underneath a worker.
  ExistingRunning,

  // Completed/Failed work is never silently reopened.
  ExistingTerminal,
};

struct IntelligenceJobEnqueueResult {
  IntelligenceJobEnqueueStatus status =
      IntelligenceJobEnqueueStatus::Added;

  std::string id;
};


class IntelligenceJobQueue {
 public:
  // Existing strict API.
  //
  // Duplicate IDs remain an error.
  void enqueue(IntelligenceJob job);

  // Idempotent request path for producers that use stable job IDs.
  //
  // Only Pending/Deferred work may be coalesced.
  // Running and terminal work are not mutated or reopened.
  [[nodiscard]]
  IntelligenceJobEnqueueResult
  enqueue_or_coalesce(
      IntelligenceJob job);

  [[nodiscard]] std::optional<IntelligenceJob>
  claim_next(const ResourceSnapshot& resources);

  void mark_completed(std::string_view id);
  void mark_failed(std::string_view id);
  void mark_deferred(std::string_view id);

  [[nodiscard]] const IntelligenceJob*
  find(std::string_view id) const;

  [[nodiscard]] std::size_t size() const;

 private:
  struct Entry {
    IntelligenceJob job;
    std::uint64_t sequence = 0;
  };

  [[nodiscard]] Entry*
  find_mutable(std::string_view id);

  void transition(
      std::string_view id,
      IntelligenceJobState target);

  std::vector<Entry> entries_;
  std::uint64_t next_sequence_ = 0;
};

}  // namespace routing::core::intelligence
