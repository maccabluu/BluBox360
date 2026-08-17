import SwiftUI

@main
struct BluBoxMacApp: App {
    @StateObject private var library = GameLibrary()
    @StateObject private var core = MacCoreBridge()

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(library)
                .environmentObject(core)
                .frame(minWidth: 980, minHeight: 650)
        }
        .windowStyle(.titleBar)
        .defaultSize(width: 1240, height: 790)
    }
}
