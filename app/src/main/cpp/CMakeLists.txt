cmake_minimum_required(VERSION 3.22.1)

project("mathagent" VERSION 1.0.0 LANGUAGES C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# ============================================================================
# Android-specific configuration
# ============================================================================

if(DEFINED ANDROID_ABI)
    message(STATUS "Building for Android ABI: ${ANDROID_ABI}")

    if(ANDROID_ABI STREQUAL "arm64-v8a")
        set(GGML_SYSTEM_ARCH "ARM64")
        set(GGML_CPU_KLEIDIAI ON)
        set(GGML_OPENMP ON)
    elseif(ANDROID_ABI STREQUAL "x86_64")
        set(GGML_SYSTEM_ARCH "x86_64")
        set(GGML_CPU_KLEIDIAI OFF)
        set(GGML_OPENMP OFF)
    else()
        message(FATAL_ERROR "Unsupported ABI: ${ANDROID_ABI}")
    endif()

    message(STATUS "GGML_SYSTEM_ARCH: ${GGML_SYSTEM_ARCH}")
    message(STATUS "GGML_CPU_KLEIDIAI: ${GGML_CPU_KLEIDIAI}")
    message(STATUS "GGML_OPENMP: ${GGML_OPENMP}")
endif()

# ============================================================================
# llama.cpp integration
# ============================================================================

# Path to llama.cpp source (assumes it's checked out in external/llama.cpp)
set(LLAMA_SRC "${CMAKE_CURRENT_SOURCE_DIR}/../../../../external/llama.cpp")

if(NOT EXISTS "${LLAMA_SRC}")
    message(FATAL_ERROR "llama.cpp not found at: ${LLAMA_SRC}\n"
                        "Run: git clone --depth 1 https://github.com/ggerganov/llama.cpp.git external/llama.cpp")
endif()

message(STATUS "Using llama.cpp from: ${LLAMA_SRC}")

# Add llama.cpp as subdirectory
add_subdirectory(${LLAMA_SRC} build-llama)

# ============================================================================
# MathAgent native library
# ============================================================================

add_library(${CMAKE_PROJECT_NAME} SHARED
    llama_jni.cpp
)

target_compile_definitions(${CMAKE_PROJECT_NAME} PRIVATE
    GGML_SYSTEM_ARCH=${GGML_SYSTEM_ARCH}
    GGML_CPU_KLEIDIAI=$<BOOL:${GGML_CPU_KLEIDIAI}>
    GGML_OPENMP=$<BOOL:${GGML_OPENMP}>
)

target_include_directories(${CMAKE_PROJECT_NAME} PRIVATE
    ${LLAMA_SRC}
    ${LLAMA_SRC}/include
    ${LLAMA_SRC}/common
    ${LLAMA_SRC}/ggml/include
    ${LLAMA_SRC}/ggml/src
)

target_link_libraries(${CMAKE_PROJECT_NAME}
    llama
    ggml
    common
    android
    log
)

# ============================================================================
# Build options
# ============================================================================

# Vulkan support (GPU acceleration on Mali-G710)
option(LLAMA_VULKAN "Enable Vulkan backend" ON)
if(ANDROID_ABI STREQUAL "arm64-v8a" AND LLAMA_VULKAN)
    target_compile_definitions(${CMAKE_PROJECT_NAME} PRIVATE LLAMA_VULKAN=ON)
    message(STATUS "Vulkan backend: ENABLED")
else()
    message(STATUS "Vulkan backend: DISABLED")
endif()
