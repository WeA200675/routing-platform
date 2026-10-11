if(ROUTING_PLATFORM_WITH_VALHALLA)
    add_executable(
        routing_route_lab
        ${PROJECT_SOURCE_DIR}/tools/route_lab/main.cpp
    )

    target_link_libraries(
        routing_route_lab
        PRIVATE
            routing_valhalla_adapter
            routing_core
    )

    target_compile_features(
        routing_route_lab
        PRIVATE
            cxx_std_20
    )

    routing_platform_warnings(
        routing_route_lab
    )
endif()
