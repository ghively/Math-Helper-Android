# Android Math Agent - Setup Guide

## What's Been Done

✅ Created Android project structure with Kotlin
✅ Implemented ReAct agent loop with GBNF grammar for tool calling
✅ Ported math tools from Python SymPy to Kotlin (exp4j)
✅ Built Compose UI matching web chat interface
✅ Added llama.cpp wrapper (needs JNI implementation)

## What's Left To Do

### 1. Build llama.cpp for Android ARM64 with Vulkan

```bash
# Clone llama.cpp
git clone https://github.com/ggerganov/llama.cpp.git
cd llama.cpp

# Install Android NDK
# Download from: https://developer.android.com/ndk/downloads
export ANDROID_NDK=/path/to/ndk

# Build for ARM64 with Vulkan support
mkdir build-android && cd build-android
cmake -DCMAKE_TOOLCHAIN_FILE=$ANDROID_NDK/build/cmake/android.toolchain.cmake \
      -DANDROID_ABI=arm64-v8a \
      -DANDROID_PLATFORM=android-28 \
      -DLLAMA_VULKAN=ON \
      -DBUILD_SHARED_LIBS=ON \
      ..

make -j$(nproc)
```

Output: `libllama.so` for ARM64

### 2. Create JNI Bindings

Create `llama.cpp/jni/android.cpp`:

```cpp
#include <jni.h>
#include "llama.h"

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_mathagent_LlamaEngine_nativeLoadModel(
    JNIEnv *env,
    jobject thiz,
    jstring path,
    jint n_ctx,
    jint n_gpu_layers
) {
    const char *model_path = env->GetStringUTFChars(path, nullptr);

    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = n_gpu_layers;

    llama_model *model = llama_load_model_from_file(model_path, model_params);
    env->ReleaseStringUTFChars(path, model_path);

    return (jlong)model;
}

// TODO: Implement nativeGenerate, nativeFreeContext, nativeFreeModel

}
```

### 3. Download Qwen2.5-Math-1.5B GGUF Model

```bash
# Create models directory
mkdir -p app/src/main/assets/models

# Download Q4_K_M quantization (~1GB)
wget https://huggingface.co/RichardErkhov/Qwen_-_Qwen2.5-Math-1.5B-gguf/resolve/main/Qwen2.5-Math-1.5B.Q4_K_M.gguf \
  -O app/src/main/assets/models/qwen2.5-math-1.5b-q4_k_m.gguf
```

Alternative models:
- **Higher accuracy**: Qwen2.5-Math-3B-Q4_K_M (~2GB)
- **Faster**: Qwen2.5-Math-1.5B-Q3_K_M (~800MB)

### 4. Copy libllama.so to Project

```bash
mkdir -p app/src/main/jniLibs/arm64-v8a
cp llama.cpp/build-android/libllama.so app/src/main/jniLibs/arm64-v8a/
```

### 5. Update MainActivity to Initialize Engine

In `MainActivity.kt`, uncomment and complete:

```kotlin
val llamaEngine = LlamaEngine(applicationContext)
lifecycleScope.launch {
    val success = llamaEngine.loadModel(
        "/data/local/tmp/qwen2.5-math-1.5b-q4_k_m.gguf"
    )
    if (success) {
        val agent = ReActAgent(llamaEngine)
        // Use agent.chat(message)
    }
}
```

### 6. Build and Install

```bash
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

---

## Performance Targets (Tensor G2 + Vulkan)

| Metric | Target |
|--------|--------|
| Model load time | < 5 seconds |
| First token latency | < 1 second |
| Token generation | 15-25 t/s |
| Memory usage | < 4GB (model + overhead) |

---

## Reference Resources

- **llama.cpp Android**: https://github.com/ggerganov/llama.cpp
- **kotlinllamacpp**: https://github.com/ljcamargo/kotlinllamacpp
- **Qwen2.5-Math GGUF**: https://huggingface.co/RichardErkhov/Qwen_-_Qwen2.5-Math-1.5B-gguf
- **Vulkan on Mali-G710**: https://developer.arm.com/documentation/101895/latest
- **GBNF Grammar**: https://github.com/ggerganov/llama.cpp/blob/master/grammars/README.md
