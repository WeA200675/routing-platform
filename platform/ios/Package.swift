// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "RoutingPlatformIOS",
    platforms: [
        .iOS(.v16),
        .macOS(.v13),
    ],
    products: [
        .library(name: "RoutingPlatformIOS", targets: ["RoutingPlatformIOS"]),
    ],
    targets: [
        .target(
            name: "RoutingPlatformIOS",
            path: "Sources/RoutingPlatformIOS"
        ),
        .testTarget(
            name: "RoutingPlatformIOSTests",
            dependencies: ["RoutingPlatformIOS"],
            path: "Tests/RoutingPlatformIOSTests"
        ),
    ]
)
