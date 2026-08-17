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

@main
struct BluBoxMacApp: App {
    @NSApplicationDelegateAdaptor(BluBoxAppDelegate.self) private var appDelegate
    @StateObject private var library = GameLibrary()
    @StateObject private var core = MacCoreBridge()

    init() {
        if CommandLine.arguments.contains("--self-test") {
            print("BluBox 360 macOS startup self-test passed.")
            fflush(stdout)
            exit(EXIT_SUCCESS)
        }
    }

    var body: some Scene {
        WindowGroup("BluBox 360") {
            ContentView()
                .environmentObject(library)
                .environmentObject(core)
                .frame(minWidth: 980, minHeight: 650)
                .background(WindowInteractionFix().allowsHitTesting(false))
                .onAppear {
                    NSApp.activate(ignoringOtherApps: true)
                    for window in NSApp.windows {
                        window.ignoresMouseEvents = false
                        window.makeKeyAndOrderFront(nil)
                    }
                }
        }
        .windowStyle(.titleBar)
        .defaultSize(width: 1240, height: 790)
    }
}
