import AppKit
import SwiftUI

private enum SidebarPage: String, CaseIterable, Identifiable {
    case library = "Library"
    case recent = "Recently Played"
    case favorites = "Favorites"
    case achievements = "Achievements"
    case profile = "Profile"
    case mods = "Mods & Patches"
    case settings = "Settings"
    case diagnostics = "Diagnostics"

    var id: String { rawValue }

    var icon: String {
        switch self {
        case .library: return "square.grid.2x2"
        case .recent: return "clock"
        case .favorites: return "star.fill"
        case .achievements: return "trophy.fill"
        case .profile: return "person.crop.circle"
        case .mods: return "puzzlepiece.extension.fill"
        case .settings: return "gearshape.fill"
        case .diagnostics: return "waveform.path.ecg"
        }
    }
}

private struct BluCard<Content: View>: View {
    @ViewBuilder var content: Content

    var body: some View {
        content
            .padding(16)
            .background(Color.white.opacity(0.07))
            .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
            .overlay {
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .stroke(Color.white.opacity(0.10), lineWidth: 1)
                    .allowsHitTesting(false)
            }
    }
}

private struct BluBoxLogoView: View {
    let size: CGFloat

    var body: some View {
        Group {
            if let url = Bundle.main.url(forResource: "blubox_logo", withExtension: "png"),
               let image = NSImage(contentsOf: url) {
                Image(nsImage: image)
                    .resizable()
                    .scaledToFit()
            } else {
                ZStack {
                    RoundedRectangle(cornerRadius: size * 0.22, style: .continuous)
                        .fill(Color.blue)
                    Text("B")
                        .font(.system(size: size * 0.52, weight: .black, design: .rounded))
                        .foregroundStyle(.white)
                }
            }
        }
        .frame(width: size, height: size)
    }
}

struct ContentView: View {
    @EnvironmentObject private var library: GameLibrary
    @EnvironmentObject private var core: MacCoreBridge

    @State private var page: SidebarPage = .library
    @State private var searchText = ""
    @State private var sortMode = "Name"
    @State private var alertText = ""
    @State private var showingAlert = false

    @AppStorage("BluBoxMacTargetFPS") private var targetFPS = 60
    @AppStorage("BluBoxMacGraphicsPreset") private var graphicsPreset = "Balanced"
    @AppStorage("BluBoxMacRenderScaleV3") private var renderScale = 1
    @AppStorage("BluBoxMacShowFPS") private var showFPS = true
    @AppStorage("BluBoxMacLowHeatMode") private var lowHeatMode = false
    @AppStorage("BluBoxMacSmartHeatGuard") private var smartHeatGuard = true
    @AppStorage("BluBoxMacProfileName") private var profileName = "Player 1"
    @AppStorage("BluBoxMacEngineMode") private var engineMode = "Auto"
    @AppStorage("BluBoxMacUpscaler") private var upscaler = "fsr"
    @AppStorage("BluBoxMacAA") private var antialiasing = "fxaa"
    @AppStorage("BluBoxMacAnisotropic") private var anisotropic = -1
    @AppStorage("BluBoxMacAspect") private var aspectMode = "16:9"
    @AppStorage("BluBoxMacInterlaced") private var interlaced = false
    @AppStorage("BluBoxMacAsyncShaders") private var asyncShaders = true
    @AppStorage("BluBoxMacSkipShaderDraws") private var skipShaderDraws = true
    @AppStorage("BluBoxMacPipelinePreload") private var pipelinePreload = true
    @AppStorage("BluBoxMacShaderWorkers") private var shaderWorkers = 2
    @AppStorage("BluBoxMacRumble") private var rumble = true
    @AppStorage("BluBoxMacApplyPatches") private var applyPatches = true
    @AppStorage("BluBoxMacCoverStyle") private var coverStyle = "3D"

    private let columns = [GridItem(.adaptive(minimum: 190, maximum: 245), spacing: 16)]

    var body: some View {
        HStack(spacing: 0) {
            sidebar
            Divider()
            detail
        }
        .background {
            LinearGradient(
                colors: [Color(red: 0.015, green: 0.045, blue: 0.11),
                         Color(red: 0.02, green: 0.19, blue: 0.37)],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            .ignoresSafeArea()
            .allowsHitTesting(false)
        }
        .preferredColorScheme(.dark)
        .alert("BluBox 360 macOS", isPresented: $showingAlert) {
            Button("OK", role: .cancel) { }
        } message: {
            Text(alertText)
        }
        .onAppear {
            library.setStatus("BluBox 360 macOS 3.0 ready")
            core.refresh()
        }
    }

    private var sidebar: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 12) {
                BluBoxLogoView(size: 46)
                VStack(alignment: .leading, spacing: 1) {
                    Text("BluBox 360")
                        .font(.headline.bold())
                    Text("macOS 3.0 Preview")
                        .font(.caption)
                        .foregroundStyle(.cyan)
                }
            }
            .padding(.horizontal, 14)
            .padding(.top, 14)

            ScrollView {
                VStack(spacing: 6) {
                    ForEach(SidebarPage.allCases) { item in
                        Button {
                            page = item
                        } label: {
                            HStack(spacing: 11) {
                                Image(systemName: item.icon)
                                    .frame(width: 22)
                                Text(item.rawValue)
                                Spacer()
                            }
                            .padding(.horizontal, 12)
                            .padding(.vertical, 10)
                            .background(page == item ? Color.blue.opacity(0.48) : Color.clear)
                            .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(.horizontal, 9)
            }

            Spacer(minLength: 8)

            VStack(alignment: .leading, spacing: 5) {
                Label(core.playableEngineExists ? "Metal engine ready" : "Engine missing",
                      systemImage: core.playableEngineExists ? "checkmark.circle.fill" : "exclamationmark.triangle.fill")
                    .foregroundStyle(core.playableEngineExists ? .green : .orange)
                    .font(.caption.bold())
                Text(MacDiagnostics.architecture)
                    .font(.caption2)
                    .foregroundStyle(.secondary)
            }
            .padding(14)
        }
        .frame(width: 225)
        .background(Color.black.opacity(0.23))
    }

    private var detail: some View {
        VStack(spacing: 0) {
            header
            Divider().overlay(Color.white.opacity(0.10))
            Group {
                switch page {
                case .library: gameCollection(title: "Game Library", games: library.games)
                case .recent: gameCollection(title: "Recently Played", games: library.recentGames)
                case .favorites: gameCollection(title: "Favorites", games: library.favoriteGames)
                case .achievements: achievementsPage
                case .profile: profilePage
                case .mods: modsPage
                case .settings: settingsPage
                case .diagnostics: diagnosticsPage
                }
            }
            statusBar
        }
    }

    private var header: some View {
        HStack(spacing: 14) {
            BluBoxLogoView(size: 55)
            VStack(alignment: .leading, spacing: 2) {
                Text("BluBox 360")
                    .font(.system(size: 25, weight: .bold, design: .rounded))
                Text("macOS 3.0 Preview • Xenia-Edge Metal")
                    .font(.caption.bold())
                    .foregroundStyle(.cyan)
            }
            Spacer()
            if core.isRunning {
                VStack(alignment: .trailing, spacing: 4) {
                    Text(core.currentGameName ?? "Game running")
                        .font(.caption.bold())
                    Button("Stop Game") { core.stop() }
                        .buttonStyle(.bordered)
                }
            }
            Button("Add Games") { library.addGames() }
                .buttonStyle(.borderedProminent)
            Button("Scan Folder") { library.addFolder() }
                .buttonStyle(.bordered)
        }
        .padding(.horizontal, 22)
        .padding(.vertical, 13)
    }

    private func visibleGames(_ games: [GameEntry]) -> [GameEntry] {
        let filtered = searchText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            ? games
            : games.filter {
                $0.name.localizedCaseInsensitiveContains(searchText) ||
                $0.fileType.localizedCaseInsensitiveContains(searchText)
            }
        switch sortMode {
        case "Recent":
            return filtered.sorted { ($0.lastPlayed ?? .distantPast) > ($1.lastPlayed ?? .distantPast) }
        case "Favorites":
            return filtered.sorted {
                if $0.isFavorite != $1.isFavorite { return $0.isFavorite && !$1.isFavorite }
                return $0.name.localizedCaseInsensitiveCompare($1.name) == .orderedAscending
            }
        default:
            return filtered.sorted { $0.name.localizedCaseInsensitiveCompare($1.name) == .orderedAscending }
        }
    }

    private func gameCollection(title: String, games: [GameEntry]) -> some View {
        let displayGames = visibleGames(games)
        return VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 12) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(title).font(.title2.bold())
                    Text("ISO, XEX and ZAR library")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                Spacer()
                TextField("Search games", text: $searchText)
                    .textFieldStyle(.roundedBorder)
                    .frame(width: 220)
                Picker("Sort", selection: $sortMode) {
                    Text("Name").tag("Name")
                    Text("Recent").tag("Recent")
                    Text("Favorites").tag("Favorites")
                }
                .frame(width: 140)
                Button("Refresh") { library.refreshLibrary() }
            }
            .padding(.horizontal, 22)
            .padding(.vertical, 14)

            if displayGames.isEmpty {
                emptyLibrary(title: title)
            } else {
                ScrollView {
                    LazyVGrid(columns: columns, alignment: .leading, spacing: 16) {
                        ForEach(displayGames) { game in gameCard(game) }
                    }
                    .padding(22)
                }
            }
        }
    }

    private func emptyLibrary(title: String) -> some View {
        VStack(spacing: 16) {
            Spacer()
            Image(systemName: title == "Favorites" ? "star" : "gamecontroller")
                .font(.system(size: 54, weight: .light))
                .foregroundStyle(.cyan)
            Text(searchText.isEmpty ? "No games here yet" : "No games match your search")
                .font(.title2.bold())
            Text("Add your own legally dumped Xbox 360 game files, then choose Play to start the experimental Metal engine.")
                .multilineTextAlignment(.center)
                .foregroundStyle(.secondary)
                .frame(maxWidth: 540)
            if title == "Game Library" && searchText.isEmpty {
                HStack {
                    Button("Add Game Files") { library.addGames() }
                    Button("Scan Game Folder") { library.addFolder() }
                }
                .buttonStyle(.borderedProminent)
                .controlSize(.large)
            }
            Spacer()
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .padding(30)
    }

    private func gameCard(_ game: GameEntry) -> some View {
        BluCard {
            VStack(alignment: .leading, spacing: 11) {
                coverView(game)
                    .frame(height: 245)
                    .rotation3DEffect(.degrees(coverStyle == "3D" ? -4 : 0), axis: (x: 0, y: 1, z: 0))
                    .shadow(radius: coverStyle == "3D" ? 8 : 0)

                HStack(alignment: .top) {
                    VStack(alignment: .leading, spacing: 3) {
                        Text(game.name).font(.headline).lineLimit(2)
                        Text("\(game.fileType) • \(game.fileSizeText)")
                            .font(.caption.bold())
                            .foregroundStyle(.cyan)
                        Text(game.folderText)
                            .font(.caption2)
                            .foregroundStyle(.secondary)
                            .lineLimit(1)
                    }
                    Spacer()
                    if game.isFavorite { Image(systemName: "star.fill").foregroundStyle(.yellow) }
                }

                HStack {
                    Button("Play") { play(game) }
                        .buttonStyle(.borderedProminent)
                        .disabled(core.isRunning)
                    Menu {
                        Button(game.isFavorite ? "Remove Favorite" : "Add to Favorites") { library.toggleFavorite(game) }
                        Button("Choose Cover…") { library.chooseCover(for: game) }
                        if game.coverURL != nil { Button("Reset Cover") { library.resetCover(for: game) } }
                        Divider()
                        Button("Test Diagnostic Core") { core.runBootstrap(game: game, settings: launchSettings()) }
                        Button("Open Save Folder") { core.openSaveFolder(for: game) }
                        Button("Show Game in Finder") { library.reveal(game) }
                        Divider()
                        Button("Remove from Library", role: .destructive) { library.remove(game) }
                    } label: { Image(systemName: "ellipsis.circle") }
                    .menuStyle(.borderlessButton)
                }
            }
        }
    }

    @ViewBuilder
    private func coverView(_ game: GameEntry) -> some View {
        if let coverURL = game.coverURL, let image = NSImage(contentsOf: coverURL) {
            Image(nsImage: image)
                .resizable()
                .scaledToFill()
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .clipped()
                .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
        } else {
            ZStack {
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .fill(LinearGradient(colors: [Color.blue.opacity(0.80), Color.cyan.opacity(0.18)], startPoint: .top, endPoint: .bottom))
                VStack(spacing: 10) {
                    BluBoxLogoView(size: 72)
                    Text(game.fileType).font(.caption.bold()).foregroundStyle(.cyan)
                }
            }
        }
    }

    private func play(_ game: GameEntry) {
        core.refresh()
        guard core.playableEngineExists else {
            alertText = "The playable Xenia-Edge Metal engine is missing from this build. Open Diagnostics to check the engine package."
            showingAlert = true
            return
        }
        library.markPlayed(game)
        core.launch(game: game, settings: launchSettings())
    }

    private func launchSettings() -> MacLaunchSettings {
        MacLaunchSettings(
            targetFPS: targetFPS,
            graphicsPreset: graphicsPreset,
            renderScale: renderScale,
            showFPS: showFPS,
            lowHeatMode: lowHeatMode,
            smartHeatGuard: smartHeatGuard,
            profileName: profileName,
            engineMode: engineMode,
            upscaler: upscaler,
            antialiasing: antialiasing,
            anisotropic: anisotropic,
            aspectMode: aspectMode,
            interlaced: interlaced,
            asyncShaders: asyncShaders,
            skipShaderDraws: skipShaderDraws,
            pipelinePreload: pipelinePreload,
            shaderWorkers: shaderWorkers,
            rumble: rumble,
            applyPatches: applyPatches
        )
    }

    private var achievementsPage: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Text("Achievements").font(.title2.bold())
                BluCard {
                    VStack(alignment: .leading, spacing: 10) {
                        Label("Local achievement support", systemImage: "trophy.fill")
                            .font(.headline)
                            .foregroundStyle(.yellow)
                        Text("BluBox keeps the profile and storage areas ready for Xbox 360 achievement data. The bundled experimental engine handles game execution. BluBox will show per-game achievement events here when the engine exposes them reliably on macOS.")
                            .foregroundStyle(.secondary)
                        Button("Open Profile Data") { MacDataPaths.open(MacDataPaths.profiles()) }
                    }
                }
                BluCard {
                    HStack {
                        VStack(alignment: .leading) {
                            Text(profileName).font(.headline)
                            Text("Local BluBox profile").font(.caption).foregroundStyle(.secondary)
                        }
                        Spacer()
                        Text("Gamerscore: awaiting engine data")
                            .font(.caption.bold())
                            .foregroundStyle(.cyan)
                    }
                }
            }
            .padding(22)
        }
    }

    private var profilePage: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Text("Profile").font(.title2.bold())
                BluCard {
                    HStack(spacing: 18) {
                        ZStack {
                            Circle().fill(Color.blue.opacity(0.80))
                            Text(String(profileName.prefix(1)).uppercased())
                                .font(.system(size: 34, weight: .bold, design: .rounded))
                        }
                        .frame(width: 78, height: 78)
                        VStack(alignment: .leading, spacing: 7) {
                            TextField("Profile name", text: $profileName)
                                .textFieldStyle(.roundedBorder)
                                .frame(maxWidth: 330)
                            Text("Local BluBox profile • saves and achievement data stay on this Mac")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                        Spacer()
                    }
                }
                HStack {
                    Button("Open Profile Folder") { MacDataPaths.open(MacDataPaths.profiles()) }
                    Button("Open Saves") { core.openSavesFolder() }
                    Button("Create Backup") { core.createBackup() }
                    Button("Open Backups") { core.openBackupsFolder() }
                }
                .buttonStyle(.bordered)
                if !core.storageStatus.isEmpty { Text(core.storageStatus).font(.caption).foregroundStyle(.secondary) }
            }
            .padding(22)
        }
    }

    private var modsPage: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Text("Mods & Patches").font(.title2.bold())
                BluCard {
                    VStack(alignment: .leading, spacing: 10) {
                        Toggle("Apply game patches", isOn: $applyPatches)
                        Text("The Fable II BluBox performance patch from the Android edition is bundled into the Mac patch folder. Other compatible Xenia patch files can be added there for testing.")
                            .foregroundStyle(.secondary)
                        HStack {
                            Button("Open Patches Folder") { core.openPatchesFolder() }
                            Button("Open Data Folder") { core.openDataFolder() }
                        }
                    }
                }
                BluCard {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("MOD SAFETY").font(.caption.bold()).foregroundStyle(.cyan)
                        Text("Use patches made for the exact game title ID and version. Keep backups before testing mods or patches.")
                            .foregroundStyle(.secondary)
                    }
                }
            }
            .padding(22)
        }
    }

    private var settingsPage: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Text("Settings").font(.title2.bold())

                BluCard {
                    VStack(alignment: .leading, spacing: 12) {
                        sectionLabel("EMULATOR ENGINE")
                        Picker("Engine", selection: $engineMode) {
                            Text("Auto (recommended)").tag("Auto")
                            Text("Compatibility x86_64").tag("Compatibility x86_64")
                            Text("Native arm64").tag("Native arm64")
                        }
                        .pickerStyle(.segmented)
                        Text("The x86_64 engine is preferred in Auto mode on Apple Silicon when Rosetta is available. Native arm64 is available for titles that behave better there.")
                            .font(.caption).foregroundStyle(.secondary)
                        Text(core.bundledEngineSummary).font(.caption.bold()).foregroundStyle(.cyan)
                    }
                }

                BluCard {
                    VStack(alignment: .leading, spacing: 12) {
                        sectionLabel("PERFORMANCE")
                        Picker("Preset", selection: $graphicsPreset) {
                            Text("Performance").tag("Performance")
                            Text("Balanced").tag("Balanced")
                            Text("HD+").tag("HD+")
                            Text("Custom").tag("Custom")
                        }
                        .pickerStyle(.segmented)
                        Picker("Frame target", selection: $targetFPS) {
                            Text("30 FPS").tag(30)
                            Text("60 FPS").tag(60)
                        }
                        .pickerStyle(.segmented)
                        Stepper("Resolution scale: \(renderScale)x", value: $renderScale, in: 1...7)
                        Toggle("Show FPS / debug overlay", isOn: $showFPS)
                        Toggle("Low Heat Mode", isOn: $lowHeatMode)
                        Toggle("Smart Heat Guard", isOn: $smartHeatGuard)
                        Text("Low Heat Mode starts at 30 FPS, native resolution and reduced shader work. Smart Heat Guard applies the same safer launch profile when macOS reports serious heat or Low Power Mode.")
                            .font(.caption).foregroundStyle(.secondary)
                    }
                }

                BluCard {
                    VStack(alignment: .leading, spacing: 12) {
                        sectionLabel("ADVANCED GRAPHICS")
                        Picker("Upscaler", selection: $upscaler) {
                            Text("None").tag("none")
                            Text("CAS").tag("cas")
                            Text("FSR").tag("fsr")
                        }
                        Picker("Anti-aliasing", selection: $antialiasing) {
                            Text("Off").tag("off")
                            Text("FXAA").tag("fxaa")
                            Text("FXAA Extreme").tag("fxaa_extreme")
                        }
                        Picker("Anisotropic filtering", selection: $anisotropic) {
                            Text("Auto").tag(-1)
                            Text("2x").tag(1)
                            Text("4x").tag(2)
                            Text("8x").tag(3)
                            Text("16x").tag(4)
                        }
                        Picker("Aspect ratio", selection: $aspectMode) {
                            Text("16:9").tag("16:9")
                            Text("4:3").tag("4:3")
                            Text("Stretch").tag("Stretch")
                        }
                        Toggle("Interlaced output", isOn: $interlaced)
                    }
                }

                BluCard {
                    VStack(alignment: .leading, spacing: 12) {
                        sectionLabel("SHADERS")
                        Toggle("Async shader compilation", isOn: $asyncShaders)
                        Toggle("Skip draws while shaders compile", isOn: $skipShaderDraws)
                            .disabled(!asyncShaders)
                        Toggle("Preload pipeline storage", isOn: $pipelinePreload)
                        Picker("Shader workers", selection: $shaderWorkers) {
                            Text("Auto").tag(0)
                            Text("1").tag(1)
                            Text("2").tag(2)
                            Text("4").tag(4)
                            Text("6").tag(6)
                        }
                    }
                }

                BluCard {
                    VStack(alignment: .leading, spacing: 12) {
                        sectionLabel("CONTROLLER")
                        Text(MacDiagnostics.controllerSummary).font(.headline)
                        Text(MacDiagnostics.controllerNames).foregroundStyle(.secondary)
                        Toggle("Controller vibration / rumble", isOn: $rumble)
                    }
                }

                BluCard {
                    VStack(alignment: .leading, spacing: 12) {
                        sectionLabel("LIBRARY & COVERS")
                        Picker("Cover style", selection: $coverStyle) {
                            Text("3D cases").tag("3D")
                            Text("Flat covers").tag("Flat")
                        }
                        .pickerStyle(.segmented)
                        HStack {
                            Button("Refresh Library") { library.refreshLibrary() }
                            Button("Add Games") { library.addGames() }
                            Button("Scan Folder") { library.addFolder() }
                        }
                    }
                }

                BluCard {
                    VStack(alignment: .leading, spacing: 12) {
                        sectionLabel("STORAGE")
                        HStack {
                            Button("Clear Shader Cache") { core.clearShaderCache() }
                            Button("Shader Cache Folder") { core.openShaderCacheFolder() }
                            Button("Logs") { core.openLogsFolder() }
                            Button("Create Backup") { core.createBackup() }
                        }
                        if !core.storageStatus.isEmpty { Text(core.storageStatus).font(.caption).foregroundStyle(.secondary) }
                    }
                }

                BluCard {
                    VStack(alignment: .leading, spacing: 10) {
                        sectionLabel("APP UPDATE")
                        Button("Check for Updates") { core.checkForUpdates() }
                            .buttonStyle(.borderedProminent)
                        Text(core.updateStatus).font(.caption).foregroundStyle(.secondary)
                    }
                }
            }
            .padding(22)
        }
    }

    private var diagnosticsPage: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                HStack {
                    Text("Diagnostics").font(.title2.bold())
                    Spacer()
                    Button("Refresh") { core.refresh() }
                }
                BluCard {
                    diagnosticRow("BluBox", "macOS 3.0 Preview")
                    diagnosticRow("Architecture", MacDiagnostics.architecture)
                    diagnosticRow("macOS", MacDiagnostics.macOSVersion)
                    diagnosticRow("Metal GPU", MacDiagnostics.metalDevice)
                    diagnosticRow("Memory", MacDiagnostics.memory)
                    diagnosticRow("Controllers", MacDiagnostics.controllerSummary)
                    diagnosticRow("Thermal state", MacDiagnostics.thermalState)
                    diagnosticRow("Low Power Mode", MacDiagnostics.lowPowerMode)
                    diagnosticRow("Playable engine", core.bundledEngineSummary)
                    diagnosticRow("Selected engine mode", engineMode)
                }
                BluCard {
                    VStack(alignment: .leading, spacing: 8) {
                        HStack {
                            sectionLabel("ENGINE LOG")
                            Spacer()
                            Button("Clear") { core.clearConsole() }.buttonStyle(.borderless)
                        }
                        ScrollView {
                            Text(core.consoleText.isEmpty ? "No game session has run yet." : core.consoleText)
                                .font(.system(.caption, design: .monospaced))
                                .textSelection(.enabled)
                                .frame(maxWidth: .infinity, alignment: .leading)
                        }
                        .frame(minHeight: 210)
                    }
                }
                HStack {
                    Button("Open Logs") { core.openLogsFolder() }
                    Button("Open Engine Data") { core.openDataFolder() }
                    Button("Open Patches") { core.openPatchesFolder() }
                }
                .buttonStyle(.bordered)
            }
            .padding(22)
        }
    }

    private func sectionLabel(_ text: String) -> some View {
        Text(text).font(.caption.bold()).foregroundStyle(.cyan)
    }

    private func diagnosticRow(_ label: String, _ value: String) -> some View {
        HStack(alignment: .firstTextBaseline) {
            Text(label).foregroundStyle(.secondary)
            Spacer()
            Text(value).fontWeight(.semibold).multilineTextAlignment(.trailing)
        }
        .padding(.vertical, 4)
    }

    private var statusBar: some View {
        HStack(spacing: 8) {
            Circle()
                .fill(core.playableEngineExists ? Color.green : Color.orange)
                .frame(width: 8, height: 8)
            Text(core.isRunning ? core.statusText : library.statusText)
                .font(.caption)
                .foregroundStyle(.secondary)
            Spacer()
            Text("Profile: \(profileName)").font(.caption2).foregroundStyle(.secondary)
            Text("•").foregroundStyle(.secondary)
            Text(core.statusText).font(.caption2).foregroundStyle(.secondary)
        }
        .padding(.horizontal, 22)
        .padding(.vertical, 9)
        .background(Color.black.opacity(0.20))
    }
}
