#include <cassert>

#include "routing/adapters/valhalla/valhalla_routing_engine.hpp"

int main() {
  routing::adapters::valhalla::ValhallaRoutingEngine engine({"{}"});
  assert(engine.name() == "valhalla");
  assert(!engine.ready());

  routing::core::RouteRequest request;
  request.origin = {48.7758, 9.1829};
  request.destination = {49.0069, 8.4037};

  const auto result = engine.route(request);
  assert(!result.success);
  assert(result.routes.empty());
  assert(result.error_code == "VALHALLA_NOT_LINKED");
  return 0;
}
