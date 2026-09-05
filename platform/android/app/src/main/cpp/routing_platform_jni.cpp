#include <jni.h>

#include <bit>
#include <cstdint>
#include <exception>
#include <limits>
#include <memory>
#include <mutex>
#include <optional>
#include <stdexcept>
#include <string>
#include <utility>
#include <vector>

#include "routing/core/navigation/navigation_runtime.hpp"

namespace {

using routing::core::CandidateFamily;
using routing::core::ManeuverType;
using routing::core::RouteDiagnostic;
using routing::core::RouteManeuver;
using routing::core::RoutePath;
using routing::core::RouteSegmentDataStatus;
using routing::core::navigation::NavigationProgressUpdate;
using routing::core::navigation::NavigationSession;
using routing::core::navigation::NavigationSessionState;
using routing::core::navigation::NavigationSnapshot;


RoutePath
make_fixture_route() {
  RoutePath route;

  route.route_id =
      "route:navigation:android-jni:1";

  route.family =
      CandidateFamily::ProfileOptimal;

  route.distance_m =
      3000.0;

  route.duration_s =
      600.0;

  route.geometry = {
      {47.1400, 9.5200},
      {47.1450, 9.5200},
      {47.1500, 9.5200},
      {47.1550, 9.5200},
  };


  RouteManeuver start;

  start.type =
      ManeuverType::Start;

  start.instruction =
      "Start";

  start.begin_shape_index =
      0;

  start.end_shape_index =
      0;


  RouteManeuver continue_maneuver;

  continue_maneuver.type =
      ManeuverType::Continue;

  continue_maneuver.instruction =
      "Continue straight";

  continue_maneuver.distance_m =
      1900.0;

  continue_maneuver.duration_s =
      380.0;

  continue_maneuver.begin_shape_index =
      0;

  continue_maneuver.end_shape_index =
      2;


  RouteManeuver turn;

  turn.type =
      ManeuverType::TurnRight;

  turn.instruction =
      "Turn right";

  turn.distance_m =
      1100.0;

  turn.duration_s =
      220.0;

  turn.begin_shape_index =
      2;

  turn.end_shape_index =
      3;


  RouteManeuver arrive;

  arrive.type =
      ManeuverType::Arrive;

  arrive.instruction =
      "Arrive";

  arrive.begin_shape_index =
      3;

  arrive.end_shape_index =
      3;


  route.maneuvers = {
      start,
      continue_maneuver,
      turn,
      arrive,
  };

  route.engine_name =
      "jni-fixture";

  route.engine_version =
      "1.0";

  route.segment_data_status =
      RouteSegmentDataStatus::Complete;

  return route;
}


constexpr std::uint32_t
kNativeRouteMagic =
    0x4E525431U;

constexpr std::size_t
kMaximumNativeRoutePayloadBytes =
    16U * 1024U * 1024U;

constexpr std::uint32_t
kMaximumNativeRouteStringBytes =
    1'000'000U;

constexpr std::uint32_t
kMaximumGeometryPointCount =
    1'000'000U;

constexpr std::uint32_t
kMaximumManeuverCount =
    100'000U;

constexpr std::uint32_t
kMaximumStreetNamesPerManeuver =
    1'000U;

constexpr std::uint32_t
kMaximumDiagnosticCount =
    10'000U;


class NativeRouteByteReader {
 public:
  explicit NativeRouteByteReader(
      const std::vector<jbyte>& bytes)
      : bytes_(bytes) {}


  [[nodiscard]]
  bool
  at_end() const noexcept {
    return
        offset_ ==
        bytes_.size();
  }


  [[nodiscard]]
  std::uint8_t
  read_byte() {
    require_available(
        1U);

    const auto value =
        static_cast<std::uint8_t>(
            bytes_[offset_]);

    ++offset_;

    return value;
  }


  [[nodiscard]]
  std::uint32_t
  read_u32() {
    std::uint32_t result =
        0U;

    for (int i = 0;
         i < 4;
         ++i) {
      result =
          (result << 8U) |
          static_cast<std::uint32_t>(
              read_byte());
    }

    return result;
  }


  [[nodiscard]]
  std::int32_t
  read_i32() {
    return
        std::bit_cast<std::int32_t>(
            read_u32());
  }


  [[nodiscard]]
  std::uint64_t
  read_u64() {
    std::uint64_t result =
        0U;

    for (int i = 0;
         i < 8;
         ++i) {
      result =
          (result << 8U) |
          static_cast<std::uint64_t>(
              read_byte());
    }

    return result;
  }


  [[nodiscard]]
  double
  read_double() {
    return
        std::bit_cast<double>(
            read_u64());
  }


  [[nodiscard]]
  bool
  read_bool() {
    const auto value =
        read_byte();

    if (value > 1U) {
      throw std::invalid_argument(
          "Native route payload contains invalid boolean.");
    }

    return
        value ==
        1U;
  }


  [[nodiscard]]
  std::string
  read_string() {
    const auto length =
        read_u32();

    if (length >
        kMaximumNativeRouteStringBytes) {
      throw std::invalid_argument(
          "Native route string exceeds maximum size.");
    }

    require_available(
        static_cast<std::size_t>(
            length));

    const auto* start =
        reinterpret_cast<const char*>(
            bytes_.data() +
            offset_);

    std::string result(
        start,
        static_cast<std::size_t>(
            length));

    offset_ +=
        static_cast<std::size_t>(
            length);

    return result;
  }


  [[nodiscard]]
  std::size_t
  read_count(
      const std::uint32_t maximum,
      const char* field_name) {

    const auto value =
        read_u32();

    if (value > maximum) {
      throw std::invalid_argument(
          std::string(
              "Native route count exceeds limit: ") +
          field_name);
    }

    return
        static_cast<std::size_t>(
            value);
  }


 private:
  void
  require_available(
      const std::size_t count) const {

    if (count >
        bytes_.size() -
            offset_) {
      throw std::invalid_argument(
          "Native route payload is truncated.");
    }
  }


  const std::vector<jbyte>&
      bytes_;

  std::size_t offset_ =
      0U;
};


[[nodiscard]]
std::optional<std::uint16_t>
read_optional_bearing(
    NativeRouteByteReader& reader) {

  if (!reader.read_bool()) {
    return std::nullopt;
  }

  const auto value =
      reader.read_i32();

  if (value < 0 ||
      value > 359) {
    throw std::invalid_argument(
        "Native route bearing must be in [0, 359].");
  }

  return
      static_cast<std::uint16_t>(
          value);
}


[[nodiscard]]
std::optional<std::int32_t>
read_optional_engine_type(
    NativeRouteByteReader& reader) {

  if (!reader.read_bool()) {
    return std::nullopt;
  }

  return
      reader.read_i32();
}


[[nodiscard]]
RoutePath
decode_native_route_payload(
    const std::vector<jbyte>& payload) {

  NativeRouteByteReader reader(
      payload);


  if (reader.read_u32() !=
      kNativeRouteMagic) {
    throw std::invalid_argument(
        "Native route payload magic mismatch.");
  }


  const auto schema_version =
      reader.read_i32();

  if (schema_version != 1) {
    throw std::invalid_argument(
        "Unsupported native route payload schema.");
  }


  RoutePath route;

  route.route_id =
      reader.read_string();


  const auto family =
      reader.read_i32();

  if (family <
          static_cast<std::int32_t>(
              CandidateFamily::Fastest) ||
      family >
          static_cast<std::int32_t>(
              CandidateFamily::Stable)) {
    throw std::invalid_argument(
        "Native route contains invalid candidate family.");
  }

  route.family =
      static_cast<CandidateFamily>(
          family);


  route.distance_m =
      reader.read_double();

  route.duration_s =
      reader.read_double();


  const auto geometry_count =
      reader.read_count(
          kMaximumGeometryPointCount,
          "geometry");

  if (geometry_count < 2U) {
    throw std::invalid_argument(
        "Native route requires at least two geometry points.");
  }

  route.geometry.reserve(
      geometry_count);

  for (std::size_t i = 0U;
       i < geometry_count;
       ++i) {
    route.geometry.push_back(
        {
            reader.read_double(),
            reader.read_double(),
        });
  }


  const auto maneuver_count =
      reader.read_count(
          kMaximumManeuverCount,
          "maneuvers");

  route.maneuvers.reserve(
      maneuver_count);

  for (std::size_t i = 0U;
       i < maneuver_count;
       ++i) {

    RouteManeuver maneuver;


    const auto maneuver_type =
        reader.read_i32();

    if (maneuver_type <
            static_cast<std::int32_t>(
                ManeuverType::Unknown) ||
        maneuver_type >
            static_cast<std::int32_t>(
                ManeuverType::Arrive)) {
      throw std::invalid_argument(
          "Native route contains invalid maneuver type.");
    }

    maneuver.type =
        static_cast<ManeuverType>(
            maneuver_type);


    maneuver.instruction =
        reader.read_string();


    const auto street_name_count =
        reader.read_count(
            kMaximumStreetNamesPerManeuver,
            "street names");

    maneuver.street_names.reserve(
        street_name_count);

    for (std::size_t street_index = 0U;
         street_index < street_name_count;
         ++street_index) {
      maneuver.street_names.push_back(
          reader.read_string());
    }


    maneuver.distance_m =
        reader.read_double();

    maneuver.duration_s =
        reader.read_double();


    const auto begin_shape_index =
        reader.read_i32();

    const auto end_shape_index =
        reader.read_i32();

    if (begin_shape_index < 0 ||
        end_shape_index < 0) {
      throw std::invalid_argument(
          "Native route maneuver shape index is negative.");
    }

    maneuver.begin_shape_index =
        static_cast<std::size_t>(
            begin_shape_index);

    maneuver.end_shape_index =
        static_cast<std::size_t>(
            end_shape_index);


    maneuver.bearing_before_deg =
        read_optional_bearing(
            reader);

    maneuver.bearing_after_deg =
        read_optional_bearing(
            reader);

    maneuver.engine_type =
        read_optional_engine_type(
            reader);


    route.maneuvers.push_back(
        std::move(
            maneuver));
  }


  route.engine_name =
      reader.read_string();

  route.engine_version =
      reader.read_string();


  const auto segment_status =
      reader.read_i32();

  if (segment_status <
          static_cast<std::int32_t>(
              RouteSegmentDataStatus::Unspecified) ||
      segment_status >
          static_cast<std::int32_t>(
              RouteSegmentDataStatus::Unavailable)) {
    throw std::invalid_argument(
        "Native route contains invalid segment data status.");
  }

  route.segment_data_status =
      static_cast<RouteSegmentDataStatus>(
          segment_status);


  const auto diagnostic_count =
      reader.read_count(
          kMaximumDiagnosticCount,
          "diagnostics");

  route.diagnostics.reserve(
      diagnostic_count);

  for (std::size_t i = 0U;
       i < diagnostic_count;
       ++i) {

    RouteDiagnostic diagnostic;

    diagnostic.code =
        reader.read_string();

    diagnostic.message =
        reader.read_string();

    route.diagnostics.push_back(
        std::move(
            diagnostic));
  }


  if (!reader.at_end()) {
    throw std::invalid_argument(
        "Native route payload contains trailing bytes.");
  }


  return route;
}


class NativeSessionState {
 public:
  NativeSessionState() {
    install(
        make_fixture_route());
  }


  [[nodiscard]]
  NavigationSession&
  session() {
    return *session_;
  }


  void
  install(
      RoutePath route) {

    if (generation_ ==
        std::numeric_limits<std::uint64_t>::max()) {
      throw std::overflow_error(
          "Native navigation session generation exhausted.");
    }

    const auto next_generation =
        generation_ +
        1U;

    auto next =
        std::make_unique<NavigationSession>(
            "navigation-session:android-jni:" +
                std::to_string(
                    next_generation),
            route);

    generation_ =
        next_generation;

    session_ =
        std::move(
            next);
  }


 private:
  std::uint64_t generation_ =
      0U;

  std::unique_ptr<NavigationSession>
      session_;
};


NativeSessionState&
native_session_state() {
  static NativeSessionState state;

  return state;
}


NavigationSession&
native_session() {
  return
      native_session_state()
          .session();
}


std::mutex&
native_session_mutex() {
  static std::mutex mutex;

  return mutex;
}


void
throw_illegal_state(
    JNIEnv* env,
    const std::string& message) {
  jclass exception_class =
      env->FindClass(
          "java/lang/IllegalStateException");

  if (exception_class != nullptr) {
    env->ThrowNew(
        exception_class,
        message.c_str());

    env->DeleteLocalRef(
        exception_class);
  }
}


jobject
to_java_snapshot(
    JNIEnv* env,
    const NavigationSnapshot& snapshot) {
  if (snapshot.route_preview == nullptr) {
    throw std::runtime_error(
        "Native navigation snapshot has no route preview.");
  }

  const auto& route =
      *snapshot.route_preview;


  jclass snapshot_class =
      env->FindClass(
          "org/routingplatform/app/navigation/"
          "NativeNavigationSnapshot");

  if (snapshot_class == nullptr) {
    return nullptr;
  }


  jmethodID constructor =
      env->GetMethodID(
          snapshot_class,
          "<init>",
          "(ILjava/lang/String;"
          "ILjava/lang/String;"
          "DD[DIDDDD"
          "Ljava/lang/String;"
          "DZZZZZZZ)V");

  if (constructor == nullptr) {
    env->DeleteLocalRef(
        snapshot_class);

    return nullptr;
  }


  std::vector<jdouble> geometry;

  geometry.reserve(
      route.geometry.size() * 2);

  for (const auto& point :
       route.geometry) {
    geometry.push_back(
        static_cast<jdouble>(
            point.latitude));

    geometry.push_back(
        static_cast<jdouble>(
            point.longitude));
  }


  jdoubleArray geometry_array =
      env->NewDoubleArray(
          static_cast<jsize>(
              geometry.size()));

  if (geometry_array == nullptr) {
    env->DeleteLocalRef(
        snapshot_class);

    return nullptr;
  }

  env->SetDoubleArrayRegion(
      geometry_array,
      0,
      static_cast<jsize>(
          geometry.size()),
      geometry.data());


  jstring session_id =
      env->NewStringUTF(
          snapshot.session_id.c_str());

  jstring route_id =
      env->NewStringUTF(
          route.route_id.c_str());


  jstring current_instruction =
      nullptr;

  if (snapshot.current_maneuver.has_value() &&
      !snapshot.current_maneuver
           ->instruction.empty()) {
    current_instruction =
        env->NewStringUTF(
            snapshot.current_maneuver
                ->instruction.c_str());
  }


  jobject result =
      env->NewObject(
          snapshot_class,
          constructor,

          static_cast<jint>(
              snapshot.schema_version),

          session_id,

          static_cast<jint>(
              snapshot.state),

          route_id,

          static_cast<jdouble>(
              route.distance_m),

          static_cast<jdouble>(
              route.duration_s),

          geometry_array,

          static_cast<jint>(
              snapshot.shape_segment_index),

          static_cast<jdouble>(
              snapshot.segment_fraction),

          static_cast<jdouble>(
              snapshot.progress_fraction),

          static_cast<jdouble>(
              snapshot.remaining_distance_m),

          static_cast<jdouble>(
              snapshot.remaining_duration_s),

          current_instruction,

          static_cast<jdouble>(
              snapshot
                  .distance_to_current_maneuver_end_m),

          static_cast<jboolean>(
              snapshot.arrived),

          static_cast<jboolean>(
              snapshot.reroute_requested),

          static_cast<jboolean>(
              snapshot.route_recomputed),

          static_cast<jboolean>(
              snapshot.routing_engine_invoked),

          static_cast<jboolean>(
              snapshot.candidate_selection_invoked),

          static_cast<jboolean>(
              snapshot.cost_engine_invoked),

          static_cast<jboolean>(
              snapshot
                  .production_route_mutation_allowed));


  if (current_instruction != nullptr) {
    env->DeleteLocalRef(
        current_instruction);
  }

  if (route_id != nullptr) {
    env->DeleteLocalRef(
        route_id);
  }

  if (session_id != nullptr) {
    env->DeleteLocalRef(
        session_id);
  }

  env->DeleteLocalRef(
      geometry_array);

  env->DeleteLocalRef(
      snapshot_class);


  return result;
}


jobject
current_snapshot(
    JNIEnv* env) {
  try {
    std::lock_guard<std::mutex> lock(
        native_session_mutex());

    return to_java_snapshot(
        env,
        native_session().snapshot());
  } catch (const std::exception& error) {
    throw_illegal_state(
        env,
        error.what());

    return nullptr;
  }
}


jobject
start_navigation(
    JNIEnv* env) {
  try {
    std::lock_guard<std::mutex> lock(
        native_session_mutex());

    return to_java_snapshot(
        env,
        native_session().start());
  } catch (const std::exception& error) {
    throw_illegal_state(
        env,
        error.what());

    return nullptr;
  }
}


jobject
update_progress(
    JNIEnv* env,
    const jint shape_segment_index,
    const jdouble segment_fraction) {
  try {
    if (shape_segment_index < 0) {
      throw std::invalid_argument(
          "Navigation shape_segment_index must not be negative.");
    }

    NavigationProgressUpdate update;

    update.shape_segment_index =
        static_cast<std::size_t>(
            shape_segment_index);

    update.segment_fraction =
        static_cast<double>(
            segment_fraction);

    std::lock_guard<std::mutex> lock(
        native_session_mutex());

    return to_java_snapshot(
        env,
        native_session().update_progress(
            update));
  } catch (const std::exception& error) {
    throw_illegal_state(
        env,
        error.what());

    return nullptr;
  }
}

jobject
install_route(
    JNIEnv* env,
    jbyteArray payload) {
  try {
    if (payload == nullptr) {
      throw std::invalid_argument(
          "Native route payload must not be null.");
    }


    const jsize payload_length =
        env->GetArrayLength(
            payload);

    if (payload_length <= 0 ||
        static_cast<std::size_t>(
            payload_length) >
            kMaximumNativeRoutePayloadBytes) {
      throw std::invalid_argument(
          "Native route payload has invalid size.");
    }


    std::vector<jbyte> bytes(
        static_cast<std::size_t>(
            payload_length));

    env->GetByteArrayRegion(
        payload,
        0,
        payload_length,
        bytes.data());

    if (env->ExceptionCheck()) {
      return nullptr;
    }


    auto route =
        decode_native_route_payload(
            bytes);


    std::lock_guard<std::mutex> lock(
        native_session_mutex());


    /*
     * Route installation is an external route-selection boundary.
     *
     * It is permitted before navigation or after arrival, but it
     * may never mutate the immutable route of an active session.
     */
    if (native_session().state() ==
        NavigationSessionState::Navigating) {
      throw std::logic_error(
          "Cannot install a route while navigation is active.");
    }


    native_session_state()
        .install(
            std::move(
                route));


    return to_java_snapshot(
        env,
        native_session()
            .snapshot());

  } catch (const std::exception& error) {
    throw_illegal_state(
        env,
        error.what());

    return nullptr;
  }
}




jobject
replace_navigating_route(
    JNIEnv* env,
    jbyteArray payload) {
  try {
    if (payload == nullptr) {
      throw std::invalid_argument(
          "Replacement route payload must not be null.");
    }


    const jsize payload_length =
        env->GetArrayLength(
            payload);

    if (payload_length <= 0 ||
        static_cast<std::size_t>(
            payload_length) >
            kMaximumNativeRoutePayloadBytes) {
      throw std::invalid_argument(
          "Replacement route payload has invalid size.");
    }


    std::vector<jbyte> bytes(
        static_cast<std::size_t>(
            payload_length));

    env->GetByteArrayRegion(
        payload,
        0,
        payload_length,
        bytes.data());

    if (env->ExceptionCheck()) {
      return nullptr;
    }


    auto route =
        decode_native_route_payload(
            bytes);


    std::lock_guard<std::mutex> lock(
        native_session_mutex());


    /*
     * This is NOT ordinary route installation.
     *
     * It is the explicit rerouting session boundary:
     * the current immutable session is discarded and a new
     * NavigationSession is constructed from an externally
     * selected route.
     *
     * No routing engine is invoked here.
     */
    if (native_session().state() !=
        NavigationSessionState::Navigating) {
      throw std::logic_error(
          "Navigating route replacement requires an active navigation session.");
    }


    native_session_state()
        .install(
            std::move(
                route));


    return to_java_snapshot(
        env,
        native_session()
            .start());

  } catch (const std::exception& error) {
    throw_illegal_state(
        env,
        error.what());

    return nullptr;
  }
}

}  // namespace


extern "C"
JNIEXPORT jobject JNICALL
Java_org_routingplatform_app_navigation_\
JniNavigationCoreBridge_nativeCurrentSnapshot(
    JNIEnv* env,
    jobject) {
  return current_snapshot(
      env);
}


extern "C"
JNIEXPORT jobject JNICALL
Java_org_routingplatform_app_navigation_\
JniNavigationCoreBridge_nativeStartNavigation(
    JNIEnv* env,
    jobject) {
  return start_navigation(
      env);
}


extern "C"
JNIEXPORT jobject JNICALL
Java_org_routingplatform_app_navigation_\
JniNavigationCoreBridge_nativeUpdateProgress(
    JNIEnv* env,
    jobject,
    const jint shape_segment_index,
    const jdouble segment_fraction) {
  return update_progress(
      env,
      shape_segment_index,
      segment_fraction);
}

extern "C"
JNIEXPORT jobject JNICALL
Java_org_routingplatform_app_navigation_\
JniNavigationCoreBridge_nativeInstallRoute(
    JNIEnv* env,
    jobject,
    jbyteArray payload) {
  return install_route(
      env,
      payload);
}

extern "C"
JNIEXPORT jobject JNICALL
Java_org_routingplatform_app_navigation_\
JniNavigationCoreBridge_nativeReplaceNavigatingRoute(
    JNIEnv* env,
    jobject,
    jbyteArray payload) {
  return replace_navigating_route(
      env,
      payload);
}
