#pragma once

#include <string_view>

namespace routing::core {
inline constexpr std::string_view kPlatformVersion = "0.2.0";
inline constexpr std::string_view kFoundationVersion = kPlatformVersion;
inline constexpr std::string_view kStreetModelVersion = "1.0-draft";
inline constexpr std::string_view kRuleEngineVersion = "1.0-draft";
inline constexpr std::string_view kCostEngineVersion = "1.0-draft";
}  // namespace routing::core
