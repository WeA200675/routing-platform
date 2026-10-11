#include "routing/core/intelligence/resource_governor.hpp"

namespace routing::core::intelligence {

ExecutionDecision evaluate_resource_request(
    const WorkRequest& request,
    const ResourceSnapshot& resources) {
  // Externe Voraussetzungen bleiben echte Voraussetzungen.
  if (request.requires_network &&
      !resources.network_available) {
    return ExecutionDecision::Defer;
  }

  if (request.requires_charging &&
      !resources.device_charging) {
    return ExecutionDecision::Defer;
  }

  // Lokale Navigation hat höchste Priorität.
  if (request.workload ==
      WorkloadClass::NavigationCritical) {
    return ExecutionDecision::RunNow;
  }

  if (resources.battery_percent <
      request.minimum_battery_percent) {
    return ExecutionDecision::Defer;
  }

  if (resources.thermal_state ==
      ThermalState::Critical) {
    return ExecutionDecision::Defer;
  }

  switch (request.workload) {
    case WorkloadClass::NavigationCritical:
      return ExecutionDecision::RunNow;

    case WorkloadClass::LiveLightweight:
      if (resources.thermal_state ==
          ThermalState::Hot) {
        return ExecutionDecision::Defer;
      }

      return ExecutionDecision::RunNow;

    case WorkloadClass::PostDrive:
      if (resources.navigation_active ||
          resources.thermal_state ==
              ThermalState::Hot) {
        return ExecutionDecision::Defer;
      }

      return ExecutionDecision::RunNow;

    case WorkloadClass::Background:
      if (resources.navigation_active ||
          !resources.device_charging ||
          resources.thermal_state !=
              ThermalState::Nominal ||
          resources.battery_percent < 30) {
        return ExecutionDecision::Defer;
      }

      return ExecutionDecision::RunNow;

    case WorkloadClass::DeepAi:
      if (resources.navigation_active ||
          !resources.device_charging ||
          resources.thermal_state !=
              ThermalState::Nominal ||
          resources.battery_percent < 50) {
        return ExecutionDecision::Defer;
      }

      return ExecutionDecision::RunNow;
  }

  return ExecutionDecision::Defer;
}

}  // namespace routing::core::intelligence
