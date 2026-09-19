#include <jni.h>

#include <mutex>
#include <string>

// This JNI bridge is intentionally a separate target from routing_platform_jni.
// The actual llama.cpp symbols are linked only by the reviewed release-candidate build.
#ifndef SOCIAL_AI_RUNTIME_BUILD_ID
#define SOCIAL_AI_RUNTIME_BUILD_ID ""
#endif
#ifndef SOCIAL_AI_RUNTIME_ARTIFACT_SHA256
#define SOCIAL_AI_RUNTIME_ARTIFACT_SHA256 ""
#endif

namespace {
std::mutex g_mutex;
bool g_loaded = false;

jstring to_jstring(JNIEnv* env, const char* value) {
    return env->NewStringUTF(value);
}
}

extern "C" JNIEXPORT jstring JNICALL
Java_org_routingplatform_app_ai_JniSocialAiNativeEngine_nativeEngineId(JNIEnv* env, jobject) {
    return to_jstring(env, SOCIAL_AI_RUNTIME_BUILD_ID);
}

extern "C" JNIEXPORT jstring JNICALL
Java_org_routingplatform_app_ai_JniSocialAiNativeEngine_nativeArtifactSha256(JNIEnv* env, jobject) {
    return to_jstring(env, SOCIAL_AI_RUNTIME_ARTIFACT_SHA256);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_org_routingplatform_app_ai_JniSocialAiNativeEngine_nativeLoadModel(
    JNIEnv* env, jobject, jstring local_path, jint context_tokens) {
    if (local_path == nullptr || context_tokens <= 0) return JNI_FALSE;
    const char* path = env->GetStringUTFChars(local_path, nullptr);
    if (path == nullptr || path[0] == '\0') {
        if (path != nullptr) env->ReleaseStringUTFChars(local_path, path);
        return JNI_FALSE;
    }
    env->ReleaseStringUTFChars(local_path, path);
    // Fail closed until the release build links the pinned llama.cpp implementation.
    std::lock_guard<std::mutex> lock(g_mutex);
    g_loaded = false;
    return JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_org_routingplatform_app_ai_JniSocialAiNativeEngine_nativeUnloadModel(JNIEnv*, jobject) {
    std::lock_guard<std::mutex> lock(g_mutex);
    g_loaded = false;
}

extern "C" JNIEXPORT jstring JNICALL
Java_org_routingplatform_app_ai_JniSocialAiNativeEngine_nativeGenerate(
    JNIEnv* env, jobject, jstring, jint) {
    std::lock_guard<std::mutex> lock(g_mutex);
    return to_jstring(env, "");
}
