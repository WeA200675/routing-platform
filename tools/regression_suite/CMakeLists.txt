if(ROUTING_PLATFORM_WITH_VALHALLA)
    add_executable(
        routing_regression_suite
        ${PROJECT_SOURCE_DIR}/tools/regression_suite/main.cpp
    )

    target_link_libraries(
        routing_regression_suite
        PRIVATE
            routing_valhalla_adapter
            routing_core
    )

    target_compile_features(
        routing_regression_suite
        PRIVATE
            cxx_std_20
    )

    routing_platform_warnings(
        routing_regression_suite
    )
endif()
