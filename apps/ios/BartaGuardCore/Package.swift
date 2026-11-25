// swift-tools-version:5.9
import PackageDescription

let package = Package(
    name: "BartaGuardCore",
    platforms: [.iOS(.v17), .macOS(.v14)],
    products: [.library(name: "BartaGuardCore", targets: ["BartaGuardCore"])],
    targets: [
        .target(name: "BartaGuardCore", resources: [.process("Resources")]),
        .testTarget(name: "BartaGuardCoreTests", dependencies: ["BartaGuardCore"], resources: [.process("ondevice_golden.json")]),
    ]
)
