cmake_minimum_required(VERSION 3.22.1)

project(
    routing_platform_jni
    LANGUAGES CXX
)

set(
    CMAKE_CXX_STANDARD
    20
)

set(
    CMAKE_CXX_STANDARD_REQUIRED
    ON
)

set(
    CMAKE_CXX_EXTENSIONS
    OFF
)

get_filename_component(
    ROUTING_PLATFORM_ROOT
    "${CMAKE_CURRENT_SOURCE_DIR}/../../../../../.."
    ABSOLUTE
)

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

target_compile_features(
    routing_platform_jni
    PRIVATE
        cxx_std_20
)