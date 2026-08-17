# BluBox 360 macOS

BluBox 360 macOS is the native Mac edition of BluBox 360. It is developed on the separate `macos-preview` branch so Android releases remain independent while the Mac frontend and emulator core are ported.

## Current stage

Version: `0.2.0-preview`

Target: Apple Silicon Macs running macOS 13 or newer.

The 0.2 preview contains:

- A native SwiftUI BluBox 360 application.
- Library, Recently Played, Favorites, Settings and Diagnostics pages.
- ISO, ZAR and XEX file selection.
- Recursive game-folder scanning.
- Persistent game library with migration from the first preview.
- Custom cover artwork stored in the user's Application Support folder.
- Finder integration for imported game files.
- 30 FPS and 60 FPS launch targets plus Performance, Balanced and Quality profiles.
- Metal GPU, Apple Silicon, memory, controller, thermal-state and Low Power Mode diagnostics.
- A native core bridge that searches for a future `blubox360-core` executable and captures its output.
- Developer override support through the `BLUBOX360_MAC_CORE` environment variable.
- Apple Silicon `.app`, `.zip` and `.dmg` packaging.

Game emulation is **not enabled yet**. The Android JNI libraries cannot be used as native macOS binaries. The Xenia-derived core still needs Apple Silicon CPU/JIT validation and a macOS-compatible graphics path before Xbox 360 games can render on Mac.

The current upstream Xenia source has cross-platform build code and a Vulkan backend, but upstream Xenia still documents macOS as unsupported because the MoltenVK/Metal path needed by macOS has not been completed. BluBox's Mac work therefore keeps the frontend and core bridge ready while the native backend is developed separately.

## Core bridge

BluBox looks for a native executable named `blubox360-core` in this order:

1. Path supplied through `BLUBOX360_MAC_CORE` for development.
2. Inside the app's `Contents/Resources` directory.
3. `~/Library/Application Support/BluBox 360/Core/blubox360-core`.

When a native core is present, BluBox launches the selected game path and supplies these environment values:

- `BLUBOX360_PLATFORM=macOS`
- `BLUBOX360_TARGET_FPS`
- `BLUBOX360_GRAPHICS_PRESET`
- `BLUBOX360_SHOW_FPS`

Core stdout and stderr are displayed under Diagnostics > Core Log.

## Port milestones

1. Native Mac application and package build. **Done**
2. macOS-native core bridge instead of Android JNI. **Frontend bridge done**
3. PowerPC JIT validation on Apple Silicon. **Next**
4. Vulkan/MoltenVK or native Metal-compatible graphics path.
5. Audio, controller and save/profile integration with the native core.
6. First real game boot tests.
7. Developer ID signing and notarized public preview beside the Android APK.

## Build the native preview

From the repository root on a Mac with Xcode command-line tools installed:

```bash
chmod +x macos/build-macos.sh
./macos/build-macos.sh
```

Outputs are written to `macos/dist/`:

- `BluBox 360.app`
- `BluBox-360-Mac-0.2.0-preview-Apple-Silicon.zip`
- `BluBox-360-Mac-0.2.0-preview-Apple-Silicon.dmg`
- `SHA256SUMS.txt`

The development build uses ad-hoc signing. A public downloadable release should use Apple Developer ID signing and notarization.
