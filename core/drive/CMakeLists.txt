target_sources(
    routing_core
    PRIVATE
        ${PROJECT_SOURCE_DIR}/core/src/drive/drive_session.cpp
        ${PROJECT_SOURCE_DIR}/core/src/drive/replay_semantics.cpp
        ${PROJECT_SOURCE_DIR}/core/src/drive/routing_snapshot.cpp
        ${PROJECT_SOURCE_DIR}/core/src/drive/drive_evidence.cpp
        ${PROJECT_SOURCE_DIR}/core/src/drive/regression_candidate.cpp
)

if(ROUTING_PLATFORM_BUILD_TESTS)
    function(add_routing_drive_test target source)
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

    add_routing_drive_test(
        routing_drive_session_test
        tests/unit/drive_session_test.cpp
    )

    add_routing_drive_test(
        routing_drive_evidence_test
        tests/unit/drive_evidence_test.cpp
    )

    add_routing_drive_test(
        routing_regression_candidate_test
        tests/unit/regression_candidate_test.cpp
    )

    add_routing_drive_test(
        routing_drive_validation_test
        tests/unit/drive_validation_test.cpp
    )

    add_routing_drive_test(
        routing_snapshot_test
        tests/unit/routing_snapshot_test.cpp
    )

    add_routing_drive_test(
        routing_replay_semantics_test
        tests/unit/replay_semantics_test.cpp
    )

    add_routing_drive_test(
        routing_drive_replay_snapshot_test
        tests/unit/drive_replay_snapshot_test.cpp
    )

    if(ROUTING_PLATFORM_WITH_VALHALLA)
        add_executable(
            routing_valhalla_drive_evidence_test
            ${PROJECT_SOURCE_DIR}/tests/integration/valhalla_drive_evidence_test.cpp
        )

        target_link_libraries(
            routing_valhalla_drive_evidence_test
            PRIVATE
                routing_core
                routing_valhalla_adapter
        )

        add_test(
            NAME routing_valhalla_drive_evidence_test
            COMMAND routing_valhalla_drive_evidence_test
        )

        set_tests_properties(
            routing_valhalla_drive_evidence_test
            PROPERTIES
                SKIP_RETURN_CODE 77
        )
    endif()
endif()
