#include <cassert>

#include "routing/core/intelligence/intelligence_job_queue.hpp"

int main() {
  using namespace routing::core::intelligence;

  IntelligenceJobQueue queue;

  IntelligenceJob local;
  local.id = "local-learning";

  local.type =
      IntelligenceJobType::UpdatePreference;

  local.workload =
      WorkloadClass::PostDrive;

  local.priority = 80;

  queue.enqueue(local);

  IntelligenceJob cloud;
  cloud.id = "optional-cloud-analysis";

  cloud.type =
      IntelligenceJobType::DeepAnalysis;

  cloud.workload =
      WorkloadClass::DeepAi;

  cloud.priority = 100;
  cloud.requires_network = true;
  cloud.requires_charging = true;

  queue.enqueue(cloud);

  IntelligenceJob critical;
  critical.id = "navigation-critical";

  critical.type =
      IntelligenceJobType::ClassifyDeviation;

  critical.workload =
      WorkloadClass::NavigationCritical;

  // Trotz niedriger Zahl muss NavigationCritical gewinnen.
  critical.priority = 1;

  queue.enqueue(critical);

  ResourceSnapshot resources;
  resources.navigation_active = false;
  resources.device_charging = false;
  resources.network_available = false;
  resources.battery_percent = 70;

  resources.thermal_state =
      ThermalState::Nominal;

  const auto first =
      queue.claim_next(resources);

  assert(first.has_value());
  assert(first->id == "navigation-critical");

  assert(
      first->state ==
      IntelligenceJobState::Running);

  queue.mark_completed(first->id);

  const auto second =
      queue.claim_next(resources);

  // Lokales Offline-Lernen läuft weiterhin.
  assert(second.has_value());
  assert(second->id == "local-learning");

  queue.mark_completed(second->id);

  const auto none =
      queue.claim_next(resources);

  assert(!none.has_value());

  const auto* cloud_state =
      queue.find(
          "optional-cloud-analysis");

  assert(cloud_state != nullptr);

  assert(
      cloud_state->state ==
      IntelligenceJobState::Deferred);

  resources.network_available = true;
  resources.device_charging = true;
  resources.battery_percent = 90;

  const auto third =
      queue.claim_next(resources);

  assert(third.has_value());

  assert(
      third->id ==
      "optional-cloud-analysis");

  queue.mark_completed(third->id);

  assert(queue.size() == 3);

  return 0;
}
