#include <jni.h>

#include <cstdint>
#include <exception>
#include <mutex>
#include <stdexcept>
#include <string>
#include <vector>

#include "routing/core/navigation/navigation_runtime.hpp"

namespace {

using routing::core::CandidateFamily;
using routing::core::ManeuverType;
using routing::core::RouteManeuver;
using routing::core::RoutePath;
using routing::core::RouteSegmentDataStatus;
using routing::core::navigation::NavigationSession;
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


NavigationSession&
native_session() {
  static NavigationSession session(
      "navigation-session:android-jni:1",
      make_fixture_route());

  return session;
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
          "DD[DDDD"
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