target_sources(
    routing_core
    PRIVATE
        ${PROJECT_SOURCE_DIR}/core/src/candidates/candidate_family_plan.cpp
        ${PROJECT_SOURCE_DIR}/core/src/candidates/family_representative.cpp
        ${PROJECT_SOURCE_DIR}/core/src/candidates/candidate_portfolio.cpp
        ${PROJECT_SOURCE_DIR}/core/src/candidates/rule_candidate_profile.cpp
        ${PROJECT_SOURCE_DIR}/core/src/candidates/route_identity.cpp
        ${PROJECT_SOURCE_DIR}/core/src/candidates/candidate_orchestrator.cpp
)

if(ROUTING_PLATFORM_BUILD_TESTS)
    function(add_routing_candidate_test target source)
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

    add_routing_candidate_test(
        routing_candidate_family_plan_test
        tests/unit/candidate_family_plan_test.cpp
    )

    add_routing_candidate_test(
        routing_family_representative_test
        tests/unit/family_representative_test.cpp
    )

    add_routing_candidate_test(
        routing_candidate_portfolio_test
        tests/unit/candidate_portfolio_test.cpp
    )

    add_routing_candidate_test(
        routing_rule_candidate_profile_test
        tests/unit/rule_candidate_profile_test.cpp
    )

    add_routing_candidate_test(
        routing_route_identity_test
        tests/unit/route_identity_test.cpp
    )

    add_routing_candidate_test(
        routing_candidate_orchestrator_test
        tests/unit/candidate_orchestrator_test.cpp
    )

    add_executable(
        valhalla_candidate_costing_test
        ${PROJECT_SOURCE_DIR}/tests/unit/valhalla_candidate_costing_test.cpp
    )

    target_link_libraries(
        valhalla_candidate_costing_test
        PRIVATE
            routing_valhalla_adapter
            routing_core
    )

    add_test(
        NAME valhalla_candidate_costing_test
        COMMAND valhalla_candidate_costing_test
    )

    if(ROUTING_PLATFORM_WITH_VALHALLA)
        add_executable(
            valhalla_candidate_family_generation_test
            ${PROJECT_SOURCE_DIR}/tests/integration/valhalla_candidate_family_generation_test.cpp
        )

        target_link_libraries(
            valhalla_candidate_family_generation_test
            PRIVATE
                routing_valhalla_adapter
                routing_core
        )

        add_test(
            NAME valhalla_candidate_family_generation_test
            COMMAND valhalla_candidate_family_generation_test
        )

        set_tests_properties(
            valhalla_candidate_family_generation_test
            PROPERTIES
                SKIP_RETURN_CODE 77
        )

        add_executable(
            valhalla_rule_candidate_orchestrator_test
            ${PROJECT_SOURCE_DIR}/tests/integration/valhalla_rule_candidate_orchestrator_test.cpp
        )

        target_link_libraries(
            valhalla_rule_candidate_orchestrator_test
            PRIVATE
                routing_valhalla_adapter
                routing_core
        )

        add_test(
            NAME valhalla_rule_candidate_orchestrator_test
            COMMAND valhalla_rule_candidate_orchestrator_test
        )

        set_tests_properties(
            valhalla_rule_candidate_orchestrator_test
            PROPERTIES
                SKIP_RETURN_CODE 77
        )
    endif()
endif()
