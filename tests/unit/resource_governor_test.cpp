#include <cassert>

#include "routing/core/intelligence/resource_governor.hpp"

int main() {
  using namespace routing::core::intelligence;

  ResourceSnapshot stressed;
  stressed.navigation_active = true;
  stressed.device_charging = false;
  stressed.network_available = false;
  stressed.battery_percent = 3;

  stressed.thermal_state =
      ThermalState::Critical;

  WorkRequest navigation;
  navigation.workload =
      WorkloadClass::NavigationCritical;

  assert(
      evaluate_resource_request(
          navigation,
          stressed) ==
      ExecutionDecision::RunNow);

  ResourceSnapshot driving;
  driving.navigation_active = true;
  driving.battery_percent = 70;

  driving.thermal_state =
      ThermalState::Nominal;

  WorkRequest live;
  live.workload =
      WorkloadClass::LiveLightweight;

  assert(
      evaluate_resource_request(
          live,
          driving) ==
      ExecutionDecision::RunNow);

  WorkRequest post_drive;
  post_drive.workload =
      WorkloadClass::PostDrive;

  assert(
      evaluate_resource_request(
          post_drive,
          driving) ==
      ExecutionDecision::Defer);

  ResourceSnapshot offline_after_drive;
  offline_after_drive.navigation_active = false;
  offline_after_drive.network_available = false;
  offline_after_drive.battery_percent = 70;

  offline_after_drive.thermal_state =
      ThermalState::Nominal;

  // Lokales Lernen benötigt kein Internet.
  assert(
      evaluate_resource_request(
          post_drive,
          offline_after_drive) ==
      ExecutionDecision::RunNow);

  WorkRequest online_only =
      post_drive;

  online_only.requires_network = true;

  assert(
      evaluate_resource_request(
          online_only,
          offline_after_drive) ==
      ExecutionDecision::Defer);

  WorkRequest background;
  background.workload =
      WorkloadClass::Background;

  assert(
      evaluate_resource_request(
          background,
          offline_after_drive) ==
      ExecutionDecision::Defer);

  ResourceSnapshot charging =
      offline_after_drive;

  charging.device_charging = true;
  charging.battery_percent = 80;

  assert(
      evaluate_resource_request(
          background,
          charging) ==
      ExecutionDecision::RunNow);

  WorkRequest deep_ai;
  deep_ai.workload =
      WorkloadClass::DeepAi;

  charging.thermal_state =
      ThermalState::Warm;

  assert(
      evaluate_resource_request(
          deep_ai,
          charging) ==
      ExecutionDecision::Defer);

  charging.thermal_state =
      ThermalState::Nominal;

  assert(
      evaluate_resource_request(
          deep_ai,
          charging) ==
      ExecutionDecision::RunNow);

  return 0;
}
