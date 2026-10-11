#include <cassert>
#include <cmath>
#include <iostream>
#include <vector>

#include "routing/core/candidates/family_representative.hpp"

namespace {

routing::core::evaluation::RouteEvaluation
make_evaluation(
    const char* route_id,
    const double cost) {
  routing::core::evaluation::
      RouteEvaluation evaluation;

  evaluation.route_id =
      route_id;

  evaluation.segment_data_available =
      true;

  evaluation.score_available =
      true;

  evaluation.allowed =
      true;

  evaluation.total_seconds_equivalent =
      cost;

  evaluation.reported_duration_s =
      cost;

  evaluation.reported_distance_m =
      1000.0;

  evaluation.analysis
      .analyzed_distance_m =
          1000.0;

  return evaluation;
}

}  // namespace

int main() {
  using namespace routing::core;
  using namespace routing::core::candidates;

  // ------------------------------------------------------------
  // Unknown != zero:
  //
  // unknown-city-data has 0 measured urban metres,
  // but all 1000 m are unknown. It must NOT beat a route
  // with known urban data.
  // ------------------------------------------------------------

  auto unknown_city =
      make_evaluation(
          "unknown-city-data",
          100.0);

  unknown_city.analysis
      .urban_distance_m = 0.0;

  unknown_city.analysis
      .unknown_urban_distance_m =
          1000.0;

  auto known_city =
      make_evaluation(
          "known-city-data",
          120.0);

  known_city.analysis
      .urban_distance_m = 200.0;

  known_city.analysis
      .unknown_urban_distance_m = 0.0;

  const auto low_urban_plan =
      candidate_family_plan(
          CandidateFamily::LowUrban);

  const auto urban_decision =
      select_family_representative(
          low_urban_plan,
          {
              unknown_city,
              known_city,
          });

  assert(
      urban_decision.status ==
      FamilyRepresentativeStatus::
          Selected);

  assert(
      urban_decision
          .selected_index.has_value());

  assert(
      *urban_decision.selected_index ==
      1);

  assert(
      urban_decision.selected_route_id ==
      "known-city-data");

  assert(
      !urban_decision
           .evidence[0].eligible);

  assert(
      urban_decision
          .evidence[1].eligible);

  // ------------------------------------------------------------
  // Major roads:
  // known coverage threshold must be respected.
  // ------------------------------------------------------------

  auto complete_major =
      make_evaluation(
          "complete-major",
          140.0);

  complete_major.analysis
      .major_road_distance_m =
          800.0;

  complete_major.analysis
      .unknown_road_class_distance_m =
          0.0;

  auto incomplete_major =
      make_evaluation(
          "incomplete-major",
          110.0);

  incomplete_major.analysis
      .major_road_distance_m =
          500.0;

  incomplete_major.analysis
      .unknown_road_class_distance_m =
          400.0;

  const auto major_plan =
      candidate_family_plan(
          CandidateFamily::MajorRoads);

  const auto major_decision =
      select_family_representative(
          major_plan,
          {
              complete_major,
              incomplete_major,
          });

  assert(
      major_decision.status ==
      FamilyRepresentativeStatus::
          Selected);

  assert(
      *major_decision.selected_index ==
      0);

  // ------------------------------------------------------------
  // Profile optimal:
  // must select existing CostEngine total, not invent a
  // second family score.
  // ------------------------------------------------------------

  auto expensive =
      make_evaluation(
          "expensive",
          300.0);

  auto cheap =
      make_evaluation(
          "cheap",
          225.0);

  const auto profile_plan =
      candidate_family_plan(
          CandidateFamily::
              ProfileOptimal);

  const auto profile_decision =
      select_family_representative(
          profile_plan,
          {
              expensive,
              cheap,
          });

  assert(
      profile_decision.status ==
      FamilyRepresentativeStatus::
          Selected);

  assert(
      *profile_decision
           .selected_index == 1);

  assert(
      profile_decision.reason_key ==
      "family.lowest_core_cost");

  std::cout
      << "Family representative tests passed\n";

  return 0;
}
