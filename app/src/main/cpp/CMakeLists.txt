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
endif()

# ============================================================================
# llama.cpp integration
# ============================================================================

# Path to llama.cpp source
set(LLAMA_SRC "${CMAKE_CURRENT_SOURCE_DIR}/../../../../external/llama.cpp")

if(EXISTS "${LLAMA_SRC}")
    message(STATUS "Using llama.cpp from: ${LLAMA_SRC}")
    set(HAVE_LLAMA ON)
else()
    message(WARNING "llama.cpp not found at: ${LLAMA_SRC}")
    message(WARNING "Native LLM support will be disabled. Python/SymPy still available.")
    set(HAVE_LLAMA OFF)
endif()

# ============================================================================
# MathAgent native library
# ============================================================================

add_library(${CMAKE_PROJECT_NAME} SHARED
    llama_jni.cpp
)

target_link_libraries(${CMAKE_PROJECT_NAME}
    android
    log
)

# ============================================================================
# Build options
# ============================================================================

option(LLAMA_VULKAN "Enable Vulkan backend" ON)
if(HAVE_LLAMA AND ANDROID_ABI STREQUAL "arm64-v8a" AND LLAMA_VULKAN)
    target_compile_definitions(${CMAKE_PROJECT_NAME} PRIVATE LLAMA_VULKAN=ON)
    message(STATUS "Vulkan backend: ENABLED")
else()
    message(STATUS "Vulkan backend: DISABLED (no llama.cpp)")
endif()
