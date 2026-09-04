target_sources(
    routing_core
    PRIVATE
        ${PROJECT_SOURCE_DIR}/core/src/navigation/navigation_runtime.cpp
)

if(ROUTING_PLATFORM_BUILD_TESTS)
    function(add_routing_navigation_test target source)
        add_executable(
            ${target}
            ${PROJECT_SOURCE_DIR}/${source}
        )

        target_link_libraries(
            ${target}
            PRIVATE
                routing_core
        )

        add_test(
            NAME ${target}
            COMMAND ${target}
        )
    endfunction()


    add_routing_navigation_test(
        routing_navigation_runtime_test
        tests/unit/navigation_runtime_test.cpp
    )

    add_routing_navigation_test(
        routing_navigation_runtime_boundary_test
        tests/unit/navigation_runtime_boundary_test.cpp
    )

    add_routing_navigation_test(
        routing_navigation_runtime_pipeline_test
        tests/unit/navigation_runtime_pipeline_test.cpp
    )


    if(ROUTING_PLATFORM_WITH_VALHALLA)
        add_executable(
            routing_navigation_runtime_valhalla_test
            ${PROJECT_SOURCE_DIR}/tests/integration/navigation_runtime_valhalla_test.cpp
        )

        target_link_libraries(
            routing_navigation_runtime_valhalla_test
            PRIVATE
                routing_valhalla_adapter
                routing_core
        )

        add_test(
            NAME routing_navigation_runtime_valhalla_test
            COMMAND routing_navigation_runtime_valhalla_test
        )

        set_tests_properties(
            routing_navigation_runtime_valhalla_test
            PROPERTIES
                SKIP_RETURN_CODE 77
        )
    endif()
endif()
