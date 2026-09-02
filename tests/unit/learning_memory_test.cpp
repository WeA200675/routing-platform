#include <cassert>
#include <cmath>
#include <stdexcept>

#include "routing/core/intelligence/learning_memory.hpp"

namespace {

bool nearly_equal(
    const double a,
    const double b,
    const double epsilon = 1e-9) {
  return std::abs(a - b) <= epsilon;
}

}  // namespace

int main() {
  using namespace routing::core;
  using namespace routing::core::intelligence;

  LearningMemory memory;

  LearningObservation first;
  first.id = "drive-1";

  first.attribute =
      Attribute::SpeedLimitKmh;

  first.preference_signal = 0.8;
  first.confidence = 1.0;

  first.scope =
      MemoryScope::LongTerm;

  memory.add(first);

  LearningObservation second;
  second.id = "drive-2";

  second.attribute =
      Attribute::SpeedLimitKmh;

  second.preference_signal = 0.2;
  second.confidence = 0.5;

  second.scope =
      MemoryScope::LongTerm;

  memory.add(second);

  const auto long_term =
      memory.summarize(
          Attribute::SpeedLimitKmh,
          MemoryScope::LongTerm);

  assert(
      long_term.observation_count == 2);

  assert(nearly_equal(
      long_term.confidence_weighted_signal,
      0.6));

  assert(nearly_equal(
      long_term.mean_confidence,
      0.75));

  LearningObservation trailer;
  trailer.id = "drive-trailer";

  trailer.attribute =
      Attribute::UrbanScore;

  trailer.preference_signal = -0.9;
  trailer.confidence = 0.95;

  trailer.scope =
      MemoryScope::Contextual;

  trailer.context_key =
      "vehicle:trailer";

  memory.add(trailer);

  const auto trailer_summary =
      memory.summarize(
          Attribute::UrbanScore,
          MemoryScope::Contextual,
          "vehicle:trailer");

  assert(
      trailer_summary.observation_count == 1);

  assert(nearly_equal(
      trailer_summary.confidence_weighted_signal,
      -0.9));

  const auto wrong_context =
      memory.summarize(
          Attribute::UrbanScore,
          MemoryScope::Contextual,
          "vehicle:solo");

  assert(
      wrong_context.observation_count == 0);

  bool duplicate_threw = false;

  try {
    memory.add(first);
  } catch (const std::invalid_argument&) {
    duplicate_threw = true;
  }

  assert(duplicate_threw);

  bool invalid_signal_threw = false;

  try {
    LearningObservation invalid;
    invalid.id = "invalid";
    invalid.preference_signal = 1.5;

    memory.add(invalid);
  } catch (const std::invalid_argument&) {
    invalid_signal_threw = true;
  }

  assert(invalid_signal_threw);

  assert(memory.erase("drive-1"));
  assert(!memory.erase("does-not-exist"));

  assert(memory.size() == 2);

  memory.clear();

  assert(memory.size() == 0);

  return 0;
}
