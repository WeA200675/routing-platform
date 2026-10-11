#pragma once

#include <cstddef>
#include <cstdint>
#include <optional>
#include <string>
#include <string_view>
#include <vector>

#include "routing/core/rule.hpp"

namespace routing::core::intelligence {

enum class MemoryScope : std::uint8_t {
  ShortTerm = 0,
  LongTerm,
  Contextual,
};

struct LearningObservation {
  std::string id;

  Attribute attribute =
      Attribute::FunctionalRoadClass;

  // -1.0 .. +1.0
  double preference_signal = 0.0;

  // 0.0 .. 1.0
  double confidence = 0.0;

  MemoryScope scope =
      MemoryScope::ShortTerm;

  // Required for Contextual.
  std::string context_key;
};

struct LearningSummary {
  std::size_t observation_count = 0;

  double confidence_weighted_signal = 0.0;
  double mean_confidence = 0.0;
};

enum class LearnedKnowledgeKind : std::uint8_t {
  Fact = 0,
  Preference,
  Correction,
  InteractionPattern,
  SocialPreference,
};

struct LearnedKnowledge {
  std::string id;

  LearnedKnowledgeKind kind =
      LearnedKnowledgeKind::Fact;

  std::string key;
  std::string value;

  // 0.0 .. 1.0
  double confidence = 0.0;

  MemoryScope scope =
      MemoryScope::LongTerm;

  // Required for Contextual.
  std::string context_key;

  // Provenance such as explicit-user, conversation, or observation.
  std::string source;

  std::uint64_t observed_at_epoch_ms = 0;

  // Explicit user choices can be protected from automatic replacement.
  bool user_locked = false;
};

class LearningMemory {
 public:
  void add(LearningObservation observation);

  void add_knowledge(
      LearnedKnowledge knowledge);

  [[nodiscard]] LearningSummary summarize(
      Attribute attribute,
      MemoryScope scope,
      std::optional<std::string_view> context =
          std::nullopt) const;

  [[nodiscard]] const LearningObservation*
  find(std::string_view id) const;

  [[nodiscard]] const LearnedKnowledge*
  find_knowledge(std::string_view id) const;

  [[nodiscard]]
  std::vector<const LearnedKnowledge*>
  find_knowledge_by_key(
      LearnedKnowledgeKind kind,
      std::string_view key,
      MemoryScope scope,
      std::optional<std::string_view> context =
          std::nullopt) const;

  bool erase(std::string_view id);

  bool erase_knowledge(
      std::string_view id);

  void clear();

  // Legacy observation count.
  [[nodiscard]] std::size_t size() const;

  [[nodiscard]]
  std::size_t knowledge_size() const;

 private:
  std::vector<LearningObservation> observations_;
  std::vector<LearnedKnowledge> knowledge_;
};

}  // namespace routing::core::intelligence
