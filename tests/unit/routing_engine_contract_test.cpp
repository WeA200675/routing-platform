#include <cassert>
#include <string>
#include <utility>
#include <vector>

#include "routing/core/routing_engine.hpp"

namespace {

class FakeRoutingEngine final : public routing::core::IRoutingEngine {
 public:
  [[nodiscard]] std::string name() const override { return "fake"; }
  [[nodiscard]] std::string version() const override { return "0.2-test"; }
  [[nodiscard]] bool ready() const override { return true; }

  [[nodiscard]] routing::core::RoutingResult route(
      const routing::core::RouteRequest& request) const override {
    routing::core::RoutingResult result;
    result.success = true;

    routing::core::RoutePath path;
    path.route_id = "fake-1";
    path.family = request.family;
    path.distance_m = 18'000.0;
    path.duration_s = 17.0 * 60.0;
    path.engine_name = name();
    path.engine_version = version();
    path.geometry = {request.origin, request.destination};
    path.segment_ids = {"B-road-a", "B-road-b"};
    result.routes.push_back(std::move(path));
    return result;
  }
};

}  // namespace

int main() {
  FakeRoutingEngine engine;
  assert(engine.ready());
  assert(engine.name() == "fake");

  routing::core::RouteRequest request;
  request.origin = {48.7758, 9.1829};
  request.destination = {49.0069, 8.4037};
  request.family = routing::core::CandidateFamily::MajorRoads;
  request.alternatives = 2;

  const auto result = engine.route(request);
  assert(result.success);
  assert(result.error_code.empty());
  assert(result.routes.size() == 1);
  assert(result.routes.front().family == routing::core::CandidateFamily::MajorRoads);
  assert(result.routes.front().engine_name == "fake");
  assert(result.routes.front().segment_ids.size() == 2);
  return 0;
}
