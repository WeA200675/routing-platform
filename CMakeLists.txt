cmake_minimum_required(VERSION 3.20)
project(routing_platform VERSION 0.3.0 LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

option(
    ROUTING_PLATFORM_BUILD_TESTS
    "Build routing-platform tests"
    ON
)

option(
    ROUTING_PLATFORM_WITH_VALHALLA
    "Link the Valhalla adapter to libvalhalla"
    OFF
)

function(routing_platform_warnings target)
    if(MSVC)
        target_compile_options(
            ${target}
            PRIVATE
                /W4
                /permissive-
        )
    else()
        target_compile_options(
            ${target}
            PRIVATE
                -Wall
                -Wextra
                -Wpedantic
                -Wconversion
                -Wshadow
        )
    endif()
endfunction()


# ---------------------------------------------------------------------------
# Core
# ---------------------------------------------------------------------------

add_library(
    routing_core
    STATIC
        core/src/cost_engine.cpp
        core/src/road_analysis.cpp
        core/src/route_analysis.cpp
)

target_include_directories(
    routing_core
    PUBLIC
        ${CMAKE_CURRENT_SOURCE_DIR}/core/include
)

target_compile_features(
    routing_core
    PUBLIC
        cxx_std_20
)

routing_platform_warnings(routing_core)


# ---------------------------------------------------------------------------
# Valhalla configuration
# ---------------------------------------------------------------------------

set(
    ROUTING_PLATFORM_VALHALLA_VERSION
    "3.8.3"
    CACHE STRING
    "Pinned Valhalla version used by routing-platform"
)

if(ROUTING_PLATFORM_WITH_VALHALLA)
    find_package(PkgConfig REQUIRED)

    pkg_check_modules(
        VALHALLA
        REQUIRED
        IMPORTED_TARGET
        "libvalhalla=${ROUTING_PLATFORM_VALHALLA_VERSION}"
    )

    find_library(
        ROUTING_PLATFORM_GEOTIFF_LIBRARY
        NAMES geotiff
        REQUIRED
    )

    message(
        STATUS
        "Valhalla backend enabled: ${VALHALLA_VERSION}"
    )

    message(
        STATUS
        "GeoTIFF library: ${ROUTING_PLATFORM_GEOTIFF_LIBRARY}"
    )
else()
    message(
        STATUS
        "Valhalla backend disabled; building open-source fallback adapter"
    )
endif()


# ---------------------------------------------------------------------------
# Valhalla adapter
# ---------------------------------------------------------------------------

add_library(
    routing_valhalla_adapter
    STATIC
        adapters/valhalla/src/valhalla_routing_engine.cpp
        adapters/valhalla/src/valhalla_parsing.cpp
        adapters/valhalla/src/valhalla_trace_request.cpp
        adapters/valhalla/src/valhalla_street_segment_mapper.cpp
)

if(ROUTING_PLATFORM_WITH_VALHALLA)
    target_sources(
        routing_valhalla_adapter
        PRIVATE
            adapters/valhalla/src/valhalla_edge_attributes.cpp
    )
endif()

target_include_directories(
    routing_valhalla_adapter
    PUBLIC
        ${CMAKE_CURRENT_SOURCE_DIR}/adapters/valhalla/include
)

target_link_libraries(
    routing_valhalla_adapter
    PUBLIC
        routing_core
)

if(ROUTING_PLATFORM_WITH_VALHALLA)
    target_link_directories(
        routing_valhalla_adapter
        PUBLIC
            ${VALHALLA_STATIC_LIBRARY_DIRS}
    )

    target_link_libraries(
        routing_valhalla_adapter
        PUBLIC
            PkgConfig::VALHALLA
            ${VALHALLA_STATIC_LIBRARIES}
            ${ROUTING_PLATFORM_GEOTIFF_LIBRARY}
    )

    target_compile_definitions(
        routing_valhalla_adapter
        PRIVATE
            ROUTING_PLATFORM_WITH_VALHALLA=1
    )
endif()

target_compile_features(
    routing_valhalla_adapter
    PUBLIC
        cxx_std_20
)

routing_platform_warnings(routing_valhalla_adapter)


# ---------------------------------------------------------------------------
# Tests
# ---------------------------------------------------------------------------

if(ROUTING_PLATFORM_BUILD_TESTS)
    enable_testing()

    add_executable(
        routing_core_smoke_test
        tests/smoke_test.cpp
    )

    target_link_libraries(
        routing_core_smoke_test
        PRIVATE
            routing_core
    )

    add_test(
        NAME routing_core_smoke_test
        COMMAND routing_core_smoke_test
    )


    add_executable(
        routing_cost_math_test
        tests/unit/foundation_smoke_test.cpp
    )

        add_executable(
        road_analysis_test
        tests/unit/road_analysis_test.cpp
    )

    target_link_libraries(
        road_analysis_test
        PRIVATE
            routing_core
    )

        add_executable(
        route_analysis_test
        tests/unit/route_analysis_test.cpp
    )

    target_link_libraries(
        route_analysis_test
        PRIVATE
            routing_core
    )

    add_test(
        NAME route_analysis_test
        COMMAND route_analysis_test
    )

    add_test(
        NAME road_analysis_test
        COMMAND road_analysis_test
    )

    target_link_libraries(
        routing_cost_math_test
        PRIVATE
            routing_core
    )

    add_test(
        NAME routing_cost_math_test
        COMMAND routing_cost_math_test
    )


    add_executable(
        routing_engine_contract_test
        tests/unit/routing_engine_contract_test.cpp
    )

    add_executable(
        valhalla_parsing_test
        tests/unit/valhalla_parsing_test.cpp
    )

    target_link_libraries(
        valhalla_parsing_test
        PRIVATE
            routing_valhalla_adapter
    )

    add_test(
        NAME valhalla_parsing_test
        COMMAND valhalla_parsing_test
    )


    if(ROUTING_PLATFORM_WITH_VALHALLA)
        add_executable(
            valhalla_edge_attributes_test
            tests/unit/valhalla_edge_attributes_test.cpp
        )

        target_link_libraries(
            valhalla_edge_attributes_test
            PRIVATE
                routing_valhalla_adapter
        )

        add_test(
            NAME valhalla_edge_attributes_test
            COMMAND valhalla_edge_attributes_test
        )
        add_executable(
            valhalla_speed_limit_semantics_test
            tests/unit/valhalla_speed_limit_semantics_test.cpp
        )

        target_link_libraries(
            valhalla_speed_limit_semantics_test
            PRIVATE
                routing_valhalla_adapter
        )

        add_test(
            NAME valhalla_speed_limit_semantics_test
            COMMAND valhalla_speed_limit_semantics_test
        )

    endif()

    add_executable(
        valhalla_street_segment_mapper_test
        tests/unit/valhalla_street_segment_mapper_test.cpp
    )

    target_link_libraries(
        valhalla_street_segment_mapper_test
        PRIVATE
            routing_valhalla_adapter
    )

    add_test(
        NAME valhalla_street_segment_mapper_test
        COMMAND valhalla_street_segment_mapper_test
    )


    target_link_libraries(
        routing_engine_contract_test
        PRIVATE
            routing_core
    )

    add_test(
        NAME routing_engine_contract_test
        COMMAND routing_engine_contract_test
    )

    add_executable(
        valhalla_trace_request_test
        tests/unit/valhalla_trace_request_test.cpp
    )

    target_link_libraries(
        valhalla_trace_request_test
        PRIVATE
            routing_valhalla_adapter
    )

    add_test(
        NAME valhalla_trace_request_test
        COMMAND valhalla_trace_request_test
    )

    if(NOT ROUTING_PLATFORM_WITH_VALHALLA)
        add_executable(
            valhalla_adapter_fallback_test
            tests/unit/valhalla_adapter_fallback_test.cpp
        )

        target_link_libraries(
            valhalla_adapter_fallback_test
            PRIVATE
                routing_valhalla_adapter
        )

        add_test(
            NAME valhalla_adapter_fallback_test
            COMMAND valhalla_adapter_fallback_test
        )
    endif()


    if(ROUTING_PLATFORM_WITH_VALHALLA)
        add_executable(
            valhalla_real_route_test
            tests/integration/valhalla_real_route_test.cpp
        )

        target_link_libraries(
            valhalla_real_route_test
            PRIVATE
                routing_valhalla_adapter
        )

        add_test(
            NAME valhalla_real_route_test
            COMMAND valhalla_real_route_test
        )

        set_tests_properties(
            valhalla_real_route_test
            PROPERTIES
                SKIP_RETURN_CODE 77
        )
    endif()
endif()

# ---------------------------------------------------------------------------
# Routing Intelligence
# ---------------------------------------------------------------------------

add_subdirectory(core/intelligence)

# ---------------------------------------------------------------------------
# Drive Sessions, Feedback and Evidence
# ---------------------------------------------------------------------------

add_subdirectory(core/drive)
