#include "routing/core/intelligence/intelligence_job_queue.hpp"

#include <stdexcept>
#include <utility>

namespace routing::core::intelligence {

namespace {

bool is_claimable(
    const IntelligenceJobState state) {
  return
      state == IntelligenceJobState::Pending ||
      state == IntelligenceJobState::Deferred;
}

}  // namespace

void IntelligenceJobQueue::enqueue(
    IntelligenceJob job) {
  if (job.id.empty()) {
    throw std::invalid_argument(
        "Intelligence job id must not be empty.");
  }

  if (job.priority > 100) {
    throw std::invalid_argument(
        "Intelligence job priority must be between 0 and 100.");
  }

  if (job.minimum_battery_percent > 100) {
    throw std::invalid_argument(
        "Minimum battery percentage must be between 0 and 100.");
  }

  if (find(job.id) != nullptr) {
    throw std::invalid_argument(
        "Duplicate intelligence job id: " +
        job.id);
  }

  job.state =
      IntelligenceJobState::Pending;

  Entry entry;
  entry.job = std::move(job);
  entry.sequence = next_sequence_++;

  entries_.push_back(std::move(entry));
}

std::optional<IntelligenceJob>
IntelligenceJobQueue::claim_next(
    const ResourceSnapshot& resources) {
  Entry* best = nullptr;

  for (auto& entry : entries_) {
    if (!is_claimable(entry.job.state)) {
      continue;
    }

    WorkRequest request;
    request.workload = entry.job.workload;
    request.requires_network =
        entry.job.requires_network;
    request.requires_charging =
        entry.job.requires_charging;
    request.minimum_battery_percent =
        entry.job.minimum_battery_percent;

    if (evaluate_resource_request(
            request,
            resources) ==
        ExecutionDecision::Defer) {
      entry.job.state =
          IntelligenceJobState::Deferred;
      continue;
    }

    if (best == nullptr) {
      best = &entry;
      continue;
    }

    const bool entry_critical =
        entry.job.workload ==
        WorkloadClass::NavigationCritical;

    const bool best_critical =
        best->job.workload ==
        WorkloadClass::NavigationCritical;

    bool entry_wins = false;

    if (entry_critical != best_critical) {
      entry_wins = entry_critical;
    } else if (entry.job.priority !=
               best->job.priority) {
      entry_wins =
          entry.job.priority >
          best->job.priority;
    } else {
      entry_wins =
          entry.sequence <
          best->sequence;
    }

    if (entry_wins) {
      best = &entry;
    }
  }

  if (best == nullptr) {
    return std::nullopt;
  }

  best->job.state =
      IntelligenceJobState::Running;

  ++best->job.attempts;

  return best->job;
}

void IntelligenceJobQueue::mark_completed(
    const std::string_view id) {
  transition(
      id,
      IntelligenceJobState::Completed);
}

void IntelligenceJobQueue::mark_failed(
    const std::string_view id) {
  transition(
      id,
      IntelligenceJobState::Failed);
}

void IntelligenceJobQueue::mark_deferred(
    const std::string_view id) {
  transition(
      id,
      IntelligenceJobState::Deferred);
}

const IntelligenceJob*
IntelligenceJobQueue::find(
    const std::string_view id) const {
  for (const auto& entry : entries_) {
    if (entry.job.id == id) {
      return &entry.job;
    }
  }

  return nullptr;
}

std::size_t IntelligenceJobQueue::size() const {
  return entries_.size();
}

IntelligenceJobQueue::Entry*
IntelligenceJobQueue::find_mutable(
    const std::string_view id) {
  for (auto& entry : entries_) {
    if (entry.job.id == id) {
      return &entry;
    }
  }

  return nullptr;
}

void IntelligenceJobQueue::transition(
    const std::string_view id,
    const IntelligenceJobState target) {
  auto* entry =
      find_mutable(id);

  if (entry == nullptr) {
    throw std::invalid_argument(
        "Unknown intelligence job id.");
  }

  if (entry->job.state !=
      IntelligenceJobState::Running) {
    throw std::logic_error(
        "Only a running intelligence job may transition.");
  }

  entry->job.state = target;
}

}  // namespace routing::core::intelligence
