import AppKit
import SwiftUI

struct WindowInteractionFix: NSViewRepresentable {
    func makeNSView(context: Context) -> NSView {
        let view = InteractionView()
        view.translatesAutoresizingMaskIntoConstraints = false
        return view
    }

    func updateNSView(_ nsView: NSView, context: Context) {
        (nsView as? InteractionView)?.repairWindow()
    }

    private final class InteractionView: NSView {
        override func viewDidMoveToWindow() {
            super.viewDidMoveToWindow()
            repairWindow()
        }

        func repairWindow() {
            guard let window else { return }
            window.ignoresMouseEvents = false
            window.acceptsMouseMovedEvents = true
            window.isMovableByWindowBackground = false
            DispatchQueue.main.async {
                window.ignoresMouseEvents = false
                window.makeKeyAndOrderFront(nil)
                NSApp.activate(ignoringOtherApps: true)
            }
        }

        override func hitTest(_ point: NSPoint) -> NSView? {
            nil
        }
    }
}
