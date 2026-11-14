cmake_minimum_required(VERSION 3.22.1)

project(routing_platform_jni LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

include(FetchContent)

get_filename_component(ROUTING_PLATFORM_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../../../../../.." ABSOLUTE)

FetchContent_Declare(
    llama_cpp
    GIT_REPOSITORY https://github.com/ggml-org/llama.cpp.git
    GIT_TAG b3561
    GIT_SHALLOW TRUE
)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
FetchContent_MakeAvailable(llama_cpp)

add_library(
    routing_platform_jni
    SHARED
        routing_platform_jni.cpp
        ${ROUTING_PLATFORM_ROOT}/core/src/navigation/navigation_runtime.cpp
)

target_include_directories(
    routing_platform_jni
    PRIVATE
        ${ROUTING_PLATFORM_ROOT}/core/include
)

target_compile_features(routing_platform_jni PRIVATE cxx_std_20)

add_library(
    social_ai_llama
    SHARED
        social_ai_llama_jni.cpp
)

target_compile_definitions(
    social_ai_llama
    PRIVATE
        SOCIAL_AI_RUNTIME_BUILD_ID="llama.cpp-b3561"
        SOCIAL_AI_RUNTIME_ARTIFACT_SHA256="source-pinned-b3561"
)

target_link_libraries(social_ai_llama PRIVATE llama)
target_compile_features(social_ai_llama PRIVATE cxx_std_20)
