#pragma once

#include <memory>
#include <string>

#include "routing/core/routing_engine.hpp"

namespace routing::adapters::valhalla {

struct ValhallaConfig {
  std::string config_json;
};

class ValhallaRoutingEngine final : public routing::core::IRoutingEngine {
 public:
  explicit ValhallaRoutingEngine(ValhallaConfig config);
  ~ValhallaRoutingEngine() override;

  ValhallaRoutingEngine(const ValhallaRoutingEngine&) = delete;
  ValhallaRoutingEngine& operator=(const ValhallaRoutingEngine&) = delete;
  ValhallaRoutingEngine(ValhallaRoutingEngine&&) noexcept;
  ValhallaRoutingEngine& operator=(ValhallaRoutingEngine&&) noexcept;

  [[nodiscard]] std::string name() const override;
  [[nodiscard]] std::string version() const override;
  [[nodiscard]] bool ready() const override;
  [[nodiscard]] routing::core::RoutingResult route(
      const routing::core::RouteRequest& request) const override;

 private:
  class Impl;
  std::unique_ptr<Impl> impl_;
};

}  // namespace routing::adapters::valhalla
