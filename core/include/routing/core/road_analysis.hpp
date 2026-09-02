#pragma once

#include <cstdint>

#include "routing/core/model.hpp"

namespace routing::core {

enum class MatchState : std::uint8_t {
  Unknown = 0,
  No,
  Yes,
};

[[nodiscard]] bool is_major_road(
    FunctionalRoadClass road_class);

[[nodiscard]] bool is_minor_road(
    FunctionalRoadClass road_class);

[[nodiscard]] MatchState classify_speed_30_or_lower(
    const StreetSegment& segment);

[[nodiscard]] MatchState classify_strongly_curvy(
    const StreetSegment& segment,
    double threshold = 0.70);

[[nodiscard]] MatchState classify_serpentine(
    const StreetSegment& segment,
    double threshold = 0.60);

[[nodiscard]] MatchState classify_urban(
    const StreetSegment& segment,
    double threshold = 0.60);

[[nodiscard]] bool is_speed_30_or_lower(
    const StreetSegment& segment);

[[nodiscard]] bool is_strongly_curvy(
    const StreetSegment& segment,
    double threshold = 0.70);

[[nodiscard]] bool is_serpentine(
    const StreetSegment& segment,
    double threshold = 0.60);

[[nodiscard]] bool is_urban(
    const StreetSegment& segment,
    double threshold = 0.60);

}  // namespace routing::core