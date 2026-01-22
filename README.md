# Android Math Agent

A fully offline Socratic Math Tutor for Android, powered by:
- **Qwen2.5-Math-1.5B** (GGUF, 4-bit quantization)
- **llama.cpp** with Vulkan GPU acceleration
- **Kotlin** + Jetpack Compose
- **ReAct Agent** pattern with tool calling
- **SymPy** via Chaquopy for symbolic algebra

## Features

- **100% Offline** - No internet required after model download
- **Math-Specialized** - Qwen2.5-Math trained specifically for mathematical reasoning (79.7% MATH benchmark)
- **8 Math Tools** - calculate, solve_equation, simplify_expression, expand_expression, factor_expression, get_hint, verify_worked_example, check_answer
- **Socratic Method** - Teaches by questioning, not giving answers
- **GPU Accelerated** - Vulkan backend for Mali-G710 (Tensor G2)
- **Modern UI** - Jetpack Compose with streaming typewriter effect
- **Auto-Download** - Model (~940MB) downloaded on first launch

## All Math Tools

| Tool | Description | Example |
|------|-------------|---------|
| `calculate` | Evaluate numeric expressions | `"2 + 2"` → `"4"` |
| `solve_equation` | Solve equations | `"2x + 5 = 15"` → `"x = 5"` |
| `simplify_expression` | Simplify algebraic expressions | `"2x + 3x"` → `"5x"` |
| `expand_expression` | Expand expressions | `"(x+1)^2"` → `"x^2 + 2x + 1"` |
| `factor_expression` | Factor expressions | `"x^2 - 4"` → `"(x-2)(x+2)"` |
| `get_hint` | Generate Socratic hints | Thoughtful questions |
| `verify_worked_example` | Check step-by-step work | Feedback on each step |
| `check_answer` | Verify answer correctness | Correct/Incorrect |

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    MainActivity.kt                          │
│  - Permission handling (storage)                             │
│  - Model download progress (940MB)                           │
│  - App state management (Loading → Download → Ready)       │
└────────────┬────────────────────────────────────────────────┘
             │
     ┌───────┴────────┐
     ▼                ▼
┌──────────────┐  ┌──────────────────────────────────┐
│ ModelManager │  │         ReActAgent               │
│              │  │  - Socratic system prompt       │
│ HuggingFace   │  │  - ReAct loop with GBNF        │
│ GitHub        │  │  - 8 tool implementations      │
└──────┬───────┘  └──────────────┬───────────────┘
       │                        │
       ▼                        ▼
┌──────────────┐     ┌─────────────────────────────────┐
│ LlamaEngine  │     │      MathTools(context)        │
│              │     └──────────────┬──────────────────┘
│ llama.cpp    │                    │
└──────┬───────┘                    ▼
       │                  ┌──────────────────┐
       ▼                  │   SymPyBridge    │
┌──────────────┐          │                  │
│ Qwen2.5-Math │          │ sympy_bridge.py │
│   (LLM)      │          │   (SymPy 1.12)   │
└──────────────┘          └──────────────────┘
```

## Project Structure

```
Android-Math-Agent/
├── .github/workflows/
│   └── build.yml              # CI/CD for APK building
├── app/
│   ├── build.gradle.kts       # Gradle + Chaquopy config
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── cpp/
│       │   ├── CMakeLists.txt  # llama.cpp build config
│       │   └── llama_jni.cpp   # JNI bindings
│       ├── python/
│       │   └── sympy_bridge.py # SymPy wrapper
│       └── java/com/mathagent/
│           ├── MainActivity.kt
│           ├── LlamaEngine.kt
│           ├── ReActAgent.kt
│           ├── MathTools.kt
│           ├── ModelManager.kt
│           └── SymPyBridge.kt
├── external/llama.cpp/         # LLM library
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```

## Building

### Prerequisites

- Android Studio Hedgehog (2023.1.1) or later
- Android NDK (auto-downloaded by Gradle)
- JDK 17

### Build Steps

```bash
cd /home/ghively/GH-OS/Android-Math-Agent

# Build debug APK
./gradlew assembleDebug

# Install on connected device
./gradlew installDebug
```

The Gradle build will automatically:
1. Download Android NDK if needed
2. Build llama.cpp for ARM64 with CMake
3. Compile JNI bindings
4. Download SymPy via pip (Chaquopy)
5. Package everything into APK (~30MB)

## Model Download

The model (~940MB) is downloaded automatically on first launch:
- First checks GitHub releases
- Falls back to HuggingFace if unavailable
- Shows progress with MB display
- Cached locally for subsequent launches

## Model Details

| Property | Value |
|----------|-------|
| Model | Qwen2.5-Math-1.5B-Instruct |
| Quantization | Q4_K_M (4-bit) |
| Size | 940MB |
| Context Window | 2048 tokens |
| Backend | Vulkan GPU + ARM NEON CPU |
| Target Speed | 15-25 tokens/sec |

## Performance Targets (Tensor G2 + Vulkan)

| Metric | Target |
|--------|--------|
| Model load time | < 5 seconds |
| First token latency | < 1 second |
| Token generation | 15-25 t/s |
| Memory usage | < 4GB (app + model) |

## Example Interactions

### Algebra
```
You: Simplify 2x + 3x

Math Mentor: Let me think about that...
🔧 Using: simplify_expression
Tool returned: 5x
Simplified 2x + 3x to 5x
Notice that both terms have 'x' in them. Can you see what you get when you combine like terms?
```

### Equations
```
You: Solve x^2 - 4 = 0

Math Mentor: I'll help you work through this equation.
🔧 Using: solve_equation
Tool returned: x = -2, x = 2
Found two solutions. Do you know why there are two answers here?
```

### Socratic Hint
```
You: I'm stuck on factoring x^2 + 5x + 6

Math Mentor: 🔧 Using: get_hint
What do you notice about the coefficients? Can you think of two numbers that multiply to 6 and add to 5?
```

## APK Size Breakdown

| Component | Size |
|-----------|------|
| Base app (Kotlin + Compose) | ~5MB |
| llama.cpp native library | ~3MB |
| Chaquopy + Python runtime | ~8MB |
| SymPy + NumPy | ~12MB |
| Total APK | ~28MB |
| Model (downloaded separately) | ~940MB |

## References

- [llama.cpp](https://github.com/ggerganov/llama.cpp)
- [Qwen2.5-Math](https://huggingface.co/Qwen/Qwen2.5-Math-1.5B-Instruct)
- [Qwen2.5-Math GGUF](https://huggingface.co/RichardErkhov/Qwen_-_Qwen2.5-Math-1.5B-gguf)
- [Chaquopy](https://chaquo.com/chaquopy/)
