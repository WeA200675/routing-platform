#include "routing/core/intelligence/learning_memory.hpp"

#include <algorithm>
#include <stdexcept>
#include <utility>

namespace routing::core::intelligence {
namespace {

void validate_confidence(
    const double confidence,
    const std::string_view label) {
  if (confidence < 0.0 ||
      confidence > 1.0) {
    throw std::invalid_argument(
        std::string(label) +
        " confidence must be between 0 and 1.");
  }
}

void validate_context(
    const MemoryScope scope,
    const std::string& context_key,
    const std::string_view label) {
  if (scope == MemoryScope::Contextual &&
      context_key.empty()) {
    throw std::invalid_argument(
        std::string(label) +
        " requires a context key.");
  }
}

}  // namespace

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

  validate_confidence(
      observation.confidence,
      "Learning observation");

  validate_context(
      observation.scope,
      observation.context_key,
      "Contextual learning observation");

  if (find(observation.id) != nullptr ||
      find_knowledge(observation.id) != nullptr) {
    throw std::invalid_argument(
        "Duplicate learning memory id: " +
        observation.id);
  }

  observations_.push_back(
      std::move(observation));
}

void LearningMemory::add_knowledge(
    LearnedKnowledge knowledge) {
  if (knowledge.id.empty()) {
    throw std::invalid_argument(
        "Learned knowledge id must not be empty.");
  }

  if (knowledge.key.empty()) {
    throw std::invalid_argument(
        "Learned knowledge key must not be empty.");
  }

  if (knowledge.value.empty()) {
    throw std::invalid_argument(
        "Learned knowledge value must not be empty.");
  }

  if (knowledge.source.empty()) {
    throw std::invalid_argument(
        "Learned knowledge source must not be empty.");
  }

  validate_confidence(
      knowledge.confidence,
      "Learned knowledge");

  validate_context(
      knowledge.scope,
      knowledge.context_key,
      "Contextual learned knowledge");

  if (find(knowledge.id) != nullptr ||
      find_knowledge(knowledge.id) != nullptr) {
    throw std::invalid_argument(
        "Duplicate learning memory id: " +
        knowledge.id);
  }

  knowledge_.push_back(
      std::move(knowledge));
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

const LearnedKnowledge*
LearningMemory::find_knowledge(
    const std::string_view id) const {
  const auto found =
      std::find_if(
          knowledge_.begin(),
          knowledge_.end(),
          [id](
              const LearnedKnowledge& knowledge) {
            return knowledge.id == id;
          });

  return found == knowledge_.end()
      ? nullptr
      : &*found;
}

std::vector<const LearnedKnowledge*>
LearningMemory::find_knowledge_by_key(
    const LearnedKnowledgeKind kind,
    const std::string_view key,
    const MemoryScope scope,
    const std::optional<std::string_view> context) const {
  std::vector<const LearnedKnowledge*> result;

  for (const auto& knowledge :
       knowledge_) {
    if (knowledge.kind != kind ||
        std::string_view(
            knowledge.key) != key ||
        knowledge.scope != scope) {
      continue;
    }

    if (scope == MemoryScope::Contextual) {
      if (!context.has_value() ||
          std::string_view(
              knowledge.context_key) !=
              *context) {
        continue;
      }
    }

    result.push_back(
        &knowledge);
  }

  return result;
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

bool LearningMemory::erase_knowledge(
    const std::string_view id) {
  const auto found =
      std::find_if(
          knowledge_.begin(),
          knowledge_.end(),
          [id](
              const LearnedKnowledge& knowledge) {
            return knowledge.id == id;
          });

  if (found == knowledge_.end()) {
    return false;
  }

  knowledge_.erase(found);
  return true;
}

void LearningMemory::clear() {
  observations_.clear();
  knowledge_.clear();
}

std::size_t LearningMemory::size() const {
  return observations_.size();
}

std::size_t LearningMemory::knowledge_size() const {
  return knowledge_.size();
}

}  // namespace routing::core::intelligence
