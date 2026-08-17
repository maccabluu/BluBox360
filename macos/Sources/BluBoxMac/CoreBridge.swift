import AppKit
import Foundation
import GameController
import Metal

struct MacLaunchSettings {
    let targetFPS: Int
    let graphicsPreset: String
    let renderScale: String
    let showFPS: Bool
    let smartHeatGuard: Bool
    let profileName: String
}

enum MacDataPaths {
    static func root() -> URL {
        let support = (try? FileManager.default.url(
            for: .applicationSupportDirectory,
            in: .userDomainMask,
            appropriateFor: nil,
            create: true
        )) ?? FileManager.default.homeDirectoryForCurrentUser
        return support.appendingPathComponent("BluBox 360", isDirectory: true)
    }

    static func core() -> URL { root().appendingPathComponent("Core", isDirectory: true) }
    static func saves() -> URL { root().appendingPathComponent("Saves", isDirectory: true) }
    static func profiles() -> URL { root().appendingPathComponent("Profiles", isDirectory: true) }
    static func shaderCache() -> URL { root().appendingPathComponent("ShaderCache", isDirectory: true) }
    static func logs() -> URL { root().appendingPathComponent("Logs", isDirectory: true) }

    static func gameSaveFolder(_ game: GameEntry) -> URL {
        saves().appendingPathComponent(game.id.uuidString, isDirectory: true)
    }

    static func prepare() {
        for folder in [root(), core(), saves(), profiles(), shaderCache(), logs()] {
            try? FileManager.default.createDirectory(
                at: folder,
                withIntermediateDirectories: true,
                attributes: nil
            )
        }
    }

    static func open(_ folder: URL) {
        try? FileManager.default.createDirectory(
            at: folder,
            withIntermediateDirectories: true,
            attributes: nil
        )
        NSWorkspace.shared.open(folder)
    }
}

@MainActor
final class MacCoreBridge: ObservableObject {
    @Published private(set) var coreURL: URL?
    @Published private(set) var isRunning = false
    @Published private(set) var statusText = "Native core bootstrap not connected"
    @Published private(set) var consoleText = ""
    @Published private(set) var currentGameName: String?

    private var process: Process?

    init() {
        MacDataPaths.prepare()
        refresh()
    }

    var isReady: Bool {
        coreURL != nil && !isRunning
    }

    func refresh() {
        guard !isRunning else { return }
        coreURL = locateCore()
        if let coreURL {
            statusText = "Native core bootstrap found: \(coreURL.lastPathComponent)"
        } else {
            statusText = "Native core bootstrap not connected"
        }
    }

    func launch(game: GameEntry, settings: MacLaunchSettings) {
        refresh()
        guard let executable = coreURL else {
            statusText = "The macOS native core bootstrap is not bundled."
            return
        }
        guard !isRunning else { return }

        MacDataPaths.prepare()
        let saveFolder = MacDataPaths.gameSaveFolder(game)
        try? FileManager.default.createDirectory(
            at: saveFolder,
            withIntermediateDirectories: true,
            attributes: nil
        )

        let task = Process()
        let output = Pipe()
        task.executableURL = executable
        task.arguments = [game.path]
        task.standardOutput = output
        task.standardError = output

        var environment = ProcessInfo.processInfo.environment
        environment["BLUBOX360_PLATFORM"] = "macOS"
        environment["BLUBOX360_VERSION"] = "2.3"
        environment["BLUBOX360_TARGET_FPS"] = String(settings.targetFPS)
        environment["BLUBOX360_GRAPHICS_PRESET"] = settings.graphicsPreset
        environment["BLUBOX360_RENDER_SCALE"] = settings.renderScale
        environment["BLUBOX360_SHOW_FPS"] = settings.showFPS ? "1" : "0"
        environment["BLUBOX360_SMART_HEAT_GUARD"] = settings.smartHeatGuard ? "1" : "0"
        environment["BLUBOX360_PROFILE"] = settings.profileName
        environment["BLUBOX360_SAVE_PATH"] = saveFolder.path
        environment["BLUBOX360_SHADER_CACHE"] = MacDataPaths.shaderCache().path
        environment["BLUBOX360_LOG_PATH"] = MacDataPaths.logs().path
        task.environment = environment

        consoleText = "BluBox 360 macOS 2.3\nBootstrapping \(game.name)…\n"
        statusText = "Testing native core with \(game.name)…"
        currentGameName = game.name

        do {
            try task.run()
            process = task
            isRunning = true
            statusText = "Native core bootstrap running"

            let bridge = self
            output.fileHandleForReading.readabilityHandler = { handle in
                let data = handle.availableData
                guard !data.isEmpty, let text = String(data: data, encoding: .utf8) else { return }
                Task { @MainActor in
                    bridge.appendConsole(text)
                }
            }

            task.terminationHandler = { finished in
                let exitCode = finished.terminationStatus
                output.fileHandleForReading.readabilityHandler = nil
                Task { @MainActor in
                    bridge.finishSession(exitCode: exitCode)
                }
            }
        } catch {
            process = nil
            isRunning = false
            currentGameName = nil
            statusText = "Core launch failed: \(error.localizedDescription)"
        }
    }

    func stop() {
        guard let process, process.isRunning else { return }
        process.terminate()
        statusText = "Stopping native core…"
    }

    func clearConsole() {
        consoleText = ""
    }

    func openCoreFolder() { MacDataPaths.open(MacDataPaths.core()) }
    func openSavesFolder() { MacDataPaths.open(MacDataPaths.saves()) }
    func openShaderCacheFolder() { MacDataPaths.open(MacDataPaths.shaderCache()) }
    func openLogsFolder() { MacDataPaths.open(MacDataPaths.logs()) }
    func openDataFolder() { MacDataPaths.open(MacDataPaths.root()) }
    func openSaveFolder(for game: GameEntry) { MacDataPaths.open(MacDataPaths.gameSaveFolder(game)) }

    private func appendConsole(_ text: String) {
        consoleText.append(text)
    }

    private func finishSession(exitCode: Int32) {
        isRunning = false
        process = nil
        currentGameName = nil

        switch exitCode {
        case 0:
            statusText = "Native core self-test finished"
        case 64:
            statusText = "Core bootstrap passed. PowerPC CPU and GPU execution are next."
        case 20:
            statusText = "Game file could not be found by the native core."
        case 21:
            statusText = "Unsupported game file type."
        default:
            statusText = "Native core exited with code \(exitCode)"
        }
    }

    private func locateCore() -> URL? {
        let fileManager = FileManager.default
        var candidates: [URL] = []

        if let override = ProcessInfo.processInfo.environment["BLUBOX360_MAC_CORE"],
           !override.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            candidates.append(URL(fileURLWithPath: override))
        }
        if let resourceURL = Bundle.main.resourceURL {
            candidates.append(resourceURL.appendingPathComponent("blubox360-core"))
        }
        candidates.append(MacDataPaths.core().appendingPathComponent("blubox360-core"))

        for candidate in candidates {
            if fileManager.isExecutableFile(atPath: candidate.path) {
                return candidate
            }
        }
        return nil
    }
}

enum MacDiagnostics {
    static var architecture: String {
        #if arch(arm64)
        return "Apple Silicon (arm64)"
        #elseif arch(x86_64)
        return "Intel (x86_64)"
        #else
        return "Unknown"
        #endif
    }

    static var metalDevice: String {
        MTLCreateSystemDefaultDevice()?.name ?? "No Metal device found"
    }

    static var controllerSummary: String {
        let count = GCController.controllers().count
        return count == 1 ? "1 controller connected" : "\(count) controllers connected"
    }

    static var controllerNames: String {
        let names = GCController.controllers().map { $0.vendorName ?? "Game Controller" }
        return names.isEmpty ? "None" : names.joined(separator: ", ")
    }

    static var thermalState: String {
        switch ProcessInfo.processInfo.thermalState {
        case .nominal: return "Nominal"
        case .fair: return "Fair"
        case .serious: return "Serious"
        case .critical: return "Critical"
        @unknown default: return "Unknown"
        }
    }

    static var shouldUseHeatSafePreset: Bool {
        ProcessInfo.processInfo.thermalState == .serious ||
        ProcessInfo.processInfo.thermalState == .critical ||
        ProcessInfo.processInfo.isLowPowerModeEnabled
    }

    static var lowPowerMode: String {
        ProcessInfo.processInfo.isLowPowerModeEnabled ? "On" : "Off"
    }

    static var memory: String {
        let gib = Double(ProcessInfo.processInfo.physicalMemory) / 1_073_741_824.0
        return String(format: "%.1f GB", gib)
    }

    static var macOSVersion: String {
        ProcessInfo.processInfo.operatingSystemVersionString
    }
}
