#pragma once

#include <cstddef>
#include <exception>
#include <stdexcept>
#include <string>
#include <utility>
#include <vector>

#include "routing/core/routing_engine.hpp"

namespace routing::core {

struct RouteEnrichmentSummary {
  std::size_t complete_route_count = 0;
  std::size_t unavailable_route_count = 0;
};

// Execute optional Street Model enrichment independently for every route.
//
// The routing result itself may already contain valuable geometry,
// maneuvers, duration and distance. Therefore failure of optional semantic
// enrichment on route N must never destroy successful sibling routes.
//
// Enricher signature:
//
//   std::vector<StreetSegment>(
//       const RoutePath& route)
//
// Missing enrichment stays missing. It is never converted to empty/zero
// semantic attributes and is therefore not silently considered favourable.
template <typename Enricher>
RouteEnrichmentSummary
enrich_route_segments_independently(
    std::vector<RoutePath>& routes,
    Enricher&& enricher) {
  RouteEnrichmentSummary summary;

  for (auto& route : routes) {
    try {
      auto segments =
          enricher(route);

      if (segments.empty()) {
        throw std::runtime_error(
            "Route segment enrichment returned no segments.");
      }

      std::vector<std::string> segment_ids;
      segment_ids.reserve(
          segments.size());

      for (const auto& segment :
           segments) {
        if (segment.id.empty()) {
          throw std::runtime_error(
              "Route segment enrichment returned an empty segment id.");
        }

        segment_ids.push_back(
            segment.id);
      }

      // Commit only after the complete route enrichment has succeeded.
      // This prevents a partially mapped route from looking complete.
      route.segments =
          std::move(segments);

      route.segment_ids =
          std::move(segment_ids);

      route.segment_data_status =
          RouteSegmentDataStatus::Complete;

      ++summary.complete_route_count;

    } catch (const std::exception& error) {
      route.segments.clear();
      route.segment_ids.clear();

      route.segment_data_status =
          RouteSegmentDataStatus::Unavailable;

      route.diagnostics.push_back({
          "ROUTE_SEGMENT_ENRICHMENT_FAILED",
          error.what(),
      });

      ++summary.unavailable_route_count;

    } catch (...) {
      route.segments.clear();
      route.segment_ids.clear();

      route.segment_data_status =
          RouteSegmentDataStatus::Unavailable;

      route.diagnostics.push_back({
          "ROUTE_SEGMENT_ENRICHMENT_FAILED",
          "Unknown exception during route segment enrichment.",
      });

      ++summary.unavailable_route_count;
    }
  }

  return summary;
}

}  // namespace routing::core
