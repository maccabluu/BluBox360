import AppKit
import CryptoKit
import Foundation
import SwiftUI

private struct MacGitHubRelease: Decodable {
    struct Asset: Decodable {
        let name: String
        let browserDownloadURL: URL

        enum CodingKeys: String, CodingKey {
            case name
            case browserDownloadURL = "browser_download_url"
        }
    }

    let tagName: String
    let name: String?
    let body: String?
    let draft: Bool
    let prerelease: Bool
    let assets: [Asset]

    enum CodingKeys: String, CodingKey {
        case tagName = "tag_name"
        case name
        case body
        case draft
        case prerelease
        case assets
    }
}

@MainActor
final class MacUpdateManager: ObservableObject {
    @Published private(set) var latestVersion = "Checking…"
    @Published private(set) var latestTag = ""
    @Published private(set) var releaseNotes = ""
    @Published private(set) var statusText = "Ready to check for updates"
    @Published private(set) var isChecking = false
    @Published private(set) var isInstalling = false
    @Published private(set) var updateAvailable = false

    private var release: MacGitHubRelease?
    private var updateWindow: NSWindow?
    private var automaticCheckStarted = false

    var currentVersion: String {
        (Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String) ?? "0.0.0"
    }

    var currentBuild: String {
        (Bundle.main.object(forInfoDictionaryKey: "CFBundleVersion") as? String) ?? "0"
    }

    func startAutomaticCheck() {
        guard !automaticCheckStarted else { return }
        automaticCheckStarted = true
        Task {
            try? await Task.sleep(nanoseconds: 1_500_000_000)
            await checkForUpdates(silent: true)
        }
    }

    func presentUpdateWindow() {
        if let updateWindow {
            updateWindow.makeKeyAndOrderFront(nil)
            NSApp.activate(ignoringOtherApps: true)
        } else {
            let controller = NSHostingController(rootView: MacUpdateView(manager: self))
            let window = NSWindow(
                contentRect: NSRect(x: 0, y: 0, width: 560, height: 500),
                styleMask: [.titled, .closable, .miniaturizable],
                backing: .buffered,
                defer: false
            )
            window.title = "BluBox 360 Software Update"
            window.contentViewController = controller
            window.center()
            window.isReleasedWhenClosed = false
            updateWindow = window
            window.makeKeyAndOrderFront(nil)
            NSApp.activate(ignoringOtherApps: true)
        }

        Task { await checkForUpdates(silent: false) }
    }

    func checkForUpdates(silent: Bool) async {
        guard !isChecking && !isInstalling else { return }
        isChecking = true
        statusText = "Checking GitHub for the latest macOS preview…"
        defer { isChecking = false }

        do {
            var request = URLRequest(url: URL(string: "https://api.github.com/repos/maccabluu/BluBox360/releases?per_page=30")!)
            request.setValue("application/vnd.github+json", forHTTPHeaderField: "Accept")
            request.setValue("BluBox360-macOS/\(currentVersion)", forHTTPHeaderField: "User-Agent")
            request.timeoutInterval = 20

            let (data, response) = try await URLSession.shared.data(for: request)
            guard let http = response as? HTTPURLResponse, (200...299).contains(http.statusCode) else {
                throw UpdateError.message("GitHub returned an update-check error.")
            }

            let releases = try JSONDecoder().decode([MacGitHubRelease].self, from: data)
            let macReleases = releases
                .filter { !$0.draft && $0.tagName.lowercased().hasPrefix("mac-v") }
                .sorted { lhs, rhs in
                    compareVersions(version(from: lhs.tagName), version(from: rhs.tagName)) == .orderedDescending
                }

            guard let newest = macReleases.first else {
                release = nil
                latestVersion = currentVersion
                latestTag = ""
                releaseNotes = "No public macOS preview has been published yet."
                updateAvailable = false
                statusText = "BluBox 360 \(currentVersion) is installed. No newer Mac preview is published."
                return
            }

            let newestVersion = version(from: newest.tagName)
            release = newest
            latestVersion = newestVersion
            latestTag = newest.tagName
            releaseNotes = newest.body?.trimmingCharacters(in: .whitespacesAndNewlines) ?? "No release notes were supplied."
            updateAvailable = compareVersions(newestVersion, currentVersion) == .orderedDescending

            if updateAvailable {
                statusText = "Update available: BluBox 360 \(newestVersion)"
                if silent && UserDefaults.standard.string(forKey: "BluBoxMacLastShownUpdate") != newest.tagName {
                    UserDefaults.standard.set(newest.tagName, forKey: "BluBoxMacLastShownUpdate")
                    presentUpdateWindow()
                }
            } else {
                statusText = "You are up to date. Installed: \(currentVersion) • Latest: \(newestVersion)"
            }
        } catch {
            statusText = "Update check failed: \(error.localizedDescription)"
            if !silent {
                releaseNotes = "BluBox could not reach the update service. Check your internet connection and try again."
            }
        }
    }

    func installLatestUpdate() async {
        guard !isInstalling else { return }
        guard let release, updateAvailable else {
            statusText = "There is no newer update to install."
            return
        }

        guard let zipAsset = release.assets.first(where: { asset in
            asset.name.lowercased().hasSuffix(".zip") && asset.name.lowercased().contains("mac")
        }), let checksumAsset = release.assets.first(where: { $0.name == "SHA256SUMS.txt" }) else {
            statusText = "This update is missing its ZIP or SHA-256 checksum file. Installation was stopped for safety."
            return
        }

        isInstalling = true
        statusText = "Downloading BluBox 360 \(latestVersion)…"

        do {
            let prepared = try await prepareUpdate(zipAsset: zipAsset, checksumAsset: checksumAsset)
            let currentApp = Bundle.main.bundleURL.standardizedFileURL
            guard currentApp.pathExtension.lowercased() == "app" else {
                throw UpdateError.message("BluBox must be opened from its .app bundle before it can update itself.")
            }

            let parent = currentApp.deletingLastPathComponent()
            if FileManager.default.isWritableFile(atPath: parent.path) {
                statusText = "Update verified. BluBox will restart to finish installing \(latestVersion)."
                try launchReplacementHelper(currentApp: currentApp, newApp: prepared.appURL, cleanupFolder: prepared.root)
                NSApp.terminate(nil)
                return
            }

            if let dmgAsset = release.assets.first(where: { $0.name.lowercased().hasSuffix(".dmg") }) {
                statusText = "This Applications folder needs macOS permission. Downloading the installer instead…"
                let dmg = try await downloadInstallerDMG(dmgAsset)
                NSWorkspace.shared.open(dmg)
                statusText = "Installer opened. Drag BluBox 360 over the existing app and choose Replace. You do not need to uninstall the old version."
            } else {
                throw UpdateError.message("The current Applications folder is not writable and this release has no DMG fallback.")
            }
        } catch {
            statusText = "Update failed: \(error.localizedDescription)"
        }

        isInstalling = false
    }

    private func prepareUpdate(zipAsset: MacGitHubRelease.Asset, checksumAsset: MacGitHubRelease.Asset) async throws -> (root: URL, appURL: URL) {
        let root = FileManager.default.temporaryDirectory
            .appendingPathComponent("BluBox360Update-\(UUID().uuidString)", isDirectory: true)
        try FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)

        let zipURL = root.appendingPathComponent(zipAsset.name)
        let sumsURL = root.appendingPathComponent("SHA256SUMS.txt")

        try await download(zipAsset.browserDownloadURL, to: zipURL)
        statusText = "Verifying update…"
        try await download(checksumAsset.browserDownloadURL, to: sumsURL)

        let sums = try String(contentsOf: sumsURL, encoding: .utf8)
        guard let expected = expectedHash(for: zipAsset.name, sums: sums) else {
            throw UpdateError.message("The checksum file does not contain the downloaded Mac ZIP.")
        }
        let actual = try sha256(of: zipURL)
        guard expected.caseInsensitiveCompare(actual) == .orderedSame else {
            throw UpdateError.message("The downloaded update failed its SHA-256 check.")
        }

        let expanded = root.appendingPathComponent("Expanded", isDirectory: true)
        try FileManager.default.createDirectory(at: expanded, withIntermediateDirectories: true)

        try await Task.detached {
            let process = Process()
            process.executableURL = URL(fileURLWithPath: "/usr/bin/ditto")
            process.arguments = ["-x", "-k", zipURL.path, expanded.path]
            try process.run()
            process.waitUntilExit()
            guard process.terminationStatus == 0 else {
                throw UpdateError.message("macOS could not unpack the update archive.")
            }
        }.value

        guard let appURL = findApp(in: expanded) else {
            throw UpdateError.message("BluBox 360.app was not found inside the downloaded update.")
        }

        let infoURL = appURL.appendingPathComponent("Contents/Info.plist")
        guard let info = NSDictionary(contentsOf: infoURL) as? [String: Any],
              info["CFBundleIdentifier"] as? String == "uk.co.blustudio.blubox360.mac",
              info["CFBundleShortVersionString"] as? String == latestVersion else {
            throw UpdateError.message("The downloaded app identity or version does not match the update feed.")
        }

        try await Task.detached {
            let process = Process()
            process.executableURL = URL(fileURLWithPath: "/usr/bin/codesign")
            process.arguments = ["--verify", "--deep", "--strict", appURL.path]
            try process.run()
            process.waitUntilExit()
            guard process.terminationStatus == 0 else {
                throw UpdateError.message("The downloaded app failed its macOS code-signature verification.")
            }
        }.value

        return (root, appURL)
    }

    private func launchReplacementHelper(currentApp: URL, newApp: URL, cleanupFolder: URL) throws {
        let helper = cleanupFolder.appendingPathComponent("install-update.zsh")
        let script = """
        #!/bin/zsh
        set -euo pipefail
        old="$1"
        new="$2"
        pid="$3"
        cleanup="$4"
        parent="${old:h}"
        backup="$parent/.BluBox360-old-$$.app"

        for _ in {1..80}; do
          if ! /bin/kill -0 "$pid" 2>/dev/null; then
            break
          fi
          /bin/sleep 0.25
        done

        restore_old() {
          if [[ ! -d "$old" && -d "$backup" ]]; then
            /bin/mv "$backup" "$old" || true
          fi
        }
        trap restore_old ERR

        /bin/rm -rf "$backup"
        /bin/mv "$old" "$backup"
        /usr/bin/ditto "$new" "$old"
        /usr/bin/codesign --verify --deep --strict "$old"
        /usr/bin/open "$old"
        /bin/sleep 2
        /bin/rm -rf "$backup"
        /bin/rm -rf "$cleanup"
        """

        try script.write(to: helper, atomically: true, encoding: .utf8)
        try FileManager.default.setAttributes([.posixPermissions: 0o755], ofItemAtPath: helper.path)

        let task = Process()
        task.executableURL = URL(fileURLWithPath: "/bin/zsh")
        task.arguments = [helper.path, currentApp.path, newApp.path, String(ProcessInfo.processInfo.processIdentifier), cleanupFolder.path]
        task.standardOutput = FileHandle.nullDevice
        task.standardError = FileHandle.nullDevice
        try task.run()
    }

    private func downloadInstallerDMG(_ asset: MacGitHubRelease.Asset) async throws -> URL {
        let downloads = FileManager.default.urls(for: .downloadsDirectory, in: .userDomainMask).first
            ?? FileManager.default.homeDirectoryForCurrentUser
        let destination = downloads.appendingPathComponent(asset.name)
        if FileManager.default.fileExists(atPath: destination.path) {
            try FileManager.default.removeItem(at: destination)
        }
        try await download(asset.browserDownloadURL, to: destination)
        return destination
    }

    private func download(_ source: URL, to destination: URL) async throws {
        var request = URLRequest(url: source)
        request.setValue("BluBox360-macOS/\(currentVersion)", forHTTPHeaderField: "User-Agent")
        request.timeoutInterval = 120
        let (temporary, response) = try await URLSession.shared.download(for: request)
        guard let http = response as? HTTPURLResponse, (200...299).contains(http.statusCode) else {
            throw UpdateError.message("The update download server returned an error.")
        }
        if FileManager.default.fileExists(atPath: destination.path) {
            try FileManager.default.removeItem(at: destination)
        }
        try FileManager.default.moveItem(at: temporary, to: destination)
    }

    private func expectedHash(for filename: String, sums: String) -> String? {
        for line in sums.split(separator: "\n") {
            let parts = line.split(whereSeparator: { $0 == " " || $0 == "\t" })
            guard parts.count >= 2 else { continue }
            let listedName = parts.dropFirst().joined(separator: " ").trimmingCharacters(in: CharacterSet(charactersIn: " *"))
            if listedName == filename {
                return String(parts[0])
            }
        }
        return nil
    }

    private func sha256(of url: URL) throws -> String {
        let handle = try FileHandle(forReadingFrom: url)
        defer { try? handle.close() }
        var hasher = SHA256()
        while true {
            let data = try handle.read(upToCount: 1024 * 1024) ?? Data()
            if data.isEmpty { break }
            hasher.update(data: data)
        }
        return hasher.finalize().map { String(format: "%02x", $0) }.joined()
    }

    private func findApp(in folder: URL) -> URL? {
        if let items = try? FileManager.default.contentsOfDirectory(at: folder, includingPropertiesForKeys: nil),
           let direct = items.first(where: { $0.lastPathComponent == "BluBox 360.app" }) {
            return direct
        }
        guard let enumerator = FileManager.default.enumerator(at: folder, includingPropertiesForKeys: nil) else { return nil }
        for case let url as URL in enumerator where url.lastPathComponent == "BluBox 360.app" {
            return url
        }
        return nil
    }

    private func version(from tag: String) -> String {
        var value = tag
        if value.lowercased().hasPrefix("mac-v") {
            value = String(value.dropFirst(5))
        }
        if let dash = value.firstIndex(of: "-") {
            value = String(value[..<dash])
        }
        return value
    }

    private func compareVersions(_ lhs: String, _ rhs: String) -> ComparisonResult {
        lhs.compare(rhs, options: [.numeric, .caseInsensitive])
    }
}

private enum UpdateError: LocalizedError {
    case message(String)

    var errorDescription: String? {
        switch self {
        case .message(let text): return text
        }
    }
}

private struct MacUpdateView: View {
    @ObservedObject var manager: MacUpdateManager

    var body: some View {
        VStack(alignment: .leading, spacing: 18) {
            HStack(spacing: 14) {
                Image(systemName: manager.updateAvailable ? "arrow.down.circle.fill" : "checkmark.circle.fill")
                    .font(.system(size: 42))
                    .foregroundStyle(manager.updateAvailable ? .blue : .green)
                VStack(alignment: .leading, spacing: 3) {
                    Text("BluBox 360 Software Update")
                        .font(.title2.bold())
                    Text(manager.statusText)
                        .foregroundStyle(.secondary)
                }
            }

            HStack {
                updateInfo("Installed", "\(manager.currentVersion) (\(manager.currentBuild))")
                Spacer()
                updateInfo("Latest", manager.latestVersion)
            }

            Divider()

            VStack(alignment: .leading, spacing: 8) {
                Text("Latest update")
                    .font(.headline)
                ScrollView {
                    Text(manager.releaseNotes.isEmpty ? "Check for updates to see the latest release notes." : manager.releaseNotes)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .textSelection(.enabled)
                }
                .frame(minHeight: 150)
                .padding(10)
                .background(Color.secondary.opacity(0.08))
                .clipShape(RoundedRectangle(cornerRadius: 10))
            }

            Text("Updates replace the existing BluBox 360 app in place. You do not need to uninstall the older version first.")
                .font(.caption)
                .foregroundStyle(.secondary)

            HStack {
                Button("Check Again") {
                    Task { await manager.checkForUpdates(silent: false) }
                }
                .disabled(manager.isChecking || manager.isInstalling)

                Spacer()

                if manager.updateAvailable {
                    Button(manager.isInstalling ? "Updating…" : "Update Now") {
                        Task { await manager.installLatestUpdate() }
                    }
                    .buttonStyle(.borderedProminent)
                    .disabled(manager.isInstalling)
                }
            }
        }
        .padding(24)
        .frame(minWidth: 520, minHeight: 450)
    }

    @ViewBuilder
    private func updateInfo(_ title: String, _ value: String) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(title.uppercased())
                .font(.caption2.bold())
                .foregroundStyle(.secondary)
            Text(value)
                .font(.headline)
        }
    }
}
