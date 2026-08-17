import AppKit
import Foundation
import GameController
import Metal

struct MacLaunchSettings {
    let targetFPS: Int
    let graphicsPreset: String
    let renderScale: Int
    let showFPS: Bool
    let lowHeatMode: Bool
    let smartHeatGuard: Bool
    let profileName: String
    let engineMode: String
    let upscaler: String
    let antialiasing: String
    let anisotropic: Int
    let aspectMode: String
    let interlaced: Bool
    let asyncShaders: Bool
    let skipShaderDraws: Bool
    let pipelinePreload: Bool
    let shaderWorkers: Int
    let rumble: Bool
    let applyPatches: Bool
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
    static func patches() -> URL { root().appendingPathComponent("Patches", isDirectory: true) }
    static func backups() -> URL { root().appendingPathComponent("Backups", isDirectory: true) }

    static func gameSaveFolder(_ game: GameEntry) -> URL {
        saves().appendingPathComponent(game.id.uuidString, isDirectory: true)
    }

    static func prepare() {
        for folder in [root(), core(), saves(), profiles(), shaderCache(), logs(), patches(), backups()] {
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

private struct EngineChoice {
    let executable: URL
    let name: String
    let architecture: String
}

@MainActor
final class MacCoreBridge: ObservableObject {
    @Published private(set) var coreURL: URL?
    @Published private(set) var isRunning = false
    @Published private(set) var statusText = "Checking macOS emulator engine…"
    @Published private(set) var consoleText = ""
    @Published private(set) var currentGameName: String?
    @Published private(set) var bundledEngineSummary = "No playable engine detected"
    @Published private(set) var updateStatus = "Mac preview update checks are ready"
    @Published private(set) var storageStatus = ""

    private var process: Process?

    init() {
        MacDataPaths.prepare()
        installBundledPatchesIfNeeded()
        refresh()
    }

    var isReady: Bool {
        playableEngineExists && !isRunning
    }

    var playableEngineExists: Bool {
        bundledEngineApp(architecture: "arm64") != nil || bundledEngineApp(architecture: "x86_64") != nil
    }

    func refresh() {
        guard !isRunning else { return }
        coreURL = locateBootstrapCore()
        let hasArm = bundledEngineApp(architecture: "arm64") != nil
        let hasX64 = bundledEngineApp(architecture: "x86_64") != nil
        if hasArm && hasX64 {
            bundledEngineSummary = "Xenia-Edge Metal engines: arm64 + x86_64"
            statusText = "Experimental Xbox 360 Metal engine ready"
        } else if hasArm {
            bundledEngineSummary = "Xenia-Edge Metal engine: arm64"
            statusText = "Native Apple Silicon Xbox 360 engine ready"
        } else if hasX64 {
            bundledEngineSummary = "Xenia-Edge Metal engine: x86_64"
            statusText = "x86_64 Xbox 360 engine ready"
        } else if coreURL != nil {
            bundledEngineSummary = "Diagnostic bootstrap only"
            statusText = "Playable Xbox 360 engine is not bundled"
        } else {
            bundledEngineSummary = "No engine detected"
            statusText = "No macOS emulator engine detected"
        }
    }

    func launch(game: GameEntry, settings: MacLaunchSettings) {
        guard !isRunning else { return }
        refresh()

        guard let engine = selectEngine(mode: settings.engineMode) else {
            statusText = "No compatible playable Mac engine was found."
            return
        }

        MacDataPaths.prepare()
        let saveFolder = MacDataPaths.gameSaveFolder(game)
        try? FileManager.default.createDirectory(
            at: saveFolder,
            withIntermediateDirectories: true,
            attributes: nil
        )

        var effective = settings
        if settings.lowHeatMode || (settings.smartHeatGuard && MacDiagnostics.shouldUseHeatSafePreset) {
            effective = MacLaunchSettings(
                targetFPS: min(settings.targetFPS, 30),
                graphicsPreset: "Performance",
                renderScale: 1,
                showFPS: settings.showFPS,
                lowHeatMode: settings.lowHeatMode,
                smartHeatGuard: settings.smartHeatGuard,
                profileName: settings.profileName,
                engineMode: settings.engineMode,
                upscaler: "none",
                antialiasing: "off",
                anisotropic: -1,
                aspectMode: settings.aspectMode,
                interlaced: settings.interlaced,
                asyncShaders: true,
                skipShaderDraws: true,
                pipelinePreload: false,
                shaderWorkers: 1,
                rumble: settings.rumble,
                applyPatches: settings.applyPatches
            )
        }

        let task = Process()
        let output = Pipe()
        task.executableURL = engine.executable
        task.arguments = xeniaArguments(game: game, settings: effective)
        task.currentDirectoryURL = MacDataPaths.root()
        task.standardOutput = output
        task.standardError = output

        var environment = ProcessInfo.processInfo.environment
        environment["BLUBOX360_PLATFORM"] = "macOS"
        environment["BLUBOX360_VERSION"] = "3.0"
        environment["BLUBOX360_ENGINE"] = engine.architecture
        environment["BLUBOX360_TARGET_FPS"] = String(effective.targetFPS)
        environment["BLUBOX360_GRAPHICS_PRESET"] = effective.graphicsPreset
        environment["BLUBOX360_RENDER_SCALE"] = String(effective.renderScale)
        environment["BLUBOX360_PROFILE"] = effective.profileName
        environment["BLUBOX360_SAVE_PATH"] = saveFolder.path
        environment["BLUBOX360_SHADER_CACHE"] = MacDataPaths.shaderCache().path
        environment["BLUBOX360_LOG_PATH"] = MacDataPaths.logs().path
        environment["BLUBOX360_PATCH_PATH"] = MacDataPaths.patches().path
        task.environment = environment

        consoleText = "BluBox 360 macOS 3.0\nEngine: \(engine.name) [\(engine.architecture)]\nLaunching \(game.name)…\n"
        statusText = "Starting \(game.name) with \(engine.architecture) Metal engine…"
        currentGameName = game.name

        do {
            try task.run()
            process = task
            isRunning = true
            statusText = "\(game.name) is running"

            let bridge = self
            output.fileHandleForReading.readabilityHandler = { handle in
                let data = handle.availableData
                guard !data.isEmpty, let text = String(data: data, encoding: .utf8) else { return }
                Task { @MainActor in bridge.appendConsole(text) }
            }

            task.terminationHandler = { finished in
                let exitCode = finished.terminationStatus
                output.fileHandleForReading.readabilityHandler = nil
                Task { @MainActor in bridge.finishSession(exitCode: exitCode) }
            }
        } catch {
            process = nil
            isRunning = false
            currentGameName = nil
            statusText = "Engine launch failed: \(error.localizedDescription)"
            appendConsole("Launch error: \(error.localizedDescription)\n")
        }
    }

    func runBootstrap(game: GameEntry, settings: MacLaunchSettings) {
        guard let executable = locateBootstrapCore(), !isRunning else {
            statusText = "Diagnostic bootstrap is unavailable."
            return
        }
        let task = Process()
        let output = Pipe()
        task.executableURL = executable
        task.arguments = [game.path]
        task.standardOutput = output
        task.standardError = output
        task.environment = ProcessInfo.processInfo.environment.merging([
            "BLUBOX360_PROFILE": settings.profileName,
            "BLUBOX360_TARGET_FPS": String(settings.targetFPS),
            "BLUBOX360_GRAPHICS_PRESET": settings.graphicsPreset,
            "BLUBOX360_RENDER_SCALE": String(settings.renderScale),
            "BLUBOX360_SAVE_PATH": MacDataPaths.gameSaveFolder(game).path,
            "BLUBOX360_SHADER_CACHE": MacDataPaths.shaderCache().path
        ]) { _, new in new }
        consoleText = "Running BluBox diagnostic core with \(game.name)…\n"
        do {
            try task.run()
            process = task
            isRunning = true
            currentGameName = game.name
            output.fileHandleForReading.readabilityHandler = { [weak self] handle in
                let data = handle.availableData
                guard !data.isEmpty, let text = String(data: data, encoding: .utf8) else { return }
                Task { @MainActor in self?.appendConsole(text) }
            }
            task.terminationHandler = { [weak self] finished in
                output.fileHandleForReading.readabilityHandler = nil
                Task { @MainActor in self?.finishSession(exitCode: finished.terminationStatus) }
            }
        } catch {
            statusText = "Diagnostic core failed: \(error.localizedDescription)"
        }
    }

    func stop() {
        guard let process, process.isRunning else { return }
        process.terminate()
        statusText = "Stopping game…"
    }

    func clearConsole() { consoleText = "" }

    func clearShaderCache() {
        guard !isRunning else {
            storageStatus = "Close the running game before clearing shader cache."
            return
        }
        let folder = MacDataPaths.shaderCache()
        do {
            if FileManager.default.fileExists(atPath: folder.path) {
                try FileManager.default.removeItem(at: folder)
            }
            try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
            storageStatus = "Shader cache cleared."
        } catch {
            storageStatus = "Shader cache could not be cleared: \(error.localizedDescription)"
        }
    }

    func createBackup() {
        guard !isRunning else {
            storageStatus = "Close the running game before creating a backup."
            return
        }
        MacDataPaths.prepare()
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd_HH-mm-ss"
        let destination = MacDataPaths.backups().appendingPathComponent("Backup_\(formatter.string(from: Date()))", isDirectory: true)
        do {
            try FileManager.default.createDirectory(at: destination, withIntermediateDirectories: true)
            for source in [MacDataPaths.saves(), MacDataPaths.profiles(), MacDataPaths.patches()] {
                let target = destination.appendingPathComponent(source.lastPathComponent, isDirectory: true)
                if FileManager.default.fileExists(atPath: source.path) {
                    try FileManager.default.copyItem(at: source, to: target)
                }
            }
            storageStatus = "Backup created: \(destination.lastPathComponent)"
        } catch {
            storageStatus = "Backup failed: \(error.localizedDescription)"
        }
    }

    func checkForUpdates() {
        updateStatus = "Checking GitHub for a newer Mac preview…"
        guard let url = URL(string: "https://api.github.com/repos/maccabluu/BluBox360/releases?per_page=30") else { return }
        var request = URLRequest(url: url)
        request.setValue("BluBox360-macOS/3.0", forHTTPHeaderField: "User-Agent")
        URLSession.shared.dataTask(with: request) { [weak self] data, _, error in
            let message: String
            if let error {
                message = "Update check failed: \(error.localizedDescription)"
            } else if let data,
                      let releases = try? JSONSerialization.jsonObject(with: data) as? [[String: Any]] {
                let tags = releases.compactMap { $0["tag_name"] as? String }
                let macTags = tags.filter { $0.lowercased().hasPrefix("mac-v") }
                message = macTags.isEmpty
                    ? "No public macOS release is published yet. You are testing the 3.0 preview track."
                    : "Newest published Mac tag: \(macTags[0])"
            } else {
                message = "Update check returned an unreadable response."
            }
            DispatchQueue.main.async { self?.updateStatus = message }
        }.resume()
    }

    func openCoreFolder() { MacDataPaths.open(MacDataPaths.core()) }
    func openSavesFolder() { MacDataPaths.open(MacDataPaths.saves()) }
    func openShaderCacheFolder() { MacDataPaths.open(MacDataPaths.shaderCache()) }
    func openLogsFolder() { MacDataPaths.open(MacDataPaths.logs()) }
    func openDataFolder() { MacDataPaths.open(MacDataPaths.root()) }
    func openPatchesFolder() { MacDataPaths.open(MacDataPaths.patches()) }
    func openBackupsFolder() { MacDataPaths.open(MacDataPaths.backups()) }
    func openSaveFolder(for game: GameEntry) { MacDataPaths.open(MacDataPaths.gameSaveFolder(game)) }

    private func xeniaArguments(game: GameEntry, settings: MacLaunchSettings) -> [String] {
        let wide = settings.aspectMode != "4:3"
        let letterbox = settings.aspectMode != "Stretch"
        var arguments = [
            "--gpu=metal",
            "--log_file=stdout",
            "--show_debug_overlay=\(settings.showFPS ? "true" : "false")",
            "--framerate_limit=\(settings.targetFPS)",
            "--draw_resolution_scale_x=\(settings.renderScale)",
            "--draw_resolution_scale_y=\(settings.renderScale)",
            "--postprocess_scaling_and_sharpening=\(settings.upscaler)",
            "--postprocess_antialiasing=\(settings.antialiasing)",
            "--anisotropic_override=\(settings.anisotropic)",
            "--widescreen=\(wide ? "true" : "false")",
            "--present_letterbox=\(letterbox ? "true" : "false")",
            "--interlaced=\(settings.interlaced ? "true" : "false")",
            "--async_shader_compilation=\(settings.asyncShaders ? "true" : "false")",
            "--async_shader_vs_interpreter=\(settings.asyncShaders ? "true" : "false")",
            "--async_shader_skip_draws=\(settings.skipShaderDraws ? "true" : "false")",
            "--pipeline_storage_precreate=\(settings.pipelinePreload ? "true" : "false")",
            "--vulkan_pipeline_creation_threads=\(settings.shaderWorkers)",
            "--vibration=\(settings.rumble ? "true" : "false")",
            "--apply_patches=\(settings.applyPatches ? "true" : "false")"
        ]
        if settings.graphicsPreset == "Performance" {
            arguments += ["--postprocess_scaling_and_sharpening=none", "--postprocess_antialiasing=off"]
        } else if settings.graphicsPreset == "HD+" {
            arguments += ["--postprocess_scaling_and_sharpening=fsr", "--postprocess_antialiasing=fxaa"]
        }
        arguments.append(game.path)
        return arguments
    }

    private func selectEngine(mode: String) -> EngineChoice? {
        #if arch(arm64)
        if mode == "Native arm64" {
            return choice(architecture: "arm64") ?? choice(architecture: "x86_64")
        }
        if mode == "Compatibility x86_64" {
            return choice(architecture: "x86_64") ?? choice(architecture: "arm64")
        }
        if rosettaAvailable(), let x64 = choice(architecture: "x86_64") {
            return x64
        }
        return choice(architecture: "arm64") ?? choice(architecture: "x86_64")
        #else
        return choice(architecture: "x86_64") ?? choice(architecture: "arm64")
        #endif
    }

    private func choice(architecture: String) -> EngineChoice? {
        guard let app = bundledEngineApp(architecture: architecture),
              let executable = executableInApp(app) else { return nil }
        return EngineChoice(
            executable: executable,
            name: "Xenia-Edge Metal",
            architecture: architecture
        )
    }

    private func bundledEngineApp(architecture: String) -> URL? {
        guard let resources = Bundle.main.resourceURL else { return nil }
        let app = resources
            .appendingPathComponent("Engines", isDirectory: true)
            .appendingPathComponent(architecture, isDirectory: true)
            .appendingPathComponent("Xenia-Edge.app", isDirectory: true)
        return FileManager.default.fileExists(atPath: app.path) ? app : nil
    }

    private func executableInApp(_ app: URL) -> URL? {
        let plist = app.appendingPathComponent("Contents/Info.plist")
        if let dictionary = NSDictionary(contentsOf: plist),
           let name = dictionary["CFBundleExecutable"] as? String {
            let executable = app.appendingPathComponent("Contents/MacOS/\(name)")
            if FileManager.default.isExecutableFile(atPath: executable.path) { return executable }
        }
        let folder = app.appendingPathComponent("Contents/MacOS", isDirectory: true)
        guard let entries = try? FileManager.default.contentsOfDirectory(at: folder, includingPropertiesForKeys: nil) else { return nil }
        return entries.first { FileManager.default.isExecutableFile(atPath: $0.path) }
    }

    private func rosettaAvailable() -> Bool {
        #if arch(arm64)
        let task = Process()
        task.executableURL = URL(fileURLWithPath: "/usr/bin/arch")
        task.arguments = ["-x86_64", "/usr/bin/true"]
        do {
            try task.run()
            task.waitUntilExit()
            return task.terminationStatus == 0
        } catch {
            return false
        }
        #else
        return true
        #endif
    }

    private func locateBootstrapCore() -> URL? {
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
        return candidates.first { fileManager.isExecutableFile(atPath: $0.path) }
    }

    private func installBundledPatchesIfNeeded() {
        guard let sourceFolder = Bundle.main.resourceURL?.appendingPathComponent("Patches", isDirectory: true),
              let files = try? FileManager.default.contentsOfDirectory(at: sourceFolder, includingPropertiesForKeys: nil) else { return }
        MacDataPaths.prepare()
        for source in files where source.pathExtension.lowercased() == "toml" {
            let destination = MacDataPaths.patches().appendingPathComponent(source.lastPathComponent)
            if !FileManager.default.fileExists(atPath: destination.path) {
                try? FileManager.default.copyItem(at: source, to: destination)
            }
        }
    }

    private func appendConsole(_ text: String) { consoleText.append(text) }

    private func finishSession(exitCode: Int32) {
        isRunning = false
        process = nil
        currentGameName = nil
        statusText = exitCode == 0
            ? "Game session finished"
            : "Emulator exited with code \(exitCode). Check Diagnostics for the log."
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

    static var metalDevice: String { MTLCreateSystemDefaultDevice()?.name ?? "No Metal device found" }

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

    static var lowPowerMode: String { ProcessInfo.processInfo.isLowPowerModeEnabled ? "On" : "Off" }

    static var memory: String {
        let gib = Double(ProcessInfo.processInfo.physicalMemory) / 1_073_741_824.0
        return String(format: "%.1f GB", gib)
    }

    static var macOSVersion: String { ProcessInfo.processInfo.operatingSystemVersionString }
}
