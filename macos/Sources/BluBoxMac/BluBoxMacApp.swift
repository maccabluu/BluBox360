import SwiftUI
import AppKit
import Darwin

final class BluBoxAppDelegate: NSObject, NSApplicationDelegate {
    func applicationDidFinishLaunching(_ notification: Notification) {
        NSApp.setActivationPolicy(.regular)
        NSApp.activate(ignoringOtherApps: true)
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
                .onAppear {
                    NSApp.activate(ignoringOtherApps: true)
                }
        }
        .windowStyle(.titleBar)
        .defaultSize(width: 1240, height: 790)
    }
}
