#include <cstdlib>
#include <fstream>
#include <iostream>
#include <sstream>
#include <string>

#include "routing/adapters/valhalla/valhalla_routing_engine.hpp"
#include "routing/core/drive/drive_evidence.hpp"
#include "routing/core/drive/regression_candidate.hpp"
#include "routing/core/drive/routing_snapshot.hpp"

namespace {

int fail(const std::string& message) {
  std::cerr << "FAIL: " << message << '\n';
  return 1;
}

}  // namespace

int main() {
  using namespace routing::core;
  using namespace routing::core::drive;

  const char* config_path =
      std::getenv(
          "ROUTING_PLATFORM_VALHALLA_TEST_CONFIG");

  if (config_path == nullptr ||
      std::string(config_path).empty()) {
    std::cout
        << "SKIP: ROUTING_PLATFORM_VALHALLA_TEST_CONFIG is not set.\n";

    return 77;
  }

  std::ifstream config_file(config_path);

  if (!config_file) {
    return fail(
        std::string(
            "Could not open Valhalla config: ") +
        config_path);
  }

  std::ostringstream config_buffer;
  config_buffer << config_file.rdbuf();

  routing::adapters::valhalla::
      ValhallaRoutingEngine engine(
          {config_buffer.str()});

  if (!engine.ready()) {
    return fail(
        "ValhallaRoutingEngine is not ready.");
  }

  RouteRequest request;

  request.origin = {
      47.1410,
      9.5209,
  };

  request.destination = {
      47.1660,
      9.5100,
  };

  request.family =
      CandidateFamily::ProfileOptimal;

  request.costing_profile = "auto";

  const auto result =
      engine.route(request);

  if (!result.success ||
      result.routes.size() != 1) {
    return fail(
        "Expected one successful real route.");
  }

  const auto& route =
      result.routes.front();

  if (route.segments.empty()) {
    return fail(
        "Real route produced no street segments.");
  }

  const auto request_snapshot =
      make_route_request_snapshot(request);

  const auto selected_snapshot =
      make_route_snapshot(route);

  if (selected_snapshot.segment_ids !=
      route.segment_ids) {
    return fail(
        "RouteSnapshot did not preserve segment order.");
  }

  DriveSessionHeader header;
  header.session_id =
      "liechtenstein-real-drive";

  header.started_at_ms = 1000;

  header.purpose =
      DriveSessionPurpose::Tester;

  header.learning_disposition =
      LearningDisposition::RecordOnly;

  header.versions.routing_engine =
      route.engine_name;

  header.versions.routing_engine_version =
      route.engine_version;

  header.versions.map_data_version =
      "liechtenstein-fixture";

  header.versions.profile_id =
      "auto";

  header.versions.profile_version =
      "fixture-v1";

  header.versions.rules_version =
      "rules-test-v1";

  header.versions.intelligence_policy_version =
      "intelligence-test-v1";

  DriveSessionRecorder recorder(
      header,
      request_snapshot,
      selected_snapshot);

  DriveEvent feedback;
  feedback.timestamp_ms = 2000;

  feedback.type =
      DriveEventType::FeedbackMarked;

  feedback.route_id =
      selected_snapshot.route_id;

  feedback.segment_id =
      selected_snapshot.segment_ids.front();

  FeedbackMark mark;

  mark.sentiment =
      FeedbackSentiment::Negative;

  mark.reason =
      FeedbackReason::ResidentialShortcut;

  mark.severity = 5;

  mark.note =
      "integration-test-feedback";

  feedback.feedback = mark;

  (void)recorder.record(feedback);
  recorder.finish(3000);

  const auto& session =
      recorder.session();

  if (may_feed_personal_learning(session)) {
    return fail(
        "RecordOnly tester drive must not feed personal learning.");
  }

  const auto evidence =
      build_drive_evidence(session);

  if (evidence.size() != 1 ||
      evidence.front().kind !=
          EvidenceKind::ExplicitFeedback ||
      evidence.front().polarity !=
          EvidencePolarity::Negative) {
    return fail(
        "Expected one negative explicit feedback evidence record.");
  }

  const auto candidates =
      derive_regression_candidates(
          session,
          evidence);

  if (candidates.size() != 1) {
    return fail(
        "Expected exactly one regression candidate.");
  }

  const auto& candidate =
      candidates.front();

  if (!candidate.requires_human_review) {
    return fail(
        "Regression candidate unexpectedly bypasses human review.");
  }

  if (candidate.request.candidate_family !=
          "profile_optimal" ||
      !candidate.request.costing_profile.has_value() ||
      *candidate.request.costing_profile !=
          "auto") {
    return fail(
        "Regression candidate lost routing request snapshot.");
  }

  if (candidate.selected_route.segment_ids !=
      route.segment_ids) {
    return fail(
        "Regression candidate lost route segment ordering.");
  }

  if (candidate.versions.routing_engine !=
          "valhalla" ||
      candidate.versions.routing_engine_version !=
          route.engine_version) {
    return fail(
        "Regression candidate lost routing engine provenance.");
  }

  std::cout
      << "PASS: Valhalla -> DriveSession -> Evidence -> RegressionCandidate\n"
      << "  route:      "
      << selected_snapshot.route_id
      << '\n'
      << "  segments:   "
      << selected_snapshot.segment_ids.size()
      << '\n'
      << "  evidence:   "
      << evidence.size()
      << '\n'
      << "  candidates: "
      << candidates.size()
      << '\n';

  return 0;
}
