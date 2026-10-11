target_sources(
    routing_core
    PRIVATE
        ${PROJECT_SOURCE_DIR}/core/src/evaluation/route_evaluation.cpp
        ${PROJECT_SOURCE_DIR}/core/src/evaluation/candidate_comparison.cpp
)

if(ROUTING_PLATFORM_BUILD_TESTS)
    function(add_routing_evaluation_test target source)
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

    add_routing_evaluation_test(
        routing_route_evaluation_test
        tests/unit/route_evaluation_test.cpp
    )

    add_routing_evaluation_test(
        routing_candidate_comparison_test
        tests/unit/candidate_comparison_test.cpp
    )

    if(ROUTING_PLATFORM_WITH_VALHALLA)
        add_executable(
            routing_valhalla_route_evaluation_test
            ${PROJECT_SOURCE_DIR}/tests/integration/valhalla_route_evaluation_test.cpp
        )

        target_link_libraries(
            routing_valhalla_route_evaluation_test
            PRIVATE
                routing_valhalla_adapter
        )

        add_test(
            NAME routing_valhalla_route_evaluation_test
            COMMAND routing_valhalla_route_evaluation_test
        )

        set_tests_properties(
            routing_valhalla_route_evaluation_test
            PROPERTIES
                SKIP_RETURN_CODE 77
        )
    endif()
endif()
