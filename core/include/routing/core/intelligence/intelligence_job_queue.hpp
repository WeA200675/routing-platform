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
};

class IntelligenceJobQueue {
 public:
  void enqueue(IntelligenceJob job);

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
