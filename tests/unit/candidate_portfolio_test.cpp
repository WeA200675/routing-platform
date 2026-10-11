#include <cassert>
#include <iostream>

#include "routing/core/candidates/candidate_portfolio.hpp"

namespace {

routing::core::evaluation::RouteEvaluation
make_evaluation(
    const char* route_id,
    const double cost,
    const double urban_m = 0.0) {
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

  evaluation.reported_distance_m =
      1000.0;

  evaluation.reported_duration_s =
      cost;

  evaluation.total_seconds_equivalent =
      cost;

  evaluation.analysis
      .analyzed_distance_m =
          1000.0;

  evaluation.analysis
      .unknown_urban_distance_m =
          0.0;

  evaluation.analysis
      .urban_distance_m =
          urban_m;

  return evaluation;
}

}  // namespace

int main() {
  using namespace routing::core;
  using namespace routing::core::candidates;

  FamilyEvaluationPool low_urban;

  low_urban.plan =
      candidate_family_plan(
          CandidateFamily::LowUrban);

  // This one is cheaper, but is worse for the LowUrban
  // family's explicit representative objective.
  low_urban.evaluations.push_back(
      make_evaluation(
          "urban-cheap",
          210.0,
          700.0));

  // Family representative should be this route.
  low_urban.evaluations.push_back(
      make_evaluation(
          "urban-low",
          250.0,
          50.0));

  FamilyEvaluationPool fastest;

  fastest.plan =
      candidate_family_plan(
          CandidateFamily::Fastest);

  fastest.evaluations.push_back(
      make_evaluation(
          "fast-route",
          230.0,
          400.0));

  FamilyEvaluationPool profile;

  profile.plan =
      candidate_family_plan(
          CandidateFamily::
              ProfileOptimal);

  profile.evaluations.push_back(
      make_evaluation(
          "profile-route",
          240.0,
          100.0));

  const auto portfolio =
      build_candidate_portfolio({
          low_urban,
          fastest,
          profile,
      });

  assert(
      portfolio.entries.size() == 3);

  assert(
      portfolio
          .selected_entry_index
          .has_value());

  const auto& winner =
      portfolio.entries[
          *portfolio
               .selected_entry_index];

  // Final selection is existing CostEngine total:
  // 230 beats LowUrban representative 250 and
  // ProfileOptimal representative 240.
  assert(
      winner.family ==
      CandidateFamily::Fastest);

  assert(
      winner.evaluation.route_id ==
      "fast-route");

  assert(
      winner.evaluation
          .total_seconds_equivalent ==
      230.0);

  assert(
      portfolio.reason_key ==
      "portfolio.lowest_core_cost");

  // Backend IDs may repeat across family requests.
  // candidate_key therefore includes the family.
  assert(
      winner.candidate_key ==
      "fastest:fast-route");

  std::cout
      << "Candidate portfolio tests passed\n";

  return 0;
}
