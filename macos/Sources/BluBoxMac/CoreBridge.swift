import AppKit
import Foundation
import GameController
import Metal

struct MacLaunchSettings {
    let targetFPS: Int
    let graphicsPreset: String
    let showFPS: Bool
}

@MainActor
final class MacCoreBridge: ObservableObject {
    @Published private(set) var coreURL: URL?
    @Published private(set) var isRunning = false
    @Published private(set) var statusText = "Native Xbox 360 core not connected"
    @Published private(set) var consoleText = ""

    private var process: Process?

    init() {
        refresh()
    }

    var isReady: Bool {
        coreURL != nil && !isRunning
    }

    func refresh() {
        guard !isRunning else { return }
        coreURL = locateCore()
        if let coreURL {
            statusText = "Native core found: \(coreURL.lastPathComponent)"
        } else {
            statusText = "Native Xbox 360 core not connected"
        }
    }

    func launch(game: GameEntry, settings: MacLaunchSettings) {
        refresh()
        guard let executable = coreURL else {
            statusText = "The macOS emulator core still needs to be ported and bundled."
            return
        }
        guard !isRunning else { return }

        let task = Process()
        let output = Pipe()
        task.executableURL = executable
        task.arguments = [game.path]
        task.standardOutput = output
        task.standardError = output

        var environment = ProcessInfo.processInfo.environment
        environment["BLUBOX360_PLATFORM"] = "macOS"
        environment["BLUBOX360_TARGET_FPS"] = String(settings.targetFPS)
        environment["BLUBOX360_GRAPHICS_PRESET"] = settings.graphicsPreset
        environment["BLUBOX360_SHOW_FPS"] = settings.showFPS ? "1" : "0"
        task.environment = environment

        consoleText = "Launching \(game.name)…\n"
        statusText = "Starting \(game.name)…"

        do {
            try task.run()
            process = task
            isRunning = true
            statusText = "\(game.name) is running"

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
            statusText = "Core launch failed: \(error.localizedDescription)"
        }
    }

    func stop() {
        guard let process, process.isRunning else { return }
        process.terminate()
        statusText = "Stopping game…"
    }

    func openCoreFolder() {
        let folder = applicationSupportCoreDirectory()
        try? FileManager.default.createDirectory(
            at: folder,
            withIntermediateDirectories: true,
            attributes: nil
        )
        NSWorkspace.shared.open(folder)
    }

    private func appendConsole(_ text: String) {
        consoleText.append(text)
    }

    private func finishSession(exitCode: Int32) {
        isRunning = false
        process = nil
        statusText = exitCode == 0
            ? "Game session finished"
            : "Core exited with code \(exitCode)"
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
        candidates.append(applicationSupportCoreDirectory().appendingPathComponent("blubox360-core"))

        for candidate in candidates {
            if fileManager.isExecutableFile(atPath: candidate.path) {
                return candidate
            }
        }
        return nil
    }

    private func applicationSupportCoreDirectory() -> URL {
        let support = (try? FileManager.default.url(
            for: .applicationSupportDirectory,
            in: .userDomainMask,
            appropriateFor: nil,
            create: true
        )) ?? FileManager.default.homeDirectoryForCurrentUser
        return support
            .appendingPathComponent("BluBox 360", isDirectory: true)
            .appendingPathComponent("Core", isDirectory: true)
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

    static var thermalState: String {
        switch ProcessInfo.processInfo.thermalState {
        case .nominal: return "Nominal"
        case .fair: return "Fair"
        case .serious: return "Serious"
        case .critical: return "Critical"
        @unknown default: return "Unknown"
        }
    }

    static var lowPowerMode: String {
        ProcessInfo.processInfo.isLowPowerModeEnabled ? "On" : "Off"
    }

    static var memory: String {
        let gib = Double(ProcessInfo.processInfo.physicalMemory) / 1_073_741_824.0
        return String(format: "%.1f GB", gib)
    }
}
