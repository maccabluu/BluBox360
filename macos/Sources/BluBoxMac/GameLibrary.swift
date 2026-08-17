import AppKit
import Foundation
import UniformTypeIdentifiers

struct GameEntry: Identifiable, Codable, Hashable {
    let id: UUID
    let path: String
    var lastPlayed: Date?
    var isFavorite: Bool
    var coverPath: String?

    init(
        id: UUID = UUID(),
        path: String,
        lastPlayed: Date? = nil,
        isFavorite: Bool = false,
        coverPath: String? = nil
    ) {
        self.id = id
        self.path = path
        self.lastPlayed = lastPlayed
        self.isFavorite = isFavorite
        self.coverPath = coverPath
    }

    var url: URL { URL(fileURLWithPath: path) }

    var name: String {
        if url.lastPathComponent.lowercased() == "default.xex" {
            return url.deletingLastPathComponent().lastPathComponent
        }
        return url.deletingPathExtension().lastPathComponent
    }

    var fileType: String {
        let ext = url.pathExtension.uppercased()
        return ext.isEmpty ? "GAME" : ext
    }

    var coverURL: URL? {
        guard let coverPath, FileManager.default.fileExists(atPath: coverPath) else {
            return nil
        }
        return URL(fileURLWithPath: coverPath)
    }
}

@MainActor
final class GameLibrary: ObservableObject {
    @Published private(set) var games: [GameEntry] = []
    @Published var selectedGame: GameEntry?
    @Published var statusText = "BluBox 360 macOS preview ready"

    private let libraryKey = "BluBoxMacGameLibraryV2"
    private let legacyKey = "BluBoxMacGamePaths"
    private let supportedExtensions = Set(["iso", "zar", "xex"])

    init() {
        load()
    }

    var recentGames: [GameEntry] {
        games
            .filter { $0.lastPlayed != nil }
            .sorted { ($0.lastPlayed ?? .distantPast) > ($1.lastPlayed ?? .distantPast) }
    }

    var favoriteGames: [GameEntry] {
        games.filter(\.isFavorite)
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
        importURLs(panel.urls)
    }

    func addFolder() {
        let panel = NSOpenPanel()
        panel.title = "Choose an Xbox 360 game folder"
        panel.prompt = "Scan Folder"
        panel.allowsMultipleSelection = false
        panel.canChooseDirectories = true
        panel.canChooseFiles = false
        panel.resolvesAliases = true

        guard panel.runModal() == .OK, let folder = panel.url else { return }

        let keys: [URLResourceKey] = [.isRegularFileKey, .isDirectoryKey]
        guard let enumerator = FileManager.default.enumerator(
            at: folder,
            includingPropertiesForKeys: keys,
            options: [.skipsHiddenFiles, .skipsPackageDescendants]
        ) else {
            statusText = "BluBox could not scan that folder."
            return
        }

        var matches: [URL] = []
        for case let fileURL as URL in enumerator {
            let ext = fileURL.pathExtension.lowercased()
            if supportedExtensions.contains(ext) {
                matches.append(fileURL)
            }
        }
        importURLs(matches)
    }

    func importURLs(_ urls: [URL]) {
        let supported = urls.filter { supportedExtensions.contains($0.pathExtension.lowercased()) }
        guard !supported.isEmpty else {
            statusText = "Choose an ISO, ZAR or XEX game file."
            return
        }

        var known = Set(games.map(\.path))
        var added = 0
        for url in supported where !known.contains(url.path) {
            games.append(GameEntry(path: url.path))
            known.insert(url.path)
            added += 1
        }
        sortLibrary()
        save()
        statusText = added == 0
            ? "Those games are already in your library."
            : "Added \(added) game\(added == 1 ? "" : "s") to the Mac library."
    }

    func remove(_ game: GameEntry) {
        games.removeAll { $0.id == game.id }
        if selectedGame?.id == game.id { selectedGame = nil }
        save()
        statusText = "Removed \(game.name) from the library."
    }

    func toggleFavorite(_ game: GameEntry) {
        guard let index = games.firstIndex(where: { $0.id == game.id }) else { return }
        games[index].isFavorite.toggle()
        save()
    }

    func markPlayed(_ game: GameEntry) {
        guard let index = games.firstIndex(where: { $0.id == game.id }) else { return }
        games[index].lastPlayed = Date()
        selectedGame = games[index]
        save()
    }

    func reveal(_ game: GameEntry) {
        NSWorkspace.shared.activateFileViewerSelecting([game.url])
    }

    func chooseCover(for game: GameEntry) {
        let panel = NSOpenPanel()
        panel.title = "Choose cover artwork"
        panel.prompt = "Use Cover"
        panel.allowsMultipleSelection = false
        panel.canChooseDirectories = false
        panel.canChooseFiles = true
        panel.allowedContentTypes = [.png, .jpeg, .image]

        guard panel.runModal() == .OK, let source = panel.url else { return }
        do {
            let root = try coverDirectory()
            let ext = source.pathExtension.isEmpty ? "png" : source.pathExtension.lowercased()
            let destination = root.appendingPathComponent("\(game.id.uuidString).\(ext)")
            if FileManager.default.fileExists(atPath: destination.path) {
                try FileManager.default.removeItem(at: destination)
            }
            try FileManager.default.copyItem(at: source, to: destination)
            guard let index = games.firstIndex(where: { $0.id == game.id }) else { return }
            games[index].coverPath = destination.path
            save()
            statusText = "Cover artwork updated for \(game.name)."
        } catch {
            statusText = "Cover artwork could not be saved: \(error.localizedDescription)"
        }
    }

    func resetCover(for game: GameEntry) {
        guard let index = games.firstIndex(where: { $0.id == game.id }) else { return }
        if let path = games[index].coverPath {
            try? FileManager.default.removeItem(atPath: path)
        }
        games[index].coverPath = nil
        save()
        statusText = "Cover artwork reset for \(game.name)."
    }

    private func coverDirectory() throws -> URL {
        let support = try FileManager.default.url(
            for: .applicationSupportDirectory,
            in: .userDomainMask,
            appropriateFor: nil,
            create: true
        )
        let folder = support
            .appendingPathComponent("BluBox 360", isDirectory: true)
            .appendingPathComponent("Covers", isDirectory: true)
        try FileManager.default.createDirectory(
            at: folder,
            withIntermediateDirectories: true,
            attributes: nil
        )
        return folder
    }

    private func sortLibrary() {
        games.sort { $0.name.localizedCaseInsensitiveCompare($1.name) == .orderedAscending }
    }

    private func load() {
        if let data = UserDefaults.standard.data(forKey: libraryKey),
           let decoded = try? JSONDecoder().decode([GameEntry].self, from: data) {
            games = decoded.filter { FileManager.default.fileExists(atPath: $0.path) }
            sortLibrary()
            return
        }

        let legacyPaths = UserDefaults.standard.stringArray(forKey: legacyKey) ?? []
        games = legacyPaths
            .filter { FileManager.default.fileExists(atPath: $0) }
            .map { GameEntry(path: $0) }
        sortLibrary()
        save()
    }

    private func save() {
        guard let data = try? JSONEncoder().encode(games) else { return }
        UserDefaults.standard.set(data, forKey: libraryKey)
    }
}
