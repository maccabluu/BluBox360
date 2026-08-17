import AppKit
import SwiftUI

private enum SidebarPage: String, CaseIterable, Identifiable {
    case library = "Library"
    case recent = "Recently Played"
    case favorites = "Favorites"
    case settings = "Settings"
    case diagnostics = "Diagnostics"

    var id: String { rawValue }

    var icon: String {
        switch self {
        case .library: return "square.grid.2x2"
        case .recent: return "clock"
        case .favorites: return "star.fill"
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

    @AppStorage("BluBoxMacTargetFPS") private var targetFPS = 60
    @AppStorage("BluBoxMacGraphicsPreset") private var graphicsPreset = "Balanced"
    @AppStorage("BluBoxMacShowFPS") private var showFPS = true

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
        .frame(minWidth: 190)
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
                Text("macOS 0.2 Preview • Apple Silicon")
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(.cyan)
            }

            Spacer()

            if core.isRunning {
                Button("Stop Game") { core.stop() }
                    .buttonStyle(.bordered)
                    .controlSize(.large)
            }

            Menu("Add Games") {
                Button("Add Game Files…") { library.addGames() }
                Button("Scan Game Folder…") { library.addFolder() }
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
        case .settings:
            settingsPage
        case .diagnostics:
            diagnosticsPage
        }
    }

    private func gameCollection(title: String, games: [GameEntry]) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                Text(title)
                    .font(.title2.bold())
                Spacer()
                Text("\(games.count) game\(games.count == 1 ? "" : "s")")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            .padding(.horizontal, 22)
            .padding(.top, 18)
            .padding(.bottom, 10)

            if games.isEmpty {
                emptyLibrary(title: title)
            } else {
                ScrollView {
                    LazyVGrid(columns: columns, alignment: .leading, spacing: 16) {
                        ForEach(games) { game in
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
            Text(title == "Favorites" ? "No favorites yet" : "Nothing here yet")
                .font(.title2.bold())
            Text(title == "Recently Played"
                 ? "Games will appear here after the native core begins launching them."
                 : title == "Favorites"
                 ? "Mark games as favorites from the menu on a game card."
                 : "Add your own Xbox 360 ISO, ZAR or XEX files to build your Mac library.")
                .multilineTextAlignment(.center)
                .foregroundStyle(.secondary)
                .frame(maxWidth: 560)
            if title == "Game Library" {
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
                        Text(game.fileType)
                            .font(.caption.bold())
                            .foregroundStyle(.cyan)
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
                        Button("Show in Finder") { library.reveal(game) }
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
            coreAlertText = "The native Mac app is ready, but the Xbox 360 emulation core is not bundled yet. BluBox now has a real core bridge ready for the macOS port. The next core milestone is PowerPC/JIT and Vulkan-to-Metal work."
            showingCoreAlert = true
            return
        }

        library.markPlayed(game)
        core.launch(
            game: game,
            settings: MacLaunchSettings(
                targetFPS: targetFPS,
                graphicsPreset: graphicsPreset,
                showFPS: showFPS
            )
        )
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

                        Toggle("Show FPS when supported", isOn: $showFPS)
                        Text("These settings are passed to the native core bridge when the macOS emulator backend is connected.")
                            .font(.caption)
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
                        }
                        .buttonStyle(.bordered)
                    }
                }

                BluCard {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("MAC PREVIEW")
                            .font(.caption.bold())
                            .foregroundStyle(.cyan)
                        Text("BluBox 360 macOS 0.2 Preview")
                            .font(.headline)
                        Text("Native SwiftUI frontend for Apple Silicon. Android remains a separate release and is not changed by this branch.")
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
                    diagnosticRow("Metal GPU", MacDiagnostics.metalDevice)
                    diagnosticRow("Memory", MacDiagnostics.memory)
                    diagnosticRow("Controllers", MacDiagnostics.controllerSummary)
                    diagnosticRow("Thermal state", MacDiagnostics.thermalState)
                    diagnosticRow("Low Power Mode", MacDiagnostics.lowPowerMode)
                    diagnosticRow("Emulator core", core.coreURL == nil ? "Not connected" : "Detected")
                }

                BluCard {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("CORE LOG")
                            .font(.caption.bold())
                            .foregroundStyle(.cyan)
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
            Text(core.coreURL == nil ? "Core port: pending" : "Core: connected")
                .font(.caption2)
                .foregroundStyle(.secondary)
        }
        .padding(.horizontal, 22)
        .padding(.vertical, 9)
        .background(Color.black.opacity(0.18))
    }
}
