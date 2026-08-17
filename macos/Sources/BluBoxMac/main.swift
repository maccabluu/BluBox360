import SwiftUI
import AppKit

struct GameEntry: Identifiable, Codable, Hashable {
    let id: UUID
    let path: String

    init(id: UUID = UUID(), path: String) {
        self.id = id
        self.path = path
    }

    var url: URL { URL(fileURLWithPath: path) }
    var name: String { url.deletingPathExtension().lastPathComponent }
    var fileType: String {
        let ext = url.pathExtension.uppercased()
        return ext.isEmpty ? "GAME" : ext
    }
}

@MainActor
final class GameLibrary: ObservableObject {
    @Published private(set) var games: [GameEntry] = []
    @Published var selectedGame: GameEntry?
    @Published var statusText = "macOS core port in development"

    private let defaultsKey = "BluBoxMacGamePaths"

    init() {
        load()
    }

    func addGames() {
        let panel = NSOpenPanel()
        panel.title = "Add Xbox 360 games to BluBox"
        panel.prompt = "Add Games"
        panel.allowsMultipleSelection = true
        panel.canChooseDirectories = false
        panel.canChooseFiles = true
        panel.resolvesAliases = true

        guard panel.runModal() == .OK else { return }
        let supported = Set(["iso", "zar", "xex"])
        let added = panel.urls.filter { supported.contains($0.pathExtension.lowercased()) }
        guard !added.isEmpty else {
            statusText = "Choose an ISO, ZAR or XEX game file."
            return
        }

        var known = Set(games.map(\.path))
        for url in added where !known.contains(url.path) {
            games.append(GameEntry(path: url.path))
            known.insert(url.path)
        }
        games.sort { $0.name.localizedCaseInsensitiveCompare($1.name) == .orderedAscending }
        save()
        statusText = "Added \(added.count) game file\(added.count == 1 ? "" : "s")."
    }

    func remove(_ game: GameEntry) {
        games.removeAll { $0.id == game.id }
        if selectedGame?.id == game.id { selectedGame = nil }
        save()
    }

    func reveal(_ game: GameEntry) {
        NSWorkspace.shared.activateFileViewerSelecting([game.url])
    }

    private func load() {
        let paths = UserDefaults.standard.stringArray(forKey: defaultsKey) ?? []
        games = paths.map { GameEntry(path: $0) }
            .filter { FileManager.default.fileExists(atPath: $0.path) }
    }

    private func save() {
        UserDefaults.standard.set(games.map(\.path), forKey: defaultsKey)
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
    @State private var showingCoreMessage = false

    private let columns = [
        GridItem(.adaptive(minimum: 180, maximum: 220), spacing: 16)
    ]

    var body: some View {
        ZStack {
            LinearGradient(
                colors: [Color(red: 0.02, green: 0.05, blue: 0.12), Color(red: 0.02, green: 0.18, blue: 0.34)],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            .ignoresSafeArea()

            VStack(spacing: 0) {
                header
                Divider().overlay(Color.white.opacity(0.08))
                mainContent
                statusBar
            }
        }
        .preferredColorScheme(.dark)
        .alert("Xbox 360 core not connected yet", isPresented: $showingCoreMessage) {
            Button("OK", role: .cancel) { }
        } message: {
            Text("This first macOS preview builds the native BluBox app, library and Mac packaging. Game emulation will be enabled after the Xenia-derived core and graphics backend are ported to macOS.")
        }
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
                Text("macOS 0.1 Preview • Apple Silicon")
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(.cyan)
            }

            Spacer()

            Button("Add Games") { library.addGames() }
                .buttonStyle(.borderedProminent)
                .controlSize(.large)
        }
        .padding(.horizontal, 22)
        .padding(.vertical, 14)
    }

    @ViewBuilder
    private var mainContent: some View {
        if library.games.isEmpty {
            VStack(spacing: 18) {
                Spacer()
                Image(systemName: "rectangle.stack.badge.plus")
                    .font(.system(size: 58, weight: .light))
                    .foregroundStyle(.cyan)
                Text("Your Mac BluBox library is empty")
                    .font(.title2.bold())
                Text("Add your own Xbox 360 ISO, ZAR or XEX files. The macOS emulation core is still being ported, so this preview is for the native app and library first.")
                    .multilineTextAlignment(.center)
                    .foregroundStyle(.secondary)
                    .frame(maxWidth: 570)
                Button("Add Games") { library.addGames() }
                    .buttonStyle(.borderedProminent)
                    .controlSize(.large)
                Spacer()
            }
            .padding(30)
        } else {
            ScrollView {
                LazyVGrid(columns: columns, alignment: .leading, spacing: 16) {
                    ForEach(library.games) { game in
                        gameCard(game)
                    }
                }
                .padding(22)
            }
        }
    }

    private func gameCard(_ game: GameEntry) -> some View {
        BluCard {
            VStack(alignment: .leading, spacing: 12) {
                ZStack {
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .fill(
                            LinearGradient(
                                colors: [Color.blue.opacity(0.7), Color.cyan.opacity(0.25)],
                                startPoint: .top,
                                endPoint: .bottom
                            )
                        )
                    VStack(spacing: 10) {
                        Image(systemName: "gamecontroller.fill")
                            .font(.system(size: 42))
                        Text(game.fileType)
                            .font(.caption.bold())
                            .foregroundStyle(.cyan)
                    }
                }
                .frame(height: 215)

                Text(game.name)
                    .font(.headline)
                    .lineLimit(2)
                    .frame(maxWidth: .infinity, alignment: .leading)

                HStack {
                    Button("Play") {
                        library.selectedGame = game
                        showingCoreMessage = true
                    }
                    .buttonStyle(.borderedProminent)

                    Menu {
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

    private var statusBar: some View {
        HStack {
            Circle()
                .fill(Color.orange)
                .frame(width: 8, height: 8)
            Text(library.statusText)
                .font(.caption)
                .foregroundStyle(.secondary)
            Spacer()
            Text("Blu Studio • Public preview development")
                .font(.caption2)
                .foregroundStyle(.secondary)
        }
        .padding(.horizontal, 22)
        .padding(.vertical, 9)
        .background(Color.black.opacity(0.18))
    }
}

@main
struct BluBoxMacApp: App {
    @StateObject private var library = GameLibrary()

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(library)
                .frame(minWidth: 920, minHeight: 620)
        }
        .windowStyle(.titleBar)
        .defaultSize(width: 1180, height: 760)
    }
}
