#include <cassert>
#include <iostream>
#include <stdexcept>
#include <string>

#include "routing/adapters/valhalla/detail/valhalla_route_request.hpp"
#include "routing/core/candidates/candidate_family_plan.hpp"

namespace {

bool contains(
    const std::string& text,
    const std::string& needle) {
  return text.find(needle) !=
      std::string::npos;
}

routing::core::RouteRequest base_request() {
  routing::core::RouteRequest request;

  request.origin = {
      47.1410,
      9.5209,
  };

  request.destination = {
      47.2410,
      9.5310,
  };

  request.costing_profile =
      "auto";

  return request;
}

}  // namespace

int main() {
  using namespace routing::core;
  using namespace routing::core::candidates;
  using routing::adapters::valhalla::detail::
      build_route_request;

  {
    const auto plan =
        candidate_family_plan(
            CandidateFamily::Fastest);

    const auto request =
        make_candidate_request(
            base_request(),
            plan);

    const auto json =
        build_route_request(
            request);

    assert(
        !contains(
            json,
            "\"shortest\""));

    assert(
        !contains(
            json,
            "\"use_highways\""));

    assert(
        !contains(
            json,
            "\"alternates\""));
  }

  {
    const auto plan =
        candidate_family_plan(
            CandidateFamily::Shortest);

    const auto request =
        make_candidate_request(
            base_request(),
            plan);

    const auto json =
        build_route_request(
            request);

    assert(
        contains(
            json,
            "\"shortest\":true"));
  }

  {
    const auto plan =
        candidate_family_plan(
            CandidateFamily::MajorRoads);

    const auto request =
        make_candidate_request(
            base_request(),
            plan);

    const auto json =
        build_route_request(
            request);

    assert(
        contains(
            json,
            "\"alternates\":3"));

    assert(
        contains(
            json,
            "\"use_highways\":0.9"));

    // We intentionally do NOT claim that this backend
    // option is FederalRoad preference.
    assert(
        !contains(
            json,
            "FederalRoad"));
  }

  {
    const auto plan =
        candidate_family_plan(
            CandidateFamily::Comfort);

    const auto request =
        make_candidate_request(
            base_request(),
            plan);

    const auto json =
        build_route_request(
            request);

    assert(
        contains(
            json,
            "\"alternates\":3"));

    assert(
        contains(
            json,
            "\"use_tracks\":0"));

    assert(
        contains(
            json,
            "\"use_living_streets\":0"));

    assert(
        contains(
            json,
            "\"service_factor\":3"));

    assert(
        contains(
            json,
            "\"maneuver_penalty\":25"));
  }

  {
    const auto plan =
        candidate_family_plan(
            CandidateFamily::LowUrban);

    const auto request =
        make_candidate_request(
            base_request(),
            plan);

    const auto json =
        build_route_request(
            request);

    // Pool generation + post-evaluation.
    assert(
        contains(
            json,
            "\"alternates\":3"));

    // Never invent an unsupported Valhalla "urban" option.
    assert(
        !contains(
            json,
            "use_urban"));
  }

  {
    auto request =
        base_request();

    request.family =
        CandidateFamily::Scenic;

    bool rejected = false;

    try {
      (void)build_route_request(
          request);
    } catch (
        const std::invalid_argument&) {
      rejected = true;
    }

    // Deferred must fail explicitly instead of silently
    // pretending Scenic == Fastest.
    assert(rejected);
  }

  std::cout
      << "Valhalla candidate costing tests passed\n";

  return 0;
}
