#include "routing/core/intelligence/learning_memory.hpp"

#include <algorithm>
#include <stdexcept>
#include <utility>

namespace routing::core::intelligence {

void LearningMemory::add(
    LearningObservation observation) {
  if (observation.id.empty()) {
    throw std::invalid_argument(
        "Learning observation id must not be empty.");
  }

  if (observation.preference_signal < -1.0 ||
      observation.preference_signal > 1.0) {
    throw std::invalid_argument(
        "Preference signal must be between -1 and 1.");
  }

  if (observation.confidence < 0.0 ||
      observation.confidence > 1.0) {
    throw std::invalid_argument(
        "Learning confidence must be between 0 and 1.");
  }

  if (observation.scope ==
          MemoryScope::Contextual &&
      observation.context_key.empty()) {
    throw std::invalid_argument(
        "Contextual learning observation requires a context key.");
  }

  if (find(observation.id) != nullptr) {
    throw std::invalid_argument(
        "Duplicate learning observation id: " +
        observation.id);
  }

  observations_.push_back(
      std::move(observation));
}

LearningSummary LearningMemory::summarize(
    const Attribute attribute,
    const MemoryScope scope,
    const std::optional<std::string_view> context) const {
  LearningSummary result;

  double weighted_signal_sum = 0.0;
  double confidence_sum = 0.0;

  for (const auto& observation :
       observations_) {
    if (observation.attribute != attribute ||
        observation.scope != scope) {
      continue;
    }

    if (scope == MemoryScope::Contextual) {
      if (!context.has_value() ||
          std::string_view(
              observation.context_key) !=
              *context) {
        continue;
      }
    }

    ++result.observation_count;

    weighted_signal_sum +=
        observation.preference_signal *
        observation.confidence;

    confidence_sum +=
        observation.confidence;
  }

  if (result.observation_count == 0) {
    return result;
  }

  result.mean_confidence =
      confidence_sum /
      static_cast<double>(
          result.observation_count);

  if (confidence_sum > 0.0) {
    result.confidence_weighted_signal =
        weighted_signal_sum /
        confidence_sum;
  }

  return result;
}

const LearningObservation*
LearningMemory::find(
    const std::string_view id) const {
  const auto found =
      std::find_if(
          observations_.begin(),
          observations_.end(),
          [id](
              const LearningObservation& observation) {
            return observation.id == id;
          });

  return found == observations_.end()
      ? nullptr
      : &*found;
}

bool LearningMemory::erase(
    const std::string_view id) {
  const auto found =
      std::find_if(
          observations_.begin(),
          observations_.end(),
          [id](
              const LearningObservation& observation) {
            return observation.id == id;
          });

  if (found == observations_.end()) {
    return false;
  }

  observations_.erase(found);
  return true;
}

void LearningMemory::clear() {
  observations_.clear();
}

std::size_t LearningMemory::size() const {
  return observations_.size();
}

}  // namespace routing::core::intelligence
