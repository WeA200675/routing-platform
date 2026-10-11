#include <cassert>
#include <iostream>
#include <stdexcept>
#include <string>
#include <vector>

#include "routing/core/testing/regression_report.hpp"

namespace {

routing::core::RoutePath make_route(
    const routing::core::RouteRequest& request) {
  using namespace routing::core;

  RoutePath route;

  route.route_id =
      "fake-route";

  route.family =
      request.family;

  route.distance_m =
      1000.0;

  route.duration_s =
      60.0;

  route.geometry = {
      request.origin,
      request.destination,
  };

  StreetSegment segment;

  segment.id =
      "fake-segment";

  segment.length_m =
      1000.0;

  segment.functional_road_class =
      FunctionalRoadClass::Primary;

  segment.road_network_class =
      RoadNetworkClass::FederalRoad;

  segment.speed_limit_kmh = 60.0;
  segment.practical_speed_kmh = 60.0;

  segment.curvature_score = 0.10;
  segment.serpentine_score = 0.10;
  segment.gradient_abs_pct = 1.0;
  segment.urban_score = 0.10;
  segment.data_confidence = 1.0;

  route.segments.push_back(
      segment);

  route.segment_ids.push_back(
      segment.id);

  route.engine_name = "fake";
  route.engine_version = "1";

  return route;
}

class CountingEngine final
    : public routing::core::IRoutingEngine {
 public:
  mutable std::size_t route_calls = 0;

  [[nodiscard]]
  std::string name() const override {
    return "fake";
  }

  [[nodiscard]]
  std::string version() const override {
    return "1";
  }

  [[nodiscard]]
  bool ready() const override {
    return true;
  }

  [[nodiscard]]
  routing::core::RoutingResult route(
      const routing::core::RouteRequest& request) const override {
    ++route_calls;

    routing::core::RoutingResult result;

    result.success = true;

    result.routes.push_back(
        make_route(
            request));

    return result;
  }
};

routing::core::testing::RoutingRegressionCase
make_case(
    const std::string& id,
    const routing::core::testing::
        RegressionDisposition disposition,
    const double maximum_distance_m) {
  using namespace routing::core::testing;

  RoutingRegressionCase regression_case;

  regression_case.case_id = id;
  regression_case.title = id;

  regression_case.disposition =
      disposition;

  regression_case.scenario.id =
      id;

  regression_case.scenario.request.origin = {
      47.1410,
      9.5209,
  };

  regression_case.scenario.request.destination = {
      47.2410,
      9.5310,
  };

  regression_case.scenario
      .family_policy
      .include_fastest_reference =
          false;

  regression_case.scenario
      .expectations
      .maximum_selected_distance_m =
          maximum_distance_m;

  return regression_case;
}

}  // namespace

int main() {
  using namespace routing::core::testing;

  {
    CountingEngine engine;

    std::vector<RoutingRegressionCase> cases = {
        make_case(
            "test:gating-pass",
            RegressionDisposition::Gating,
            1500.0),

        make_case(
            "test:observe-fail",
            RegressionDisposition::ObserveOnly,
            500.0),

        make_case(
            "test:disabled",
            RegressionDisposition::Disabled,
            1.0),
    };

    const auto result =
        run_regression_suite(
            engine,
            cases);

    assert(result.passed);

    assert(
        result.total_case_count ==
        3);

    assert(
        result.executed_case_count ==
        2);

    assert(
        result.skipped_case_count ==
        1);

    assert(
        result.gating_case_count ==
        1);

    assert(
        result.gating_failure_count ==
        0);

    assert(
        result.observe_case_count ==
        1);

    assert(
        result.observe_failure_count ==
        1);

    assert(
        engine.route_calls ==
        2);

    const auto report =
        format_regression_suite_report(
            result);

    assert(
        report.find(
            "[OBSERVE-FAIL] test:observe-fail") !=
        std::string::npos);

    assert(
        report.find(
            "suite result: PASS") !=
        std::string::npos);
  }

  {
    CountingEngine engine;

    const auto result =
        run_regression_suite(
            engine,
            {
                make_case(
                    "test:gating-fail",
                    RegressionDisposition::Gating,
                    500.0),
            });

    assert(!result.passed);

    assert(
        result.gating_failure_count ==
        1);
  }

  {
    auto first =
        make_case(
            "test:duplicate",
            RegressionDisposition::Gating,
            1500.0);

    auto second = first;

    bool rejected = false;

    try {
      validate_regression_suite({
          first,
          second,
      });
    } catch (const std::invalid_argument&) {
      rejected = true;
    }

    assert(rejected);
  }

  std::cout
      << "Regression suite tests passed\n";

  return 0;
}
