target_sources(
    routing_core
    PRIVATE
        ${PROJECT_SOURCE_DIR}/core/src/intelligence/ai_policy.cpp
        ${PROJECT_SOURCE_DIR}/core/src/intelligence/resource_governor.cpp
        ${PROJECT_SOURCE_DIR}/core/src/intelligence/intelligence_job_queue.cpp
        ${PROJECT_SOURCE_DIR}/core/src/intelligence/diagnostic_investigation_job.cpp
        ${PROJECT_SOURCE_DIR}/core/src/intelligence/cluster_problem_analysis.cpp
        ${PROJECT_SOURCE_DIR}/core/src/intelligence/cluster_problem_review.cpp
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
        routing_diagnostic_investigation_job_test
        tests/unit/diagnostic_investigation_job_test.cpp
    )

    add_routing_intelligence_test(
        routing_diagnostic_investigation_pipeline_test
        tests/unit/diagnostic_investigation_pipeline_test.cpp
    )

    add_routing_intelligence_test(
        routing_cluster_problem_analysis_test
        tests/unit/cluster_problem_analysis_test.cpp
    )

    add_routing_intelligence_test(
        routing_cluster_problem_analysis_revision_test
        tests/unit/cluster_problem_analysis_revision_test.cpp
    )

    add_routing_intelligence_test(
        routing_cluster_problem_analysis_pipeline_test
        tests/unit/cluster_problem_analysis_pipeline_test.cpp
    )

    add_routing_intelligence_test(
        routing_cluster_problem_review_test
        tests/unit/cluster_problem_review_test.cpp
    )

    add_routing_intelligence_test(
        routing_cluster_problem_refresh_workflow_test
        tests/unit/cluster_problem_refresh_workflow_test.cpp
    )

    add_routing_intelligence_test(
        routing_cluster_problem_review_boundary_test
        tests/unit/cluster_problem_review_boundary_test.cpp
    )

    add_routing_intelligence_test(
        routing_learning_memory_test
        tests/unit/learning_memory_test.cpp
    )
endif()

# BEGIN evidence-learning-intelligence
target_sources(
    routing_core
    PRIVATE
        ${PROJECT_SOURCE_DIR}/core/src/intelligence/preference_hypothesis.cpp
        ${PROJECT_SOURCE_DIR}/core/src/intelligence/learning_gate.cpp
)

if(ROUTING_PLATFORM_BUILD_TESTS)
    add_routing_intelligence_test(
        routing_preference_hypothesis_test
        tests/unit/preference_hypothesis_test.cpp
    )

    add_routing_intelligence_test(
        routing_learning_gate_test
        tests/unit/learning_gate_test.cpp
    )
endif()
# END evidence-learning-intelligence

# BEGIN post-drive-shadow-intelligence
target_sources(
    routing_core
    PRIVATE
        ${PROJECT_SOURCE_DIR}/core/src/intelligence/question_candidate.cpp
        ${PROJECT_SOURCE_DIR}/core/src/intelligence/shadow_evaluation.cpp
)

if(ROUTING_PLATFORM_BUILD_TESTS)
    add_routing_intelligence_test(
        routing_question_candidate_test
        tests/unit/question_candidate_test.cpp
    )

    add_routing_intelligence_test(
        routing_shadow_evaluation_test
        tests/unit/shadow_evaluation_test.cpp
    )
endif()
# END post-drive-shadow-intelligence

