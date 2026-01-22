#include <android/log.h>
#include <jni.h>
#include <string>
#include <cstring>
#include <unistd.h>

#include "llama.h"
#include "common.h"
#include "sampling.h"

#define TAG "MathAgent"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)

// --------------------------------------------------------------------------
// Global state
// --------------------------------------------------------------------------

static llama_model *g_model = nullptr;
static llama_context *g_context = nullptr;
static llama_batch g_batch = {};
static common_sampler *g_sampler = nullptr;
static std::string g_grammar = "";

// --------------------------------------------------------------------------
// Configuration constants
// --------------------------------------------------------------------------

constexpr int DEFAULT_N_CTX = 2048;
constexpr int DEFAULT_N_THREADS = 4;
constexpr int DEFAULT_N_BATCH = 512;
constexpr float DEFAULT_TEMPERATURE = 0.7f;

// --------------------------------------------------------------------------
// Helper functions
// --------------------------------------------------------------------------

extern "C" {

/**
 * Initialize the llama.cpp backend
 */
JNIEXPORT void JNICALL
Java_com_mathagent_LlamaEngine_nativeInit(JNIEnv *env, jobject /*this*/) {
    llama_backend_init();
    llama_log_set([](int level, const char *text, void *user_data) {
        if (level >= LLAMA_LOG_LEVEL_ERROR) {
            LOGE("%s", text);
        } else if (level >= LLAMA_LOG_LEVEL_WARNING) {
            LOGI("%s", text);
        }
    }, nullptr);
    LOGI("llama.cpp backend initialized");
}

/**
 * Load a GGUF model from file
 *
 * @param modelPath Path to the GGUF model file
 * @param nCtx Context window size (tokens)
 * @param nGpuLayers Number of layers to offload to GPU (Vulkan)
 * @return Native pointer to model, or 0 on failure
 */
JNIEXPORT jlong JNICALL
Java_com_mathagent_LlamaEngine_nativeLoadModel(
    JNIEnv *env,
    jobject /*this*/,
    jstring modelPath,
    jint nCtx,
    jint nGpuLayers
) {
    const char *model_path_cstr = env->GetStringUTFChars(modelPath, nullptr);
    LOGI("Loading model from: %s", model_path_cstr);
    LOGI("Context size: %d, GPU layers: %d", nCtx, nGpuLayers);

    // Configure model parameters
    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = (int)nGpuLayers;

    // Load the model
    llama_model *model = llama_load_model_from_file(model_path_cstr, model_params);
    env->ReleaseStringUTFChars(modelPath, model_path_cstr);

    if (!model) {
        LOGE("Failed to load model from: %s", model_path_cstr);
        return 0;
    }

    g_model = model;
    LOGI("Model loaded successfully");
    return reinterpret_cast<jlong>(model);
}

/**
 * Initialize context for generation
 */
JNIEXPORT jlong JNICALL
Java_com_mathagent_LlamaEngine_nativeInitContext(
    JNIEnv *env,
    jobject /*this*/,
    jlong modelPtr,
    jint nCtx,
    jint nThreads,
    jfloat temperature
) {
    if (!modelPtr) {
        LOGE("Model pointer is null");
        return 0;
    }

    llama_model *model = reinterpret_cast<llama_model *>(modelPtr);

    // Configure context parameters
    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = (int32_t)nCtx;
    ctx_params.n_batch = DEFAULT_N_BATCH;
    ctx_params.n_ubatch = DEFAULT_N_BATCH;
    ctx_params.n_threads = (int)nThreads > 0 ? (int)nThreads : DEFAULT_N_THREADS;
    ctx_params.n_threads_batch = ctx_params.n_threads;

    // Initialize context
    llama_context *context = llama_init_from_model(model, ctx_params);
    if (!context) {
        LOGE("Failed to initialize context");
        return 0;
    }

    // Initialize sampler
    common_params_sampling sparams;
    sparams.temp = temperature;
    sparams.top_p = 0.95f;
    sparams.top_k = 40;
    g_sampler = common_sampler_init(model, sparams);
    if (!g_sampler) {
        LOGE("Failed to initialize sampler");
        llama_free(context);
        return 0;
    }

    // Initialize batch
    g_batch = llama_batch_init(DEFAULT_N_BATCH, 0, 1);

    g_context = context;
    LOGI("Context initialized with %d threads", ctx_params.n_threads);
    return reinterpret_cast<jlong>(context);
}

/**
 * Tokenize a string
 */
JNIEXPORT jstring JNICALL
Java_com_mathagent_LlamaEngine_nativeTokenize(
    JNIEnv *env,
    jobject /*this*/,
    jlong modelPtr,
    jstring text
) {
    if (!modelPtr) {
        return env->NewStringUTF("");
    }

    llama_model *model = reinterpret_cast<llama_model *>(modelPtr);
    const char *text_cstr = env->GetStringUTFChars(text, nullptr);

    // Tokenize
    std::vector<llama_token> tokens = common_tokenize(model, text_cstr, true);

    env->ReleaseStringUTFChars(text, text_cstr);

    // Return token count for now (can be expanded)
    char result[64];
    snprintf(result, sizeof(result), "%zu", tokens.size());
    return env->NewStringUTF(result);
}

/**
 * Generate completion with streaming callback
 *
 * @param contextPtr Context pointer
 * @param prompt Input prompt
 * @param maxTokens Maximum tokens to generate
 * @param temperature Sampling temperature
 * @param grammar Optional GBNF grammar (null for none)
 * @param callback Java callback object for streaming
 * @return Generated text
 */
JNIEXPORT jstring JNICALL
Java_com_mathagent_LlamaEngine_nativeGenerate(
    JNIEnv *env,
    jobject /*this*/,
    jlong contextPtr,
    jstring prompt,
    jint maxTokens,
    jfloat temperature,
    jstring grammar,
    jobject callback
) {
    if (!contextPtr || !g_model) {
        LOGE("Context or model is null");
        return env->NewStringUTF("");
    }

    llama_context *context = reinterpret_cast<llama_context *>(contextPtr);
    const char *prompt_cstr = env->GetStringUTFChars(prompt, nullptr);

    // Tokenize prompt
    std::vector<llama_token> tokens = common_tokenize(g_model, prompt_cstr, true);
    env->ReleaseStringUTFChars(prompt, prompt_cstr);

    LOGI("Tokenized prompt: %zu tokens", tokens.size());

    // Get callback class and method
    jclass callbackClass = env->GetObjectClass(callback);
    jmethodID onTokenMethod = env->GetMethodID(callbackClass, "onToken", "(Ljava/lang/String;)V");

    if (!onTokenMethod) {
        LOGE("Failed to find onToken callback method");
    }

    // Reset sampler
    common_sampler_reset(g_sampler);

    // Process prompt tokens
    for (size_t i = 0; i < tokens.size(); i++) {
        common_batch_add(g_batch, tokens[i], i, { 0 }, false);
    }
    g_batch.logits[g_batch.n_tokens - 1] = true;

    if (llama_decode(context, g_batch) != 0) {
        LOGE("Failed to decode prompt");
        return env->NewStringUTF("");
    }

    // Generate response
    std::string generated;
    int n_generated = 0;

    while (n_generated < maxTokens) {
        // Sample token
        llama_token token = common_sampler_sample(g_sampler, context, g_batch.n_tokens - 1);
        common_sampler_accept(g_sampler, token, true);

        // Convert token to string
        char token_str[256] = {0 };
        int n_chars = llama_token_to_piece(g_model, token, token_str, sizeof(token_str) - 1);
        if (n_chars > 0) {
            generated += std::string(token_str, n_chars);

            // Call Java callback
            if (onTokenMethod) {
                jstring token_jstr = env->NewStringUTF(token_str);
                env->CallVoidMethod(callback, onTokenMethod, token_jstr);
                env->DeleteLocalRef(token_jstr);
            }
        }

        n_generated++;

        // Check for EOS
        if (token == llama_token_eos(g_model)) {
            LOGI("EOS token reached");
            break;
        }

        // Prepare next batch
        common_batch_clear(g_batch);
        common_batch_add(g_batch, token, g_batch.n_tokens, { 0 }, true);

        // Decode
        if (llama_decode(context, g_batch) != 0) {
            LOGE("Failed to decode generation");
            break;
        }
    }

    LOGI("Generated %d tokens", n_generated);
    return env->NewStringUTF(generated.c_str());
}

/**
 * Free context resources
 */
JNIEXPORT void JNICALL
Java_com_mathagent_LlamaEngine_nativeFreeContext(
    JNIEnv * /*env*/,
    jobject /*this*/,
    jlong contextPtr
) {
    if (contextPtr) {
        llama_context *context = reinterpret_cast<llama_context *>(contextPtr);
        llama_free(context);
        LOGI("Context freed");
    }

    if (g_sampler) {
        common_sampler_free(g_sampler);
        g_sampler = nullptr;
    }

    if (g_batch.n_tokens > 0) {
        llama_batch_free(g_batch);
        g_batch = {};
    }

    g_context = nullptr;
}

/**
 * Free model resources
 */
JNIEXPORT void JNICALL
Java_com_mathagent_LlamaEngine_nativeFreeModel(
    JNIEnv * /*env*/,
    jobject /*this*/,
    jlong modelPtr
) {
    if (modelPtr) {
        llama_model *model = reinterpret_cast<llama_model *>(modelPtr);
        llama_free_model(model);
        LOGI("Model freed");
    }
    g_model = nullptr;
}

/**
 * Get system information
 */
JNIEXPORT jstring JNICALL
Java_com_mathagent_LlamaEngine_nativeSystemInfo(
    JNIEnv *env,
    jobject /*this*/
) {
    const char *info = llama_print_system_info();
    return env->NewStringUTF(info);
}

/**
 * Cleanup backend
 */
JNIEXPORT void JNICALL
Java_com_mathagent_LlamaEngine_nativeShutdown(
    JNIEnv * /*env*/,
    jobject /*this*/
) {
    llama_backend_free();
    LOGI("Backend shutdown");
}

} // extern "C"
