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

  // Pflicht bei Contextual.
  std::string context_key;
};

struct LearningSummary {
  std::size_t observation_count = 0;

  double confidence_weighted_signal = 0.0;
  double mean_confidence = 0.0;
};

class LearningMemory {
 public:
  void add(LearningObservation observation);

  [[nodiscard]] LearningSummary summarize(
      Attribute attribute,
      MemoryScope scope,
      std::optional<std::string_view> context =
          std::nullopt) const;

  [[nodiscard]] const LearningObservation*
  find(std::string_view id) const;

  bool erase(std::string_view id);

  void clear();

  [[nodiscard]] std::size_t size() const;

 private:
  std::vector<LearningObservation> observations_;
};

}  // namespace routing::core::intelligence
