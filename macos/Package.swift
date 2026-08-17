// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "BluBoxMac",
    platforms: [
        .macOS(.v13)
    ],
    products: [
        .executable(name: "BluBoxMac", targets: ["BluBoxMac"])
    ],
    targets: [
        .executableTarget(
            name: "BluBoxMac",
            path: "Sources/BluBoxMac"
        )
    ]
)
