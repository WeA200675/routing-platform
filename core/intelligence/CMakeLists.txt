target_sources(
    routing_core
    PRIVATE
        ${PROJECT_SOURCE_DIR}/core/src/intelligence/ai_policy.cpp
        ${PROJECT_SOURCE_DIR}/core/src/intelligence/resource_governor.cpp
        ${PROJECT_SOURCE_DIR}/core/src/intelligence/intelligence_job_queue.cpp
        ${PROJECT_SOURCE_DIR}/core/src/intelligence/learning_memory.cpp
)

if(ROUTING_PLATFORM_BUILD_TESTS)
    function(add_routing_intelligence_test target source)
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

    add_routing_intelligence_test(
        routing_ai_policy_test
        tests/unit/ai_policy_test.cpp
    )

    add_routing_intelligence_test(
        routing_resource_governor_test
        tests/unit/resource_governor_test.cpp
    )

    add_routing_intelligence_test(
        routing_intelligence_job_queue_test
        tests/unit/intelligence_job_queue_test.cpp
    )

    add_routing_intelligence_test(
        routing_learning_memory_test
        tests/unit/learning_memory_test.cpp
    )
endif()
