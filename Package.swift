// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "HerMorseCore",
    platforms: [.iOS(.v16), .macOS(.v13)],
    products: [.library(name: "HerMorseCore", targets: ["HerMorseCore"])],
    targets: [
        .target(name: "HerMorseCore"),
        .testTarget(name: "HerMorseCoreTests", dependencies: ["HerMorseCore"])
    ]
)
