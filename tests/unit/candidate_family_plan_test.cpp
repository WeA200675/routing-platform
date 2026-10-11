#include <cassert>
#include <iostream>
#include <set>
#include <string>

#include "routing/core/candidates/candidate_family_plan.hpp"

int main() {
  using namespace routing::core;
  using namespace routing::core::candidates;

  const auto plans =
      all_candidate_family_plans();

  assert(plans.size() == 12);

  std::set<std::string> keys;

  std::size_t implemented_count = 0;
  std::size_t deferred_count = 0;

  for (const auto& plan :
       plans) {
    const std::string key(
        candidate_family_key(
            plan.family));

    assert(!key.empty());

    assert(
        keys.insert(key).second);

    if (plan.implemented) {
      ++implemented_count;
    }

    if (plan.generation_mode ==
        CandidateGenerationMode::
            Deferred) {
      ++deferred_count;
      assert(!plan.implemented);
    }
  }

  assert(implemented_count == 8);
  assert(deferred_count == 4);

  const auto fastest =
      candidate_family_plan(
          CandidateFamily::Fastest);

  assert(
      fastest.generation_mode ==
      CandidateGenerationMode::Direct);

  assert(
      fastest.alternatives_requested == 0);

  const auto shortest =
      candidate_family_plan(
          CandidateFamily::Shortest);

  assert(
      has_generation_bias(
          shortest,
          CandidateGenerationBias::
              DistanceFirst));

  const auto profile =
      candidate_family_plan(
          CandidateFamily::ProfileOptimal);

  assert(
      profile.generation_mode ==
      CandidateGenerationMode::
          DiversePoolPostEvaluate);

  assert(
      profile.alternatives_requested == 3);

  assert(
      profile.representative_metric ==
      FamilyRepresentativeMetric::
          CoreCost);

  const auto major =
      candidate_family_plan(
          CandidateFamily::MajorRoads);

  assert(
      has_generation_bias(
          major,
          CandidateGenerationBias::
              PreferHighHierarchy));

  assert(
      major.representative_metric ==
      FamilyRepresentativeMetric::
          MajorRoadShare);

  // Critical semantic guard:
  // backend highway bias is NOT our complete
  // MajorRoads meaning.
  assert(
      !major
           .backend_bias_semantically_complete);

  const auto comfort =
      candidate_family_plan(
          CandidateFamily::Comfort);

  assert(
      has_generation_bias(
          comfort,
          CandidateGenerationBias::
              AvoidTracks));

  assert(
      has_generation_bias(
          comfort,
          CandidateGenerationBias::
              AvoidLivingStreets));

  assert(
      has_generation_bias(
          comfort,
          CandidateGenerationBias::
              AvoidServiceRoads));

  assert(
      has_generation_bias(
          comfort,
          CandidateGenerationBias::
              ReduceManeuvers));

  const auto low_urban =
      candidate_family_plan(
          CandidateFamily::LowUrban);

  assert(
      low_urban.representative_metric ==
      FamilyRepresentativeMetric::
          UrbanShare);

  const auto scenic =
      candidate_family_plan(
          CandidateFamily::Scenic);

  assert(!scenic.implemented);

  RouteRequest base;

  base.origin = {
      47.1410,
      9.5209,
  };

  base.destination = {
      47.2410,
      9.5310,
  };

  const auto request =
      make_candidate_request(
          base,
          major);

  assert(
      request.family ==
      CandidateFamily::MajorRoads);

  assert(
      request.alternatives == 3);

  bool deferred_rejected = false;

  try {
    (void)make_candidate_request(
        base,
        scenic);
  } catch (
      const std::invalid_argument&) {
    deferred_rejected = true;
  }

  assert(deferred_rejected);

  std::cout
      << "Candidate family plan tests passed\n";

  return 0;
}
