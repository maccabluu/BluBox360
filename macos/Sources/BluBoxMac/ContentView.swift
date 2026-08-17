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
            .overlay {
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .stroke(Color.white.opacity(0.09), lineWidth: 1)
                    .allowsHitTesting(false)
            }
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
    @AppStorage("BluBoxMacRenderScale") private var renderScale = "Native"
    @AppStorage("BluBoxMacShowFPS") private var showFPS = true
    @AppStorage("BluBoxMacSmartHeatGuard") private var smartHeatGuard = true
    @AppStorage("BluBoxMacProfileName") private var profileName = "Player 1"

    private let columns = [
        GridItem(.adaptive(minimum: 190, maximum: 240), spacing: 16)
    ]

    var body: some View {
        HStack(spacing: 0) {
            sidebar
            Divider()
            detail
        }
        .background {
            LinearGradient(
                colors: [
                    Color(red: 0.02, green: 0.05, blue: 0.12),
                    Color(red: 0.02, green: 0.18, blue: 0.34)
                ],
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
            library.setStatus("BluBox 360 macOS 2.3 ready")
            core.refresh()
        }
    }

    private var sidebar: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 10) {
                ZStack {
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .fill(Color.blue.opacity(0.85))
                        .allowsHitTesting(false)
                    Text("B")
                        .font(.system(size: 24, weight: .black, design: .rounded))
                        .allowsHitTesting(false)
                }
                .frame(width: 46, height: 46)

                VStack(alignment: .leading, spacing: 2) {
                    Text("BluBox 360")
                        .font(.headline)
                    Text("macOS 2.3 Preview")
                        .font(.caption)
                        .foregroundStyle(.cyan)
                }
            }
            .padding(.bottom, 6)

            ForEach(SidebarPage.allCases) { item in
                Button {
                    page = item
                } label: {
                    HStack(spacing: 10) {
                        Image(systemName: item.icon)
                            .frame(width: 20)
                        Text(item.rawValue)
                        Spacer()
                    }
                    .padding(.horizontal, 12)
                    .padding(.vertical, 9)
                    .background(page == item ? Color.blue.opacity(0.28) : Color.clear)
                    .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
            }

            Spacer()

            VStack(alignment: .leading, spacing: 6) {
                Label(
                    core.coreURL == nil ? "Core missing" : "Core bootstrap ready",
                    systemImage: core.coreURL == nil ? "exclamationmark.triangle" : "checkmark.circle.fill"
                )
                .font(.caption.bold())
                .foregroundStyle(core.coreURL == nil ? .orange : .green)

                Text("Universal • Apple Silicon + Intel")
                    .font(.caption2)
                    .foregroundStyle(.secondary)
            }
        }
        .padding(16)
        .frame(width: 220)
        .background(Color.black.opacity(0.22))
    }

    private var detail: some View {
        VStack(spacing: 0) {
            header
            Divider().overlay(Color.white.opacity(0.08)).allowsHitTesting(false)
            pageContent
            statusBar
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private var header: some View {
        HStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 2) {
                Text(page.rawValue)
                    .font(.title2.bold())
                Text("BluBox 360 macOS 2.3 Preview")
                    .font(.caption)
                    .foregroundStyle(.cyan)
            }

            Spacer()

            TextField("Search games", text: $searchText)
                .textFieldStyle(.roundedBorder)
                .frame(width: 220)

            Menu("Library") {
                Button("Add Game Files…") { library.addGames() }
                Button("Scan Game Folder…") { library.addFolder() }
                Divider()
                Button("Refresh Library") { library.refreshLibrary() }
            }
            .buttonStyle(.borderedProminent)
            .controlSize(.large)

            if core.isRunning {
                Button("Stop Core") { core.stop() }
                    .buttonStyle(.bordered)
                    .controlSize(.large)
            }
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 14)
        .background(Color.black.opacity(0.14))
    }

    @ViewBuilder
    private var pageContent: some View {
        switch page {
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
        let trimmed = searchText.trimmingCharacters(in: .whitespacesAndNewlines)
        let filtered = trimmed.isEmpty ? games : games.filter {
            $0.name.localizedCaseInsensitiveContains(trimmed) ||
            $0.fileType.localizedCaseInsensitiveContains(trimmed)
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
            return filtered.sorted {
                $0.name.localizedCaseInsensitiveCompare($1.name) == .orderedAscending
            }
        }
    }

    private func gameCollection(title: String, games: [GameEntry]) -> some View {
        let displayGames = visibleGames(games)
        return VStack(alignment: .leading, spacing: 0) {
            HStack {
                Text("\(displayGames.count) game\(displayGames.count == 1 ? "" : "s")")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                Spacer()
                Picker("Sort", selection: $sortMode) {
                    Text("Name").tag("Name")
                    Text("Recently Played").tag("Recently Played")
                    Text("Favorites First").tag("Favorites First")
                }
                .frame(width: 180)
            }
            .padding(.horizontal, 20)
            .padding(.top, 14)

            if displayGames.isEmpty {
                emptyLibrary(title: title)
            } else {
                ScrollView {
                    LazyVGrid(columns: columns, alignment: .leading, spacing: 16) {
                        ForEach(displayGames) { game in
                            gameCard(game)
                        }
                    }
                    .padding(20)
                }
            }
        }
    }

    private func emptyLibrary(title: String) -> some View {
        VStack(spacing: 14) {
            Spacer()
            Image(systemName: title == "Favorites" ? "star" : "rectangle.stack.badge.plus")
                .font(.system(size: 50, weight: .light))
                .foregroundStyle(.cyan)
                .allowsHitTesting(false)
            Text(searchText.isEmpty ? "Nothing here yet" : "No games match your search")
                .font(.title2.bold())
            Text("Add your own Xbox 360 ISO, ZAR or XEX files. BluBox 2.3 can now pass them into the bundled native core bootstrap for a real host/JIT boot-stage test.")
                .multilineTextAlignment(.center)
                .foregroundStyle(.secondary)
                .frame(maxWidth: 600)
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
            VStack(alignment: .leading, spacing: 11) {
                coverView(game)
                    .frame(height: 230)
                    .allowsHitTesting(false)

                Text(game.name)
                    .font(.headline)
                    .lineLimit(2)

                Text("\(game.fileType) • \(game.fileSizeText)")
                    .font(.caption.bold())
                    .foregroundStyle(.cyan)

                HStack {
                    Button("Test Boot") { testBoot(game) }
                        .buttonStyle(.borderedProminent)
                        .disabled(core.isRunning)

                    Button {
                        library.toggleFavorite(game)
                    } label: {
                        Image(systemName: game.isFavorite ? "star.fill" : "star")
                    }
                    .buttonStyle(.bordered)

                    Menu {
                        Button("Choose Cover…") { library.chooseCover(for: game) }
                        if game.coverURL != nil {
                            Button("Reset Cover") { library.resetCover(for: game) }
                        }
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
                VStack(spacing: 9) {
                    Image(systemName: "gamecontroller.fill")
                        .font(.system(size: 42))
                    Text(game.fileType)
                        .font(.caption.bold())
                        .foregroundStyle(.cyan)
                }
            }
        }
    }

    private func testBoot(_ game: GameEntry) {
        core.refresh()
        guard core.isReady else {
            alertText = "The BluBox 2.3 native core bootstrap was not found inside the app. Reinstall the 2.3 build and try again."
            showingAlert = true
            return
        }

        var launchFPS = targetFPS
        var launchPreset = graphicsPreset
        var launchScale = renderScale

        if smartHeatGuard && MacDiagnostics.shouldUseHeatSafePreset {
            launchFPS = 30
            launchPreset = "Performance"
            launchScale = "Native"
            library.setStatus("Smart Heat Guard selected the cool 30 FPS preset.")
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
                BluCard {
                    HStack(spacing: 18) {
                        ZStack {
                            Circle().fill(Color.blue.opacity(0.8)).allowsHitTesting(false)
                            Text(String(profileName.prefix(1)).uppercased())
                                .font(.system(size: 34, weight: .bold, design: .rounded))
                                .allowsHitTesting(false)
                        }
                        .frame(width: 78, height: 78)

                        VStack(alignment: .leading, spacing: 8) {
                            Text("Local profile")
                                .font(.headline)
                            TextField("Profile name", text: $profileName)
                                .textFieldStyle(.roundedBorder)
                                .frame(maxWidth: 320)
                            Text("Profile data stays on this Mac.")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                        Spacer()
                    }
                }

                BluCard {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("ACHIEVEMENTS & GAMERSCORE")
                            .font(.caption.bold())
                            .foregroundStyle(.cyan)
                        Text("Prepared for native core events")
                            .font(.headline)
                        Text("The local profile structure is ready for achievement events when full Xbox 360 execution is connected later.")
                            .foregroundStyle(.secondary)
                    }
                }

                Button("Open Profile Data Folder") {
                    MacDataPaths.open(MacDataPaths.profiles())
                }
                .buttonStyle(.bordered)
            }
            .padding(20)
        }
    }

    private var settingsPage: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                BluCard {
                    VStack(alignment: .leading, spacing: 12) {
                        Text("PERFORMANCE")
                            .font(.caption.bold())
                            .foregroundStyle(.cyan)

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
                    }
                }

                BluCard {
                    VStack(alignment: .leading, spacing: 10) {
                        Text("NATIVE CORE")
                            .font(.caption.bold())
                            .foregroundStyle(.cyan)
                        Text(core.coreURL == nil ? "Bootstrap not detected" : "2.3 bootstrap detected")
                            .font(.headline)
                        Text(core.statusText)
                            .foregroundStyle(.secondary)
                        HStack {
                            Button("Refresh") { core.refresh() }
                            Button("Core Folder") { core.openCoreFolder() }
                            Button("Saves") { core.openSavesFolder() }
                        }
                        .buttonStyle(.bordered)
                    }
                }

                BluCard {
                    VStack(alignment: .leading, spacing: 10) {
                        Text("STORAGE")
                            .font(.caption.bold())
                            .foregroundStyle(.cyan)
                        HStack {
                            Button("Data Folder") { core.openDataFolder() }
                            Button("Shader Cache") { core.openShaderCacheFolder() }
                            Button("Logs") { core.openLogsFolder() }
                        }
                        .buttonStyle(.bordered)
                    }
                }

                BluCard {
                    VStack(alignment: .leading, spacing: 7) {
                        Text("ABOUT")
                            .font(.caption.bold())
                            .foregroundStyle(.cyan)
                        Text("BluBox 360 macOS 2.3 Preview")
                            .font(.headline)
                        Text("2.3 fixes mouse interaction, keeps the Universal Mac build, and bundles the first native core bootstrap with host/JIT and game-file boot-stage validation. Full Xbox 360 CPU/GPU execution is still in development.")
                            .foregroundStyle(.secondary)
                    }
                }
            }
            .padding(20)
        }
    }

    private var diagnosticsPage: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                HStack {
                    Text("System")
                        .font(.headline)
                    Spacer()
                    Button("Refresh Core") { core.refresh() }
                }

                BluCard {
                    diagnosticRow("Architecture", MacDiagnostics.architecture)
                    diagnosticRow("macOS", MacDiagnostics.macOSVersion)
                    diagnosticRow("Metal GPU", MacDiagnostics.metalDevice)
                    diagnosticRow("Memory", MacDiagnostics.memory)
                    diagnosticRow("Controllers", MacDiagnostics.controllerSummary)
                    diagnosticRow("Thermal state", MacDiagnostics.thermalState)
                    diagnosticRow("Low Power Mode", MacDiagnostics.lowPowerMode)
                    diagnosticRow("Native core", core.coreURL == nil ? "Not detected" : "Bootstrap detected")
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
                            Text(core.consoleText.isEmpty ? "Run Test Boot on a game to exercise the 2.3 native core bootstrap." : core.consoleText)
                                .font(.system(.caption, design: .monospaced))
                                .textSelection(.enabled)
                                .frame(maxWidth: .infinity, alignment: .leading)
                        }
                        .frame(minHeight: 180)
                    }
                }
            }
            .padding(20)
        }
    }

    private func diagnosticRow(_ label: String, _ value: String) -> some View {
        HStack(alignment: .firstTextBaseline) {
            Text(label).foregroundStyle(.secondary)
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
                .allowsHitTesting(false)
            Text(core.isRunning ? core.statusText : library.statusText)
                .font(.caption)
                .foregroundStyle(.secondary)
            Spacer()
            Text("Profile: \(profileName)")
                .font(.caption2)
                .foregroundStyle(.secondary)
            Text("•").foregroundStyle(.secondary)
            Text(core.coreURL == nil ? "Core: missing" : "Core bootstrap: ready")
                .font(.caption2)
                .foregroundStyle(.secondary)
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 9)
        .background(Color.black.opacity(0.22))
    }
}
