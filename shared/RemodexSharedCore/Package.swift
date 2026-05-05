// swift-tools-version: 6.0

import PackageDescription

let package = Package(
    name: "RemodexSharedCore",
    products: [
        .library(
            name: "RemodexSharedCore",
            targets: ["RemodexSharedCore"]
        ),
    ],
    targets: [
        .target(
            name: "RemodexSharedCore"
        ),
        .testTarget(
            name: "RemodexSharedCoreTests",
            dependencies: ["RemodexSharedCore"]
        ),
    ]
)
