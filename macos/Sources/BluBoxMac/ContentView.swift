import AppKit
import SwiftUI

private enum SidebarPage: String, CaseIterable, Identifiable {
    case library = "Library"
    case recent = "Recently Played"
    case favorites = "Favorites"
    case profile = "Profile"
    case settings = "Settings"
    case diagnostics = "Diagnostics"

    var id: String { rawValue }

    var icon: String {
        switch self {
        case .library: return "square.grid.2x2"
        case .recent: return "clock"
        case .favorites: return "star.fill"
        case .profile: return "person.crop.circle"
        case .settings: return "gearshape"
        case .diagnostics: return "waveform.path.ecg"
        }
    }
}

struct BluCard<Content: View>: View {
    @ViewBuilder var content: Content

    var body: some View {
        content
            .padding(16)
            .background(Color.white.opacity(0.07))
            .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .stroke(Color.white.opacity(0.09), lineWidth: 1)
            )
    }
}

struct ContentView: View {
    @EnvironmentObject private var library: GameLibrary
    @EnvironmentObject private var core: MacCoreBridge

    @State private var page: SidebarPage? = .library
    @State private var coreAlertText = ""
    @State private var showingCoreAlert = false
    @State private var searchText = ""
    @State private var sortMode = "Name"

    @AppStorage("BluBoxMacTargetFPS") private var targetFPS = 60
    @AppStorage("BluBoxMacGraphicsPreset") private var graphicsPreset = "Balanced"
    @AppStorage("BluBoxMacRenderScale") private var renderScale = "Native"
    @AppStorage("BluBoxMacShowFPS") private var showFPS = true
    @AppStorage("BluBoxMacSmartHeatGuard") private var smartHeatGuard = true
    @AppStorage("BluBoxMacProfileName") private var profileName = "Player 1"

    private let columns = [
        GridItem(.adaptive(minimum: 190, maximum: 235), spacing: 16)
    ]

    var body: some View {
        NavigationSplitView {
            sidebar
        } detail: {
            ZStack {
                LinearGradient(
                    colors: [
                        Color(red: 0.02, green: 0.05, blue: 0.12),
                        Color(red: 0.02, green: 0.18, blue: 0.34)
                    ],
                    startPoint: .topLeading,
                    endPoint: .bottomTrailing
                )
                .ignoresSafeArea()

                VStack(spacing: 0) {
                    header
                    Divider().overlay(Color.white.opacity(0.08))
                    pageContent
                    statusBar
                }
            }
        }
        .navigationSplitViewStyle(.balanced)
        .preferredColorScheme(.dark)
        .searchable(text: $searchText, placement: .toolbar, prompt: "Search games")
        .alert("BluBox 360 macOS", isPresented: $showingCoreAlert) {
            Button("OK", role: .cancel) { }
        } message: {
            Text(coreAlertText)
        }
    }

    private var sidebar: some View {
        List(selection: $page) {
            Section("BluBox 360") {
                ForEach(SidebarPage.allCases) { item in
                    Label(item.rawValue, systemImage: item.icon)
                        .tag(Optional(item))
                }
            }
        }
        .navigationTitle("BluBox 360")
        .frame(minWidth: 205)
    }

    private var header: some View {
        HStack(spacing: 14) {
            ZStack {
                RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .fill(Color.blue.opacity(0.85))
                Text("B")
                    .font(.system(size: 28, weight: .black, design: .rounded))
            }
            .frame(width: 54, height: 54)

            VStack(alignment: .leading, spacing: 2) {
                Text("BluBox 360")
                    .font(.system(size: 25, weight: .bold, design: .rounded))
                Text("macOS 2.2 Preview • Universal")
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(.cyan)
            }

            Spacer()

            if core.isRunning {
                VStack(alignment: .trailing, spacing: 2) {
                    Text(core.currentGameName ?? "Game running")
                        .font(.caption.bold())
                    Button("Stop Game") { core.stop() }
                        .buttonStyle(.bordered)
                }
            }

            Menu("Library") {
                Button("Add Game Files…") { library.addGames() }
                Button("Scan Game Folder…") { library.addFolder() }
                Divider()
                Button("Refresh Library") { library.refreshLibrary() }
            }
            .buttonStyle(.borderedProminent)
            .controlSize(.large)
        }
        .padding(.horizontal, 22)
        .padding(.vertical, 14)
    }

    @ViewBuilder
    private var pageContent: some View {
        switch page ?? .library {
        case .library:
            gameCollection(title: "Game Library", games: library.games)
        case .recent:
            gameCollection(title: "Recently Played", games: library.recentGames)
        case .favorites:
            gameCollection(title: "Favorites", games: library.favoriteGames)
        case .profile:
            profilePage
        case .settings:
            settingsPage
        case .diagnostics:
            diagnosticsPage
        }
    }

    private func visibleGames(_ games: [GameEntry]) -> [GameEntry] {
        let filtered: [GameEntry]
        if searchText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            filtered = games
        } else {
            filtered = games.filter {
                $0.name.localizedCaseInsensitiveContains(searchText) ||
                $0.fileType.localizedCaseInsensitiveContains(searchText)
            }
        }

        switch sortMode {
        case "Recently Played":
            return filtered.sorted { ($0.lastPlayed ?? .distantPast) > ($1.lastPlayed ?? .distantPast) }
        case "Favorites First":
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
            HStack {
                VStack(alignment: .leading, spacing: 3) {
                    Text(title)
                        .font(.title2.bold())
                    Text("ISO, ZAR and XEX library")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                Spacer()

                Picker("Sort", selection: $sortMode) {
                    Text("Name").tag("Name")
                    Text("Recently Played").tag("Recently Played")
                    Text("Favorites First").tag("Favorites First")
                }
                .frame(width: 170)

                Text("\(displayGames.count) game\(displayGames.count == 1 ? "" : "s")")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            .padding(.horizontal, 22)
            .padding(.top, 18)
            .padding(.bottom, 10)

            if displayGames.isEmpty {
                emptyLibrary(title: title)
            } else {
                ScrollView {
                    LazyVGrid(columns: columns, alignment: .leading, spacing: 16) {
                        ForEach(displayGames) { game in
                            gameCard(game)
                        }
                    }
                    .padding(22)
                }
            }
        }
    }

    private func emptyLibrary(title: String) -> some View {
        VStack(spacing: 16) {
            Spacer()
            Image(systemName: title == "Favorites" ? "star" : "rectangle.stack.badge.plus")
                .font(.system(size: 54, weight: .light))
                .foregroundStyle(.cyan)
            Text(searchText.isEmpty
                 ? (title == "Favorites" ? "No favorites yet" : "Nothing here yet")
                 : "No games match your search")
                .font(.title2.bold())
            Text(title == "Recently Played"
                 ? "Games appear here after the native core launches them."
                 : title == "Favorites"
                 ? "Mark games as favorites from the menu on a game card."
                 : "Add your own Xbox 360 ISO, ZAR or XEX files to build your Mac library.")
                .multilineTextAlignment(.center)
                .foregroundStyle(.secondary)
                .frame(maxWidth: 560)
            if title == "Game Library" && searchText.isEmpty {
                HStack {
                    Button("Add Game Files") { library.addGames() }
                    Button("Scan Folder") { library.addFolder() }
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
            VStack(alignment: .leading, spacing: 12) {
                coverView(game)
                    .frame(height: 245)

                HStack(alignment: .top) {
                    VStack(alignment: .leading, spacing: 4) {
                        Text(game.name)
                            .font(.headline)
                            .lineLimit(2)
                        Text("\(game.fileType) • \(game.fileSizeText)")
                            .font(.caption.bold())
                            .foregroundStyle(.cyan)
                        Text(game.folderText)
                            .font(.caption2)
                            .foregroundStyle(.secondary)
                            .lineLimit(1)
                    }
                    Spacer()
                    if game.isFavorite {
                        Image(systemName: "star.fill")
                            .foregroundStyle(.yellow)
                    }
                }

                HStack {
                    Button("Play") { play(game) }
                        .buttonStyle(.borderedProminent)
                        .disabled(core.isRunning)

                    Menu {
                        Button(game.isFavorite ? "Remove Favorite" : "Add to Favorites") {
                            library.toggleFavorite(game)
                        }
                        Button("Choose Cover…") { library.chooseCover(for: game) }
                        if game.coverURL != nil {
                            Button("Reset Cover") { library.resetCover(for: game) }
                        }
                        Divider()
                        Button("Open Save Folder") { core.openSaveFolder(for: game) }
                        Button("Show Game in Finder") { library.reveal(game) }
                        Divider()
                        Button("Remove from Library", role: .destructive) { library.remove(game) }
                    } label: {
                        Image(systemName: "ellipsis.circle")
                    }
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
                    .fill(
                        LinearGradient(
                            colors: [Color.blue.opacity(0.72), Color.cyan.opacity(0.22)],
                            startPoint: .top,
                            endPoint: .bottom
                        )
                    )
                VStack(spacing: 10) {
                    Image(systemName: "gamecontroller.fill")
                        .font(.system(size: 44))
                    Text(game.fileType)
                        .font(.caption.bold())
                        .foregroundStyle(.cyan)
                }
            }
        }
    }

    private func play(_ game: GameEntry) {
        core.refresh()
        guard core.isReady else {
            coreAlertText = "BluBox macOS 2.2 is ready for testing, but the Xbox 360 emulation core is not bundled yet. The library, profiles, saves, graphics settings, heat guard, controller diagnostics and core bridge are built in. The remaining emulator milestone is the native PowerPC/Xenia-derived core and Mac graphics backend."
            showingCoreAlert = true
            return
        }

        var launchFPS = targetFPS
        var launchPreset = graphicsPreset
        var launchScale = renderScale

        if smartHeatGuard && MacDiagnostics.shouldUseHeatSafePreset {
            launchFPS = 30
            launchPreset = "Performance"
            launchScale = "Native"
            library.statusText = "Smart Heat Guard selected the cool 30 FPS preset for this launch."
        }

        library.markPlayed(game)
        core.launch(
            game: game,
            settings: MacLaunchSettings(
                targetFPS: launchFPS,
                graphicsPreset: launchPreset,
                renderScale: launchScale,
                showFPS: showFPS,
                smartHeatGuard: smartHeatGuard,
                profileName: profileName
            )
        )
    }

    private var profilePage: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Text("Profile")
                    .font(.title2.bold())

                BluCard {
                    HStack(spacing: 18) {
                        ZStack {
                            Circle()
                                .fill(Color.blue.opacity(0.8))
                            Text(String(profileName.prefix(1)).uppercased())
                                .font(.system(size: 34, weight: .bold, design: .rounded))
                        }
                        .frame(width: 78, height: 78)

                        VStack(alignment: .leading, spacing: 7) {
                            TextField("Profile name", text: $profileName)
                                .textFieldStyle(.roundedBorder)
                                .frame(maxWidth: 320)
                            Text("Local BluBox profile")
                                .font(.caption)
                                .foregroundStyle(.cyan)
                            Text("Profile data stays on this Mac. Xbox Live sign-in is not used by this preview.")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                        Spacer()
                    }
                }

                BluCard {
                    VStack(alignment: .leading, spacing: 10) {
                        Text("ACHIEVEMENTS & GAMERSCORE")
                            .font(.caption.bold())
                            .foregroundStyle(.cyan)
                        Text("Ready for native core events")
                            .font(.headline)
                        Text("BluBox 2.2 keeps the local profile structure ready for achievements and gamerscore. They will populate when the macOS emulator core reports game achievement events.")
                            .foregroundStyle(.secondary)
                    }
                }

                Button("Open Profile Data Folder") {
                    MacDataPaths.open(MacDataPaths.profiles())
                }
                .buttonStyle(.bordered)
            }
            .padding(22)
        }
    }

    private var settingsPage: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Text("Settings")
                    .font(.title2.bold())

                BluCard {
                    VStack(alignment: .leading, spacing: 12) {
                        Text("PERFORMANCE")
                            .font(.caption.bold())
                            .foregroundStyle(.cyan)
                        Text("Mac launch profile")
                            .font(.headline)

                        Picker("Graphics", selection: $graphicsPreset) {
                            Text("Performance").tag("Performance")
                            Text("Balanced").tag("Balanced")
                            Text("Quality").tag("Quality")
                        }
                        .pickerStyle(.segmented)

                        Picker("Frame target", selection: $targetFPS) {
                            Text("30 FPS").tag(30)
                            Text("60 FPS").tag(60)
                        }
                        .pickerStyle(.segmented)

                        Picker("Render scale", selection: $renderScale) {
                            Text("Native").tag("Native")
                            Text("HD+").tag("HD+")
                        }
                        .pickerStyle(.segmented)

                        Toggle("Show FPS when supported", isOn: $showFPS)
                        Toggle("Smart Heat Guard", isOn: $smartHeatGuard)

                        Text("Smart Heat Guard switches a launch to Performance, Native scale and 30 FPS when macOS reports serious thermal pressure or Low Power Mode.")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }

                BluCard {
                    VStack(alignment: .leading, spacing: 10) {
                        Text("CONTROLLERS")
                            .font(.caption.bold())
                            .foregroundStyle(.cyan)
                        Text(MacDiagnostics.controllerSummary)
                            .font(.headline)
                        Text(MacDiagnostics.controllerNames)
                            .foregroundStyle(.secondary)
                    }
                }

                BluCard {
                    VStack(alignment: .leading, spacing: 10) {
                        Text("EMULATOR CORE")
                            .font(.caption.bold())
                            .foregroundStyle(.cyan)
                        Text(core.coreURL == nil ? "Core port in development" : "Core executable detected")
                            .font(.headline)
                        Text(core.statusText)
                            .foregroundStyle(.secondary)
                        HStack {
                            Button("Refresh Core Status") { core.refresh() }
                            Button("Open Core Folder") { core.openCoreFolder() }
                            Button("Open Saves") { core.openSavesFolder() }
                        }
                        .buttonStyle(.bordered)
                    }
                }

                BluCard {
                    VStack(alignment: .leading, spacing: 10) {
                        Text("STORAGE")
                            .font(.caption.bold())
                            .foregroundStyle(.cyan)
                        Text("BluBox application data")
                            .font(.headline)
                        HStack {
                            Button("Data Folder") { core.openDataFolder() }
                            Button("Shader Cache") { core.openShaderCacheFolder() }
                            Button("Logs") { core.openLogsFolder() }
                        }
                        .buttonStyle(.bordered)
                    }
                }

                BluCard {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("MAC PREVIEW")
                            .font(.caption.bold())
                            .foregroundStyle(.cyan)
                        Text("BluBox 360 macOS 2.2 Preview")
                            .font(.headline)
                        Text("Universal frontend for Apple Silicon and Intel Macs. Android stays on its separate release track.")
                            .foregroundStyle(.secondary)
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
                    Text("Diagnostics")
                        .font(.title2.bold())
                    Spacer()
                    Button("Refresh") { core.refresh() }
                }

                BluCard {
                    diagnosticRow("Architecture", MacDiagnostics.architecture)
                    diagnosticRow("macOS", MacDiagnostics.macOSVersion)
                    diagnosticRow("Metal GPU", MacDiagnostics.metalDevice)
                    diagnosticRow("Memory", MacDiagnostics.memory)
                    diagnosticRow("Controllers", MacDiagnostics.controllerSummary)
                    diagnosticRow("Thermal state", MacDiagnostics.thermalState)
                    diagnosticRow("Low Power Mode", MacDiagnostics.lowPowerMode)
                    diagnosticRow("Smart Heat Guard", smartHeatGuard ? "On" : "Off")
                    diagnosticRow("Emulator core", core.coreURL == nil ? "Not connected" : "Detected")
                }

                BluCard {
                    VStack(alignment: .leading, spacing: 8) {
                        HStack {
                            Text("CORE LOG")
                                .font(.caption.bold())
                                .foregroundStyle(.cyan)
                            Spacer()
                            Button("Clear") { core.clearConsole() }
                                .buttonStyle(.borderless)
                        }
                        ScrollView {
                            Text(core.consoleText.isEmpty
                                 ? "No native core session has run yet."
                                 : core.consoleText)
                                .font(.system(.caption, design: .monospaced))
                                .textSelection(.enabled)
                                .frame(maxWidth: .infinity, alignment: .leading)
                        }
                        .frame(minHeight: 160)
                    }
                }
            }
            .padding(22)
        }
    }

    private func diagnosticRow(_ label: String, _ value: String) -> some View {
        HStack(alignment: .firstTextBaseline) {
            Text(label)
                .foregroundStyle(.secondary)
            Spacer()
            Text(value)
                .fontWeight(.semibold)
                .multilineTextAlignment(.trailing)
        }
        .padding(.vertical, 4)
    }

    private var statusBar: some View {
        HStack(spacing: 8) {
            Circle()
                .fill(core.coreURL == nil ? Color.orange : Color.green)
                .frame(width: 8, height: 8)
            Text(core.isRunning ? core.statusText : library.statusText)
                .font(.caption)
                .foregroundStyle(.secondary)
            Spacer()
            Text("Profile: \(profileName)")
                .font(.caption2)
                .foregroundStyle(.secondary)
            Text("•")
                .foregroundStyle(.secondary)
            Text(core.coreURL == nil ? "Core port: pending" : "Core: connected")
                .font(.caption2)
                .foregroundStyle(.secondary)
        }
        .padding(.horizontal, 22)
        .padding(.vertical, 9)
        .background(Color.black.opacity(0.18))
    }
}
