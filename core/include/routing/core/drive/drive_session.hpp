#pragma once

#include <cstddef>
#include <cstdint>
#include <limits>
#include <optional>
#include <string>
#include <vector>

#include "routing/core/drive/replay_semantics.hpp"

namespace routing::core::drive {

inline constexpr std::uint32_t
kDriveSessionLegacySchemaVersion = 1;

inline constexpr std::uint32_t
kDriveSessionReplaySemanticsVersion = 2;

inline constexpr std::uint32_t
kDriveSessionSchemaVersion =
    kDriveSessionReplaySemanticsVersion;

enum class DriveSessionPurpose : std::uint8_t {
  Normal = 0,
  Tester,
  Replay,
};

enum class LearningDisposition : std::uint8_t {
  Eligible = 0,

  // Fahrt bleibt für Diagnose/Regression verfügbar,
  // darf aber nicht in persönliche Präferenzen einfließen.
  RecordOnly,

  // Vollständig vom persönlichen Lernen ausgeschlossen.
  Excluded,
};

struct VersionSnapshot {
  std::string routing_engine;
  std::string routing_engine_version;

  std::string map_data_version;

  std::string profile_id;
  std::string profile_version;

  std::string rules_version;
  std::string intelligence_policy_version;
};

struct GeoPointSnapshot {
  // NaN bedeutet ausdr?cklich: nicht gesetzt.
  // (0,0) ist eine reale geografische Position und darf
  // deshalb niemals als Default-/Missing-Wert dienen.
  double latitude =
      std::numeric_limits<double>::quiet_NaN();

  double longitude =
      std::numeric_limits<double>::quiet_NaN();
};

struct RouteRequestSnapshot {
  GeoPointSnapshot origin;
  GeoPointSnapshot destination;

  std::vector<GeoPointSnapshot> via_points;

  // Stabiler textueller Identifier statt persistierter Enum-Zahl.
  std::string candidate_family;

  std::size_t alternatives_requested = 0;

  std::optional<std::string> costing_profile;
};

struct RouteSnapshot {
  std::string route_id;

  // Ebenfalls stabil als Text gespeichert.
  std::string candidate_family;

  double distance_m = 0.0;
  double duration_s = 0.0;

  // Reihenfolge entspricht der Fahrtrichtung.
  std::vector<std::string> segment_ids;
};

struct DriveSessionHeader {
  std::uint32_t schema_version =
      kDriveSessionSchemaVersion;

  std::string session_id;

  std::int64_t started_at_ms = 0;

  DriveSessionPurpose purpose =
      DriveSessionPurpose::Normal;

  LearningDisposition learning_disposition =
      LearningDisposition::Eligible;

  std::string learning_exclusion_reason;

  VersionSnapshot versions;

  // Später z.B.:
  // vehicle:trailer
  // weather:snow
  // trip:family
  // mode:time-pressure
  std::vector<std::string> context_tags;
};

enum class FeedbackSentiment : std::uint8_t {
  Neutral = 0,
  Positive,
  Negative,
};

enum class FeedbackReason : std::uint8_t {
  Unspecified = 0,
  RouteWasGood,
  PreferredAlternative,
  ResidentialShortcut,
  Speed30Zone,
  TooManyTurns,
  CurvyRoad,
  SteepRoad,
  NarrowRoad,
  PoorRoadQuality,
  Traffic,
  UnsafeFeeling,
  Other,
};

struct FeedbackMark {
  FeedbackSentiment sentiment =
      FeedbackSentiment::Neutral;

  FeedbackReason reason =
      FeedbackReason::Unspecified;

  // 1..5
  std::uint8_t severity = 3;

  std::string note;
};

enum class DriveEventType : std::uint8_t {
  LocationObservation = 0,
  RouteDeviationDetected,
  RouteDeviationEnded,
  RerouteRequested,
  RerouteApplied,
  AlternativeSelected,
  FeedbackMarked,
};

struct DriveEvent {
  // Wird vom Recorder deterministisch vergeben.
  std::string id;
  std::uint64_t sequence = 0;

  std::int64_t timestamp_ms = 0;

  DriveEventType type =
      DriveEventType::LocationObservation;

  std::string route_id;
  std::string segment_id;

  std::string alternative_route_id;

  std::optional<double> deviation_distance_m;

  // Confidence des Detektors, nicht einer
  // persönlichen Präferenzinterpretation.
  std::optional<double> detector_confidence;

  std::optional<FeedbackMark> feedback;
};

struct DriveSession {
  DriveSessionHeader header;

  // Optional for legacy/imported sessions.
  // Presence means the executable route semantics were captured.
  std::optional<ReplaySemanticsSnapshot>
      replay_semantics;

  RouteRequestSnapshot request;

  RouteSnapshot selected_route;
  std::vector<RouteSnapshot> alternatives;

  std::vector<DriveEvent> events;

  std::optional<std::int64_t> ended_at_ms;

  bool completed = false;
};

class DriveSessionRecorder {
 public:
  DriveSessionRecorder(
      DriveSessionHeader header,
      RouteRequestSnapshot request,
      RouteSnapshot selected_route,
      std::vector<RouteSnapshot> alternatives = {},
      std::optional<ReplaySemanticsSnapshot>
          replay_semantics = std::nullopt);

  [[nodiscard]] std::string record(
      DriveEvent event);

  void finish(std::int64_t ended_at_ms);

  [[nodiscard]] const DriveSession&
  session() const;

 private:
  DriveSession session_;
  std::uint64_t next_sequence_ = 1;
};

}  // namespace routing::core::drive
