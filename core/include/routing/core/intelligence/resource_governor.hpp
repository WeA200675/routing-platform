#pragma once

#include <cstdint>

namespace routing::core::intelligence {

enum class WorkloadClass : std::uint8_t {
  NavigationCritical = 0,
  LiveLightweight,
  PostDrive,
  Background,
  DeepAi,
};

enum class ThermalState : std::uint8_t {
  Nominal = 0,
  Warm,
  Hot,
  Critical,
};

enum class ExecutionDecision : std::uint8_t {
  RunNow = 0,
  Defer,
};

struct ResourceSnapshot {
  bool navigation_active = false;
  bool device_charging = false;
  bool network_available = false;

  std::uint8_t battery_percent = 100;

  ThermalState thermal_state =
      ThermalState::Nominal;
};

struct WorkRequest {
  WorkloadClass workload =
      WorkloadClass::PostDrive;

  bool requires_network = false;
  bool requires_charging = false;

  std::uint8_t minimum_battery_percent = 0;
};

[[nodiscard]] ExecutionDecision
evaluate_resource_request(
    const WorkRequest& request,
    const ResourceSnapshot& resources);

}  // namespace routing::core::intelligence
