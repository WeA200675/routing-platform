#include <cassert>
#include <stdexcept>

#include "routing/core/intelligence/intelligence_job_queue.hpp"

int main() {
  using namespace routing::core::intelligence;

  IntelligenceJobQueue queue;

  IntelligenceJob local;

  local.id =
      "local-learning";

  local.type =
      IntelligenceJobType::UpdatePreference;

  local.workload =
      WorkloadClass::PostDrive;

  local.priority =
      80;

  queue.enqueue(
      local);

  IntelligenceJob cloud;

  cloud.id =
      "optional-cloud-analysis";

  cloud.type =
      IntelligenceJobType::DeepAnalysis;

  cloud.workload =
      WorkloadClass::DeepAi;

  cloud.priority =
      100;

  cloud.requires_network =
      true;

  cloud.requires_charging =
      true;

  queue.enqueue(
      cloud);

  IntelligenceJob critical;

  critical.id =
      "navigation-critical";

  critical.type =
      IntelligenceJobType::ClassifyDeviation;

  critical.workload =
      WorkloadClass::NavigationCritical;

  // NavigationCritical must still win despite low numeric priority.
  critical.priority =
      1;

  queue.enqueue(
      critical);

  ResourceSnapshot resources;

  resources.navigation_active =
      false;

  resources.device_charging =
      false;

  resources.network_available =
      false;

  resources.battery_percent =
      70;

  resources.thermal_state =
      ThermalState::Nominal;

  const auto first =
      queue.claim_next(
          resources);

  assert(first.has_value());

  assert(
      first->id ==
      "navigation-critical");

  assert(
      first->state ==
      IntelligenceJobState::Running);

  queue.mark_completed(
      first->id);

  const auto second =
      queue.claim_next(
          resources);

  // Local offline learning still runs.
  assert(second.has_value());

  assert(
      second->id ==
      "local-learning");

  queue.mark_completed(
      second->id);

  const auto none =
      queue.claim_next(
          resources);

  assert(!none.has_value());

  const auto* cloud_state =
      queue.find(
          "optional-cloud-analysis");

  assert(
      cloud_state != nullptr);

  assert(
      cloud_state->state ==
      IntelligenceJobState::Deferred);

  resources.network_available =
      true;

  resources.device_charging =
      true;

  resources.battery_percent =
      90;

  const auto third =
      queue.claim_next(
          resources);

  assert(third.has_value());

  assert(
      third->id ==
      "optional-cloud-analysis");

  queue.mark_completed(
      third->id);

  assert(queue.size() == 3);


  // -------------------------------------------------------------
  // Strict enqueue remains strict.
  // -------------------------------------------------------------

  bool duplicate_rejected =
      false;

  try {
    queue.enqueue(
        critical);
  } catch (const std::invalid_argument&) {
    duplicate_rejected =
        true;
  }

  assert(
      duplicate_rejected);


  // -------------------------------------------------------------
  // Stable-ID producer path may coalesce Pending/Deferred work.
  // -------------------------------------------------------------

  IntelligenceJob cluster;

  cluster.id =
      "cluster-problem:test";

  cluster.type =
      IntelligenceJobType::ClusterProblem;

  cluster.workload =
      WorkloadClass::PostDrive;

  cluster.priority =
      50;

  cluster.minimum_battery_percent =
      25;

  cluster.subject_key =
      "diagnostic-cluster:test";

  cluster.context_key =
      "corridor:test";

  cluster.data_scope_key =
      "local-only";

  cluster.reason_key =
      "reason:first";

  cluster.evidence_revision =
      2;

  const auto added =
      queue.enqueue_or_coalesce(
          cluster);

  assert(
      added.status ==
      IntelligenceJobEnqueueStatus::Added);

  assert(queue.size() == 4);

  IntelligenceJob stronger =
      cluster;

  stronger.priority =
      75;

  stronger.reason_key =
      "reason:stronger";

  stronger.evidence_revision =
      5;

  const auto coalesced =
      queue.enqueue_or_coalesce(
          stronger);

  assert(
      coalesced.status ==
      IntelligenceJobEnqueueStatus::Coalesced);

  assert(queue.size() == 4);

  const auto* stored =
      queue.find(
          cluster.id);

  assert(stored != nullptr);

  assert(
      stored->priority == 75);

  assert(
      stored->evidence_revision == 5);

  assert(
      stored->reason_key ==
      "reason:stronger");


  // Same stable ID must never alias another semantic subject.
  IntelligenceJob collision =
      stronger;

  collision.context_key =
      "different-context";

  bool collision_rejected =
      false;

  try {
    (void)queue.enqueue_or_coalesce(
        collision);
  } catch (const std::invalid_argument&) {
    collision_rejected =
        true;
  }

  assert(
      collision_rejected);

  return 0;
}
