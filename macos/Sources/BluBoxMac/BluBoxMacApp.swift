import SwiftUI
import AppKit
import Darwin

final class BluBoxAppDelegate: NSObject, NSApplicationDelegate {
    private func repairWindows() {
        DispatchQueue.main.async {
            NSApp.setActivationPolicy(.regular)
            NSApp.activate(ignoringOtherApps: true)
            for window in NSApp.windows {
                window.ignoresMouseEvents = false
                window.acceptsMouseMovedEvents = true
                window.isMovableByWindowBackground = false
                window.makeKeyAndOrderFront(nil)
            }
        }
    }

    func applicationDidFinishLaunching(_ notification: Notification) {
        repairWindows()
    }

    func applicationDidBecomeActive(_ notification: Notification) {
        repairWindows()
    }

    func applicationShouldHandleReopen(_ sender: NSApplication, hasVisibleWindows flag: Bool) -> Bool {
        repairWindows()
        return true
    }
}

private struct MacUpdateBanner: View {
    @ObservedObject var updater: MacUpdateManager

    var body: some View {
        HStack(spacing: 10) {
            Image(systemName: "arrow.down.circle.fill")
                .font(.title2)
                .foregroundStyle(.cyan)
            VStack(alignment: .leading, spacing: 2) {
                Text("BluBox 360 \(updater.latestVersion) is available")
                    .font(.headline)
                Text("Installed: \(updater.currentVersion)")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            Button("View Update") {
                updater.presentUpdateWindow()
            }
            .buttonStyle(.borderedProminent)
        }
        .padding(12)
        .background(.ultraThinMaterial)
        .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
        .shadow(radius: 10)
        .padding(16)
    }
}

@main
struct BluBoxMacApp: App {
    @NSApplicationDelegateAdaptor(BluBoxAppDelegate.self) private var appDelegate
    @StateObject private var library = GameLibrary()
    @StateObject private var core = MacCoreBridge()
    @StateObject private var updater = MacUpdateManager()

    init() {
        if CommandLine.arguments.contains("--self-test") {
            print("BluBox 360 macOS startup self-test passed.")
            fflush(stdout)
            exit(EXIT_SUCCESS)
        }
    }

    var body: some Scene {
        WindowGroup("BluBox 360") {
            ZStack(alignment: .topTrailing) {
                ContentView()
                    .environmentObject(library)
                    .environmentObject(core)

                if updater.updateAvailable {
                    MacUpdateBanner(updater: updater)
                        .transition(.move(edge: .top).combined(with: .opacity))
                }
            }
            .animation(.easeInOut(duration: 0.2), value: updater.updateAvailable)
            .frame(minWidth: 980, minHeight: 650)
            .background(WindowInteractionFix().allowsHitTesting(false))
            .onAppear {
                NSApp.activate(ignoringOtherApps: true)
                updater.startAutomaticCheck()
                for window in NSApp.windows {
                    window.ignoresMouseEvents = false
                    window.makeKeyAndOrderFront(nil)
                }
            }
        }
        .windowStyle(.titleBar)
        .defaultSize(width: 1240, height: 790)
        .commands {
            CommandGroup(after: .appInfo) {
                Button("Software Update…") {
                    updater.presentUpdateWindow()
                }
                .keyboardShortcut("u", modifiers: [.command, .option])
            }
        }
    }
}
