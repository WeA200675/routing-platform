#include <jni.h>
#include <llama.h>

#include <algorithm>
#include <mutex>
#include <string>
#include <vector>

#ifndef SOCIAL_AI_RUNTIME_BUILD_ID
#define SOCIAL_AI_RUNTIME_BUILD_ID "llama.cpp-unknown"
#endif
#ifndef SOCIAL_AI_RUNTIME_ARTIFACT_SHA256
#define SOCIAL_AI_RUNTIME_ARTIFACT_SHA256 "unknown"
#endif

namespace {
std::mutex g_mutex;
llama_model * g_model = nullptr;
llama_context * g_context = nullptr;
llama_sampler * g_sampler = nullptr;
const llama_vocab * g_vocab = nullptr;

jstring to_jstring(JNIEnv * env, const std::string & value) {
    return env->NewStringUTF(value.c_str());
}

void free_runtime_locked() {
    if (g_sampler != nullptr) {
        llama_sampler_free(g_sampler);
        g_sampler = nullptr;
    }
    if (g_context != nullptr) {
        llama_free(g_context);
        g_context = nullptr;
    }
    if (g_model != nullptr) {
        llama_model_free(g_model);
        g_model = nullptr;
    }
    g_vocab = nullptr;
}
}

extern "C" JNIEXPORT jstring JNICALL
Java_org_routingplatform_app_ai_JniSocialAiNativeEngine_nativeEngineId(
        JNIEnv * env, jobject) {
    return to_jstring(env, SOCIAL_AI_RUNTIME_BUILD_ID);
}

extern "C" JNIEXPORT jstring JNICALL
Java_org_routingplatform_app_ai_JniSocialAiNativeEngine_nativeArtifactSha256(
        JNIEnv * env, jobject) {
    return to_jstring(env, SOCIAL_AI_RUNTIME_ARTIFACT_SHA256);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_org_routingplatform_app_ai_JniSocialAiNativeEngine_nativeLoadModel(
        JNIEnv * env, jobject, jstring local_path, jint context_tokens) {
    if (local_path == nullptr || context_tokens < 512 || context_tokens > 32768) {
        return JNI_FALSE;
    }

    const char * path = env->GetStringUTFChars(local_path, nullptr);
    if (path == nullptr || path[0] == '\0') {
        if (path != nullptr) {
            env->ReleaseStringUTFChars(local_path, path);
        }
        return JNI_FALSE;
    }

    std::lock_guard<std::mutex> lock(g_mutex);
    free_runtime_locked();
    llama_backend_init();

    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = 0;

    g_model = llama_model_load_from_file(path, model_params);
    env->ReleaseStringUTFChars(local_path, path);
    if (g_model == nullptr) {
        free_runtime_locked();
        return JNI_FALSE;
    }

    llama_context_params context_params = llama_context_default_params();
    context_params.n_ctx = static_cast<uint32_t>(context_tokens);
    context_params.n_batch = std::min<uint32_t>(context_params.n_batch, static_cast<uint32_t>(context_tokens));
    context_params.n_threads = 2;
    context_params.n_threads_batch = 2;

    g_context = llama_init_from_model(g_model, context_params);
    if (g_context == nullptr) {
        free_runtime_locked();
        return JNI_FALSE;
    }

    g_vocab = llama_model_get_vocab(g_model);
    g_sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
    if (g_sampler == nullptr) {
        free_runtime_locked();
        return JNI_FALSE;
    }
    llama_sampler_chain_add(g_sampler, llama_sampler_init_greedy());
    return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_org_routingplatform_app_ai_JniSocialAiNativeEngine_nativeUnloadModel(
        JNIEnv *, jobject) {
    std::lock_guard<std::mutex> lock(g_mutex);
    free_runtime_locked();
}

extern "C" JNIEXPORT jstring JNICALL
Java_org_routingplatform_app_ai_JniSocialAiNativeEngine_nativeGenerate(
        JNIEnv * env, jobject, jstring prompt, jint maximum_tokens) {
    if (prompt == nullptr || maximum_tokens <= 0 || maximum_tokens > 1024) {
        return to_jstring(env, "");
    }

    const char * prompt_chars = env->GetStringUTFChars(prompt, nullptr);
    if (prompt_chars == nullptr) {
        return to_jstring(env, "");
    }

    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_context == nullptr || g_vocab == nullptr || g_sampler == nullptr) {
        env->ReleaseStringUTFChars(prompt, prompt_chars);
        return to_jstring(env, "");
    }

    const std::string prompt_text(prompt_chars);
    env->ReleaseStringUTFChars(prompt, prompt_chars);

    const int prompt_length = llama_tokenize(
        g_vocab,
        prompt_text.c_str(),
        static_cast<int32_t>(prompt_text.size()),
        nullptr,
        0,
        true,
        true
    );
    if (prompt_length <= 0) {
        return to_jstring(env, "");
    }

    std::vector<llama_token> prompt_tokens(static_cast<size_t>(prompt_length));
    if (llama_tokenize(
            g_vocab,
            prompt_text.c_str(),
            static_cast<int32_t>(prompt_text.size()),
            prompt_tokens.data(),
            prompt_length,
            true,
            true) < 0) {
        return to_jstring(env, "");
    }

    llama_sampler_reset(g_sampler);
    llama_batch batch = llama_batch_get_one(prompt_tokens.data(), prompt_tokens.size());
    if (llama_decode(g_context, batch) != 0) {
        return to_jstring(env, "");
    }

    std::string output;
    output.reserve(static_cast<size_t>(maximum_tokens) * 4U);
    for (int i = 0; i < maximum_tokens; ++i) {
        const llama_token token = llama_sampler_sample(g_sampler, g_context, -1);
        if (llama_vocab_is_eog(g_vocab, token)) {
            break;
        }

        char piece[256];
        const int piece_length = llama_token_to_piece(
            g_vocab, token, piece, static_cast<int32_t>(sizeof(piece)), 0, true);
        if (piece_length > 0) {
            output.append(piece, static_cast<size_t>(piece_length));
        }

        llama_sampler_accept(g_sampler, token);
        batch = llama_batch_get_one(&token, 1);
        if (llama_decode(g_context, batch) != 0) {
            break;
        }
    }

    return to_jstring(env, output);
}
