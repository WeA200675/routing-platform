target_sources(
    routing_core
    PRIVATE
        ${PROJECT_SOURCE_DIR}/core/src/testing/route_metric.cpp
        ${PROJECT_SOURCE_DIR}/core/src/testing/routing_scenario.cpp
        ${PROJECT_SOURCE_DIR}/core/src/testing/scenario_runner.cpp
        ${PROJECT_SOURCE_DIR}/core/src/testing/route_decision_report.cpp
)

if(ROUTING_PLATFORM_BUILD_TESTS)
    function(add_routing_testing_test target source)
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

    add_routing_testing_test(
        routing_route_metric_test
        tests/unit/route_metric_test.cpp
    )

    add_routing_testing_test(
        routing_scenario_test
        tests/unit/routing_scenario_test.cpp
    )

    add_routing_testing_test(
        routing_scenario_runner_test
        tests/unit/scenario_runner_test.cpp
    )

    if(ROUTING_PLATFORM_WITH_VALHALLA)
        add_executable(
            valhalla_routing_scenario_test
            ${PROJECT_SOURCE_DIR}/tests/integration/valhalla_routing_scenario_test.cpp
        )

        target_link_libraries(
            valhalla_routing_scenario_test
            PRIVATE
                routing_valhalla_adapter
                routing_core
        )

        add_test(
            NAME valhalla_routing_scenario_test
            COMMAND valhalla_routing_scenario_test
        )

        set_tests_properties(
            valhalla_routing_scenario_test
            PROPERTIES
                SKIP_RETURN_CODE 77
        )
    endif()
endif()
