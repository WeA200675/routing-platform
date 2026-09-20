#include <jni.h>
#include <android/log.h>
#include <algorithm>
#include <mutex>
#include <string>
#include <vector>
#include "llama.h"

#ifndef SOCIAL_AI_RUNTIME_BUILD_ID
#define SOCIAL_AI_RUNTIME_BUILD_ID ""
#endif
#ifndef SOCIAL_AI_RUNTIME_ARTIFACT_SHA256
#define SOCIAL_AI_RUNTIME_ARTIFACT_SHA256 ""
#endif

namespace {
std::mutex g_mutex;
llama_model * g_model = nullptr;
bool g_backend_initialized = false;

jstring js(JNIEnv * env, const std::string & s) { return env->NewStringUTF(s.c_str()); }

void unload_locked() {
    if (g_model) {
        llama_model_free(g_model);
        g_model = nullptr;
    }
}

std::string piece(const llama_vocab * vocab, llama_token token) {
    std::vector<char> buf(256);
    int n = llama_token_to_piece(vocab, token, buf.data(), (int) buf.size(), 0, true);
    if (n < 0) {
        buf.resize((size_t) -n);
        n = llama_token_to_piece(vocab, token, buf.data(), (int) buf.size(), 0, true);
    }
    return n > 0 ? std::string(buf.data(), (size_t) n) : std::string();
}
}

extern "C" JNIEXPORT jstring JNICALL
Java_org_routingplatform_app_ai_JniSocialAiNativeEngine_nativeEngineId(JNIEnv * env, jobject) {
    return js(env, SOCIAL_AI_RUNTIME_BUILD_ID);
}

extern "C" JNIEXPORT jstring JNICALL
Java_org_routingplatform_app_ai_JniSocialAiNativeEngine_nativeArtifactSha256(JNIEnv * env, jobject) {
    return js(env, SOCIAL_AI_RUNTIME_ARTIFACT_SHA256);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_org_routingplatform_app_ai_JniSocialAiNativeEngine_nativeLoadModel(
    JNIEnv * env, jobject, jstring path, jint context_tokens) {
    if (!path || context_tokens <= 0) return JNI_FALSE;
    std::lock_guard<std::mutex> lock(g_mutex);
    unload_locked();
    if (!g_backend_initialized) {
        llama_backend_init();
        g_backend_initialized = true;
    }
    const char * raw = env->GetStringUTFChars(path, nullptr);
    if (!raw) return JNI_FALSE;
    llama_model_params params = llama_model_default_params();
    params.n_gpu_layers = 0;
    g_model = llama_model_load_from_file(raw, params);
    env->ReleaseStringUTFChars(path, raw);
    return g_model ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_org_routingplatform_app_ai_JniSocialAiNativeEngine_nativeUnloadModel(JNIEnv *, jobject) {
    std::lock_guard<std::mutex> lock(g_mutex);
    unload_locked();
}

extern "C" JNIEXPORT jstring JNICALL
Java_org_routingplatform_app_ai_JniSocialAiNativeEngine_nativeGenerate(
    JNIEnv * env, jobject, jstring jprompt, jint maximum_output_tokens) {
    if (!jprompt || maximum_output_tokens <= 0) return js(env, "");
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_model) return js(env, "");

    const char * raw = env->GetStringUTFChars(jprompt, nullptr);
    if (!raw) return js(env, "");
    std::string prompt(raw);
    env->ReleaseStringUTFChars(jprompt, raw);

    const llama_vocab * vocab = llama_model_get_vocab(g_model);
    int n_prompt = -llama_tokenize(vocab, prompt.c_str(), prompt.size(), nullptr, 0, true, true);
    if (n_prompt <= 0) return js(env, "");
    std::vector<llama_token> tokens((size_t) n_prompt);
    if (llama_tokenize(vocab, prompt.c_str(), prompt.size(), tokens.data(), n_prompt, true, true) < 0) return js(env, "");

    llama_context_params cp = llama_context_default_params();
    cp.n_ctx = std::max(512, std::min(4096, n_prompt + maximum_output_tokens + 32));
    cp.n_batch = std::min(512, n_prompt);
    cp.n_threads = std::max(2, std::min(6, (int) std::thread::hardware_concurrency()));
    cp.n_threads_batch = cp.n_threads;
    llama_context * ctx = llama_init_from_model(g_model, cp);
    if (!ctx) return js(env, "");

    llama_sampler * sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(sampler, llama_sampler_init_temp(0.2f));
    llama_sampler_chain_add(sampler, llama_sampler_init_dist(0x620u));

    llama_batch batch = llama_batch_get_one(tokens.data(), n_prompt);
    std::string output;
    for (int i = 0; i < maximum_output_tokens; ++i) {
        if (llama_decode(ctx, batch) != 0) break;
        llama_token token = llama_sampler_sample(sampler, ctx, -1);
        if (llama_vocab_is_eog(vocab, token)) break;
        output += piece(vocab, token);
        batch = llama_batch_get_one(&token, 1);
    }

    llama_sampler_free(sampler);
    llama_free(ctx);
    return js(env, output);
}
