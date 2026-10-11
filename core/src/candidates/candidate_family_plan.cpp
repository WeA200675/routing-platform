#include "routing/core/candidates/candidate_family_plan.hpp"

#include <algorithm>
#include <stdexcept>

namespace routing::core::candidates {

std::string_view candidate_family_key(
    const CandidateFamily family) {
  switch (family) {
    case CandidateFamily::Fastest:
      return "fastest";

    case CandidateFamily::Shortest:
      return "shortest";

    case CandidateFamily::ProfileOptimal:
      return "profile_optimal";

    case CandidateFamily::MajorRoads:
      return "major_roads";

    case CandidateFamily::Comfort:
      return "comfort";

    case CandidateFamily::LowUrban:
      return "low_urban";

    case CandidateFamily::LowCurvature:
      return "low_curvature";

    case CandidateFamily::LowGradient:
      return "low_gradient";

    case CandidateFamily::LowTraffic:
      return "low_traffic";

    case CandidateFamily::Energy:
      return "energy";

    case CandidateFamily::Scenic:
      return "scenic";

    case CandidateFamily::Stable:
      return "stable";
  }

  throw std::invalid_argument(
      "Unknown candidate family.");
}

CandidateFamilyPlan candidate_family_plan(
    const CandidateFamily family) {
  CandidateFamilyPlan plan;

  plan.family = family;

  switch (family) {
    case CandidateFamily::Fastest:
      plan.generation_mode =
          CandidateGenerationMode::Direct;

      plan.alternatives_requested = 0;

      plan.representative_metric =
          FamilyRepresentativeMetric::FirstRoute;

      plan.minimum_known_coverage = 0.0;

      plan.backend_bias_semantically_complete =
          true;

      plan.rationale_key =
          "candidate.fastest";

      return plan;

    case CandidateFamily::Shortest:
      plan.generation_mode =
          CandidateGenerationMode::Direct;

      plan.alternatives_requested = 0;

      plan.generation_biases = {
          CandidateGenerationBias::DistanceFirst,
      };

      plan.representative_metric =
          FamilyRepresentativeMetric::FirstRoute;

      plan.minimum_known_coverage = 0.0;

      plan.backend_bias_semantically_complete =
          true;

      plan.rationale_key =
          "candidate.shortest";

      return plan;

    case CandidateFamily::ProfileOptimal:
      plan.generation_mode =
          CandidateGenerationMode::
              DiversePoolPostEvaluate;

      plan.alternatives_requested = 3;

      plan.representative_metric =
          FamilyRepresentativeMetric::CoreCost;

      plan.minimum_known_coverage = 0.0;

      plan.backend_bias_semantically_complete =
          false;

      plan.rationale_key =
          "candidate.profile_optimal";

      return plan;

    case CandidateFamily::MajorRoads:
      plan.generation_mode =
          CandidateGenerationMode::
              DiversePoolPostEvaluate;

      plan.alternatives_requested = 3;

      plan.generation_biases = {
          CandidateGenerationBias::
              PreferHighHierarchy,
      };

      plan.representative_metric =
          FamilyRepresentativeMetric::
              MajorRoadShare;

      plan.minimum_known_coverage = 0.80;

      // A backend highway preference is only a search hint.
      // It is not equivalent to our major-road semantics and
      // especially not equivalent to FederalRoad.
      plan.backend_bias_semantically_complete =
          false;

      plan.rationale_key =
          "candidate.major_roads";

      return plan;

    case CandidateFamily::Comfort:
      plan.generation_mode =
          CandidateGenerationMode::
              DiversePoolPostEvaluate;

      plan.alternatives_requested = 3;

      plan.generation_biases = {
          CandidateGenerationBias::AvoidTracks,
          CandidateGenerationBias::
              AvoidLivingStreets,
          CandidateGenerationBias::
              AvoidServiceRoads,
          CandidateGenerationBias::
              ReduceManeuvers,
      };

      // Final choice inside the generated comfort pool still
      // uses the semantic CostEngine.
      plan.representative_metric =
          FamilyRepresentativeMetric::CoreCost;

      plan.minimum_known_coverage = 0.0;

      plan.backend_bias_semantically_complete =
          false;

      plan.rationale_key =
          "candidate.comfort";

      return plan;

    case CandidateFamily::LowUrban:
      plan.generation_mode =
          CandidateGenerationMode::
              DiversePoolPostEvaluate;

      plan.alternatives_requested = 3;

      plan.representative_metric =
          FamilyRepresentativeMetric::UrbanShare;

      plan.minimum_known_coverage = 0.80;

      plan.backend_bias_semantically_complete =
          false;

      plan.rationale_key =
          "candidate.low_urban";

      return plan;

    case CandidateFamily::LowCurvature:
      plan.generation_mode =
          CandidateGenerationMode::
              DiversePoolPostEvaluate;

      plan.alternatives_requested = 3;

      plan.representative_metric =
          FamilyRepresentativeMetric::CurvyShare;

      plan.minimum_known_coverage = 0.80;

      plan.backend_bias_semantically_complete =
          false;

      plan.rationale_key =
          "candidate.low_curvature";

      return plan;

    case CandidateFamily::LowGradient:
      plan.generation_mode =
          CandidateGenerationMode::
              DiversePoolPostEvaluate;

      plan.alternatives_requested = 3;

      plan.representative_metric =
          FamilyRepresentativeMetric::
              SteepGradientShare;

      plan.minimum_known_coverage = 0.80;

      plan.backend_bias_semantically_complete =
          false;

      plan.rationale_key =
          "candidate.low_gradient";

      return plan;

    case CandidateFamily::LowTraffic:
      plan.generation_mode =
          CandidateGenerationMode::Deferred;

      plan.implemented = false;

      plan.rationale_key =
          "candidate.low_traffic.deferred";

      return plan;

    case CandidateFamily::Energy:
      plan.generation_mode =
          CandidateGenerationMode::Deferred;

      plan.implemented = false;

      plan.rationale_key =
          "candidate.energy.deferred";

      return plan;

    case CandidateFamily::Scenic:
      plan.generation_mode =
          CandidateGenerationMode::Deferred;

      plan.implemented = false;

      plan.rationale_key =
          "candidate.scenic.deferred";

      return plan;

    case CandidateFamily::Stable:
      plan.generation_mode =
          CandidateGenerationMode::Deferred;

      plan.implemented = false;

      plan.rationale_key =
          "candidate.stable.deferred";

      return plan;
  }

  throw std::invalid_argument(
      "Unknown candidate family.");
}

std::vector<CandidateFamilyPlan>
all_candidate_family_plans() {
  return {
      candidate_family_plan(
          CandidateFamily::Fastest),

      candidate_family_plan(
          CandidateFamily::Shortest),

      candidate_family_plan(
          CandidateFamily::ProfileOptimal),

      candidate_family_plan(
          CandidateFamily::MajorRoads),

      candidate_family_plan(
          CandidateFamily::Comfort),

      candidate_family_plan(
          CandidateFamily::LowUrban),

      candidate_family_plan(
          CandidateFamily::LowCurvature),

      candidate_family_plan(
          CandidateFamily::LowGradient),

      candidate_family_plan(
          CandidateFamily::LowTraffic),

      candidate_family_plan(
          CandidateFamily::Energy),

      candidate_family_plan(
          CandidateFamily::Scenic),

      candidate_family_plan(
          CandidateFamily::Stable),
  };
}

bool has_generation_bias(
    const CandidateFamilyPlan& plan,
    const CandidateGenerationBias bias) {
  return std::find(
             plan.generation_biases.begin(),
             plan.generation_biases.end(),
             bias) !=
      plan.generation_biases.end();
}

RouteRequest make_candidate_request(
    const RouteRequest& base_request,
    const CandidateFamilyPlan& plan) {
  if (!plan.implemented ||
      plan.generation_mode ==
          CandidateGenerationMode::Deferred) {
    throw std::invalid_argument(
        "Candidate family is not implemented: " +
        std::string(
            candidate_family_key(
                plan.family)));
  }

  RouteRequest request =
      base_request;

  request.family =
      plan.family;

  request.alternatives =
      std::max(
          base_request.alternatives,
          plan.alternatives_requested);

  return request;
}

}  // namespace routing::core::candidates
